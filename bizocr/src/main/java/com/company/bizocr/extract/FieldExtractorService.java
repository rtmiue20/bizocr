package com.company.bizocr.extract;

import com.company.bizocr.service.TemplateConfigLoaderService;
import com.company.bizocr.model.dto.ClassifyResult;
import com.company.bizocr.model.entity.TemplateFieldConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class FieldExtractorService {

    private final ObjectMapper objectMapper;
    private final ChatClient chatClient;
    private final TemplateConfigLoaderService templateConfigLoaderService;

    public FieldExtractorService(
            ObjectMapper objectMapper,
            ChatClient.Builder chatClientBuilder,
            TemplateConfigLoaderService templateConfigLoaderService
    ) {
        this.objectMapper = objectMapper;
        this.chatClient = chatClientBuilder.build();
        this.templateConfigLoaderService = templateConfigLoaderService;
    }

    public Mono<String> extractFields(ClassifyResult classifyResult, String rawText) {
        return Mono.fromCallable(() -> {
            if (rawText == null || rawText.isBlank()) {
                return "{}";
            }
            String documentType = classifyResult.getDocumentType();
            Map<String, Object> regexFields = new HashMap<>();
            boolean regexSuccess;

            // Bước 1a: Ưu tiên cấu hình động từ DB (template_field_config, nạp từ Excel qua
            // TemplateConfigLoaderService) nếu loại chứng từ này đã được đăng ký — cho phép
            // mở rộng danh mục chứng từ mới mà KHÔNG cần sửa code Java.
            List<TemplateFieldConfig> dynamicFieldConfigs = templateConfigLoaderService.getFieldConfigs(documentType);

            if (!dynamicFieldConfigs.isEmpty()) {
                regexFields = extractFieldsFromDynamicConfig(dynamicFieldConfigs, rawText);

                // Khắc phục lỗi Stream rỗng luôn trả về true khi không có field bắt buộc nào
                boolean hasRequiredFieldsMatch = dynamicFieldConfigs.stream()
                        .filter(cfg -> Boolean.TRUE.equals(cfg.getRequired()))
                        .map(TemplateFieldConfig::getFieldKey)
                        .allMatch(regexFields::containsKey);

                regexSuccess = !regexFields.isEmpty() && hasRequiredFieldsMatch;

                if (regexSuccess) {
                    log.info("Bóc tách bằng cấu hình DB thành công cho loại chứng từ: {}", documentType);
                    return objectMapper.writeValueAsString(regexFields);
                }
            } else {
                // Bước 1b: Không có cấu hình DB cho loại chứng từ này -> dùng logic Regex
                // hardcode theo nhóm như cũ (giữ nguyên hoàn toàn, không đổi hành vi).
                if ("VAT_INVOICE".equalsIgnoreCase(documentType) || "HOA_DON_VAT".equalsIgnoreCase(documentType)) {
                    regexFields = extractInvoiceVatFields(rawText);
                } else if ("ELECTRICITY_BILL".equalsIgnoreCase(documentType) || "HOA_DON_DIEN".equalsIgnoreCase(documentType)) {
                    regexFields = extractElectricityFields(rawText);
                } else if ("WATER_BILL".equalsIgnoreCase(documentType) || "HOA_DON_NUOC".equalsIgnoreCase(documentType)) {
                    regexFields = extractWaterFields(rawText);
                } else if ("SERVICE_PAYMENT_VOUCHER".equalsIgnoreCase(documentType)
                        || "RECEIPT".equalsIgnoreCase(documentType)
                        || "SERVICE_BILL".equalsIgnoreCase(documentType)) {
                    regexFields = extractServicePaymentFields(rawText);
                } else if ("CONTRACT".equalsIgnoreCase(documentType) || "HOP_DONG".equalsIgnoreCase(documentType)) {
                    regexFields = extractContractFields(rawText);
                }

                // Kiểm tra độ hoàn thiện của trường trích xuất bằng Regex
                boolean hasRequiredIdentifier = regexFields.containsKey("tax_code")
                        || regexFields.containsKey("customer_id")
                        || regexFields.containsKey("service_name")
                        || regexFields.containsKey("party_a")
                        || "SERVICE_PAYMENT_VOUCHER".equalsIgnoreCase(documentType)
                        || "RECEIPT".equalsIgnoreCase(documentType)
                        || "SERVICE_BILL".equalsIgnoreCase(documentType);

                regexSuccess = hasRequiredIdentifier && regexFields.containsKey("total_amount");

                if (regexSuccess) {
                    log.info("Bóc tách bằng Regex thành công cho loại chứng từ: {}", documentType);
                    return objectMapper.writeValueAsString(regexFields);
                }
            }

            // Bước 2: Kiểm tra outlier hoặc yêu cầu AI Fallback
            boolean isOutlier = classifyResult.isOutlier()
                    || classifyResult.isUseAiFallback()
                    || "AI_FALLBACK".equalsIgnoreCase(classifyResult.getMatchedBy())
                    || "OLLAMA_LOCAL".equalsIgnoreCase(classifyResult.getMatchedBy())
                    || classifyResult.getConfidenceScore() == null
                    || classifyResult.getConfidenceScore() < 0.75;

            if (!isOutlier) {
                log.warn(
                        "Mẫu không đủ field nhưng không phải outlier. "
                                + "Chuyển sang kiểm duyệt thủ công"
                );
                return objectMapper.writeValueAsString(regexFields);
            }

            log.warn("Chứng từ ngoại lệ. Gọi Ollama local để bóc tách");
            return callOllamaExtraction(
                    documentType,
                    rawText
            );
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private String callOllamaExtraction(String documentType, String rawText) {
        try {
            String promptText = """
            Bạn là chuyên gia trích xuất dữ liệu chứng từ kế toán Việt Nam.
            Hãy đọc đoạn văn bản OCR dưới đây và trích xuất thông tin thành cấu trúc JSON phẳng (Flat JSON).
            
            Loại chứng từ dự kiến: %s
            Văn bản OCR RawText:
            \"\"\"
            %s
            \"\"\"
            
            Yêu cầu:
            1. Trả về DUY NHẤT một chuỗi JSON hợp lệ, không kèm Markdown codeblock, không kèm giải thích.
            2. Với Hóa đơn VAT, trích xuất các key: tax_code, seller_name, invoice_date, total_amount, vat_amount.
            3. Với Hóa đơn Điện/Nước, trích xuất các key: customer_id, customer_name, total_amount, bill_date.
            4. Với Hợp đồng/Chứng từ khác, trích xuất các key: party_a, party_b, contract_value, effective_date, summary.
            """.formatted(documentType, rawText);

            String response = chatClient.prompt()
                    .user(promptText)
                    .call()
                    .content();

            // 1. Loại bỏ markdown codeblock
            String cleanJson = response.replaceAll("```json", "").replaceAll("```", "").trim();

            // 2. Trích xuất chính xác khối JSON nằm giữa '{' và '}' (loại bỏ văn bản giải thích thừa từ LLM)
            int firstBrace = cleanJson.indexOf("{");
            int lastBrace = cleanJson.lastIndexOf("}");
            if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
                cleanJson = cleanJson.substring(firstBrace, lastBrace + 1);
            }

            // 3. Parse thử JSON - Nếu không phải JSON hợp lệ, dòng này sẽ throw Jackson Exception
            objectMapper.readTree(cleanJson);

            log.info(">> [Ollama Engine] Bóc tách và Validate JSON thành công!");
            return cleanJson;

        } catch (Exception e) {
            log.error(">> [Ollama Engine] Phản hồi từ Ollama không phải JSON hợp lệ: {}", e.getMessage());

            // Trả về JSON lỗi chuẩn hóa để không làm hỏng cột DB và đánh dấu chuyển sang NEEDS_REVIEW
            Map<String, String> fallbackError = new HashMap<>();
            fallbackError.put("error", "invalid_json_output");
            try {
                return objectMapper.writeValueAsString(fallbackError);
            } catch (Exception ex) {
                return "{\"error\":\"invalid_json_output\"}";
            }
        }
    }

    // 1. Regex bóc tách Hóa đơn VAT
    private Map<String, Object> extractInvoiceVatFields(String text) {
        Map<String, Object> fields = new HashMap<>();

        Pattern mstPattern = Pattern.compile("(?:Mã số thuế|MST|Tax code)[:\\s]*([0-9]{10}(?:-[0-9]{3})?)", Pattern.CASE_INSENSITIVE);
        Matcher mstMatcher = mstPattern.matcher(text);
        if (mstMatcher.find()) {
            // Đã SỬA: Ghi vào Map bằng key tax_code
            fields.put("tax_code", mstMatcher.group(1));
        }

        Pattern amountPattern = Pattern.compile("(?:Tổng cộng tiền thanh toán|Cộng tiền hàng|Tổng tiền|Thành tiền)[:\\s]*([0-9\\.,]+)", Pattern.CASE_INSENSITIVE);
        Matcher amountMatcher = amountPattern.matcher(text);
        if (amountMatcher.find()) {
            fields.put("total_amount", parseAmount(amountMatcher.group(1)));
        }

        return fields;
    }

    // 2. Regex bóc tách Hóa đơn Điện
    private Map<String, Object> extractElectricityFields(String text) {
        Map<String, Object> fields = new HashMap<>();
        Pattern custPattern = Pattern.compile("(?:Thẻ khách hàng|Mã KH|Mã khách hàng)[:\\s]*([A-Z0-9]+)", Pattern.CASE_INSENSITIVE);
        Matcher custMatcher = custPattern.matcher(text);
        if (custMatcher.find()) {
            fields.put("customer_id", custMatcher.group(1));
        }
        Pattern amountPattern = Pattern.compile("(?:Tổng cộng tiền thanh toán|Tổng tiền điện thanh toán|Tổng tiền thanh toán|Tiền điện|Tổng tiền|Thành tiền)[:\\s]*([0-9\\.,]+)", Pattern.CASE_INSENSITIVE);
        Matcher amountMatcher = amountPattern.matcher(text);
        if (amountMatcher.find()) {
            fields.put("total_amount", parseAmount(amountMatcher.group(1)));
        }
        return fields;
    }

    // 3. Regex bóc tách Hóa đơn Nước
    private Map<String, Object> extractWaterFields(String text) {
        Map<String, Object> fields = new HashMap<>();
        Pattern dbPattern = Pattern.compile("(?:Mã danh bộ|Số danh bộ|Mã KH|Mã khách hàng)[:\\s]*([A-Z0-9]+)", Pattern.CASE_INSENSITIVE);
        Matcher dbMatcher = dbPattern.matcher(text);
        if (dbMatcher.find()) {
            fields.put("customer_id", dbMatcher.group(1));
        }
        Pattern amountPattern = Pattern.compile("(?:Tổng tiền thanh toán|Cộng tiền hàng|Tiền nước|Tổng tiền|Thành tiền)[:\\s]*([0-9\\.,]+)", Pattern.CASE_INSENSITIVE);
        Matcher amountMatcher = amountPattern.matcher(text);
        if (amountMatcher.find()) {
            fields.put("total_amount", parseAmount(amountMatcher.group(1)));
        }
        return fields;
    }

    // 4. Regex bóc tách Phiếu thanh toán dịch vụ / Hóa đơn bán lẻ
    private Map<String, Object> extractServicePaymentFields(String text) {
        Map<String, Object> fields = new HashMap<>();

        // Tìm tên dịch vụ / đơn vị từ dòng đầu tiên hoặc từ khóa
        String[] lines = text.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.toLowerCase().contains("phiếu thanh toán")
                    && !trimmed.toLowerCase().contains("tổng chi phí")
                    && !trimmed.toLowerCase().contains("thành tiền")) {
                fields.put("service_name", trimmed);
                break;
            }
        }

        Pattern amountPattern = Pattern.compile("(?:Tổng chi phí|Tổng cộng tiền thanh toán|Tổng tiền thanh toán|Tổng tiền|Thành tiền|Tổng cộng)[:\\s]*([0-9\\.,]+)", Pattern.CASE_INSENSITIVE);
        Matcher amountMatcher = amountPattern.matcher(text);
        if (amountMatcher.find()) {
            fields.put("total_amount", parseAmount(amountMatcher.group(1)));
        }

        return fields;
    }

    // 5. Regex bóc tách Hợp đồng / Biên bản quyết toán
    private Map<String, Object> extractContractFields(String text) {
        Map<String, Object> fields = new HashMap<>();

        Pattern partyAPattern = Pattern.compile("(?:Bên A|Ben A)[:\\s]*([^\\n]+)", Pattern.CASE_INSENSITIVE);
        Matcher partyAMatcher = partyAPattern.matcher(text);
        if (partyAMatcher.find()) {
            fields.put("party_a", partyAMatcher.group(1).trim());
        }

        Pattern partyBPattern = Pattern.compile("(?:Bên B|Ben B)[:\\s]*([^\\n]+)", Pattern.CASE_INSENSITIVE);
        Matcher partyBMatcher = partyBPattern.matcher(text);
        if (partyBMatcher.find()) {
            fields.put("party_b", partyBMatcher.group(1).trim());
        }

        Pattern amountPattern = Pattern.compile("(?:Giá trị quyết toán|Giá trị hợp đồng|Tổng cộng|Tổng tiền)[:\\s]*([0-9\\.,]+)", Pattern.CASE_INSENSITIVE);
        Matcher amountMatcher = amountPattern.matcher(text);
        if (amountMatcher.find()) {
            fields.put("total_amount", parseAmount(amountMatcher.group(1)));
        }

        return fields;
    }

    // 6. Bóc tách theo cấu hình động từ DB (template_field_config, nạp từ Excel)
    private Map<String, Object> extractFieldsFromDynamicConfig(List<TemplateFieldConfig> configs, String rawText) {
        Map<String, Object> fields = new HashMap<>();
        for (TemplateFieldConfig cfg : configs) {
            try {
                Pattern pattern = Pattern.compile(cfg.getRegexPattern(), Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(rawText);
                if (matcher.find()) {
                    String rawValue = matcher.groupCount() >= 1 ? matcher.group(1) : matcher.group(0);
                    fields.put(cfg.getFieldKey(), castDynamicValue(rawValue, cfg.getDataType()));
                }
            } catch (Exception e) {
                log.warn("Regex cấu hình DB lỗi cho field '{}' (doc_type={}): {}",
                        cfg.getFieldKey(), cfg.getDocTypeCode(), e.getMessage());
            }
        }
        return fields;
    }

    private Object castDynamicValue(String rawValue, String dataType) {
        if (rawValue == null) {
            return null;
        }
        String trimmed = rawValue.trim();
        if ("NUMBER".equalsIgnoreCase(dataType)) {
            return parseAmount(trimmed);
        }
        // TEXT, DATE: giữ nguyên chuỗi thô — chuẩn hóa định dạng ngày có thể bổ sung sau nếu cần
        return trimmed;
    }

    // Utility chuẩn hóa định dạng số tiền từ chuỗi OCR
    private Double parseAmount(String rawAmount) {
        try {
            String clean = rawAmount.replaceAll("[^0-9]", "");
            return Double.parseDouble(clean);
        } catch (Exception e) {
            return null;
        }
    }
}