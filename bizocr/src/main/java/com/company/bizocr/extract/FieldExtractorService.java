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
import java.text.Normalizer;

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
            //
            // FIX: TemplateMatcher luôn chuẩn hóa confidenceScore về khoảng 0.85-0.98 khi
            // khớp thành công (xem TemplateMatcher.match()), nên điều kiện confidenceScore < 0.75
            // KHÔNG BAO GIỜ đúng với document đã khớp Template Matching - kể cả khi regexFields
            // hoàn toàn rỗng (vd: NAME_CARD chưa có hàm extract*Fields hardcode nào).
            // Hậu quả cũ: các loại chứng từ như NAME_CARD luôn rơi vào nhánh "!isOutlier" bên dưới,
            // trả về JSON rỗng "{}" mà KHÔNG BAO GIỜ gọi Ollama fallback, dù prompt Ollama đã
            // chuẩn bị sẵn để trích contact_name/contact_title/company_name/phone/email cho NAMECARD.
            // Thêm điều kiện: nếu không trích được field nào bằng regex/DB config thì luôn coi là
            // outlier, bất kể confidence bao nhiêu, vì trả "{}" cho reviewer không giúp được gì.
            boolean noFieldsExtracted = regexFields.isEmpty();

            boolean isOutlier = classifyResult.isOutlier()
                    || classifyResult.isUseAiFallback()
                    || "AI_FALLBACK".equalsIgnoreCase(classifyResult.getMatchedBy())
                    || "OLLAMA_LOCAL".equalsIgnoreCase(classifyResult.getMatchedBy())
                    || classifyResult.getConfidenceScore() == null
                    || classifyResult.getConfidenceScore() < 0.75
                    || noFieldsExtracted;

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
            4. Với Hợp đồng, trích xuất các key: party_a, party_b, contract_value, effective_date, summary.
            5. Với NAMECARD, trích xuất các key: contact_name, contact_title, company_name, phone, email.
            6. Với Chứng từ khác, linh hoạt trích xuất các thông tin quan trọng nhất.
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

        // FIX: regex chỉ liệt kê nhãn CÓ DẤU trước đây sẽ không khớp được với OCR text không dấu
        // (rất phổ biến, xem test "...thực tế từ OCR scan" trong TemplateMatcherTest.java).
        // Giờ so khớp trên bản đã bỏ dấu (stripAccents), pattern chỉ cần viết 1 dạng không dấu.
        Pattern mstPattern = Pattern.compile("(?:Ma so thue|MST|Tax code)[:\\s]*([0-9]{10}(?:-[0-9]{3})?)", Pattern.CASE_INSENSITIVE);
        String mst = captureGroupFromOriginal(mstPattern, text);
        if (mst != null) {
            fields.put("tax_code", mst);
        }

        Pattern amountPattern = Pattern.compile("(?:Tong cong tien thanh toan|Cong tien hang|Tong tien|Thanh tien)[:\\s]*([0-9\\.,]+)", Pattern.CASE_INSENSITIVE);
        String amountRaw = captureGroupFromOriginal(amountPattern, text);
        if (amountRaw != null) {
            fields.put("total_amount", parseAmount(amountRaw));
        }

        return fields;
    }

    // 2. Regex bóc tách Hóa đơn Điện
    private Map<String, Object> extractElectricityFields(String text) {
        Map<String, Object> fields = new HashMap<>();
        Pattern custPattern = Pattern.compile("(?:The khach hang|Ma KH|Ma khach hang)[:\\s]*([A-Z0-9]+)", Pattern.CASE_INSENSITIVE);
        String cust = captureGroupFromOriginal(custPattern, text);
        if (cust != null) {
            fields.put("customer_id", cust);
        }
        Pattern amountPattern = Pattern.compile("(?:Tong cong tien thanh toan|Tong tien dien thanh toan|Tong tien thanh toan|Tien dien|Tong tien|Thanh tien)[:\\s]*([0-9\\.,]+)", Pattern.CASE_INSENSITIVE);
        String amountRaw = captureGroupFromOriginal(amountPattern, text);
        if (amountRaw != null) {
            fields.put("total_amount", parseAmount(amountRaw));
        }
        return fields;
    }

    // 3. Regex bóc tách Hóa đơn Nước
    private Map<String, Object> extractWaterFields(String text) {
        Map<String, Object> fields = new HashMap<>();
        Pattern dbPattern = Pattern.compile("(?:Ma danh bo|So danh bo|Ma KH|Ma khach hang)[:\\s]*([A-Z0-9]+)", Pattern.CASE_INSENSITIVE);
        String db = captureGroupFromOriginal(dbPattern, text);
        if (db != null) {
            fields.put("customer_id", db);
        }
        Pattern amountPattern = Pattern.compile("(?:Tong tien thanh toan|Cong tien hang|Tien nuoc|Tong tien|Thanh tien)[:\\s]*([0-9\\.,]+)", Pattern.CASE_INSENSITIVE);
        String amountRaw = captureGroupFromOriginal(amountPattern, text);
        if (amountRaw != null) {
            fields.put("total_amount", parseAmount(amountRaw));
        }
        return fields;
    }

    // 4. Regex bóc tách Phiếu thanh toán dịch vụ / Hóa đơn bán lẻ
    private Map<String, Object> extractServicePaymentFields(String text) {
        Map<String, Object> fields = new HashMap<>();

        // Tìm tên dịch vụ / đơn vị từ dòng đầu tiên hoặc từ khóa
        // FIX: kiểm tra loại trừ cũng chạy trên bản đã bỏ dấu, tránh nhầm dòng "Phieu thanh toan"
        // (không dấu) thành tên dịch vụ vì không khớp được với "phiếu thanh toán" (có dấu).
        String[] lines = text.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            String trimmedUnaccented = stripAccents(trimmed).toLowerCase();
            if (!trimmed.isEmpty()
                    && !trimmedUnaccented.contains("phieu thanh toan")
                    && !trimmedUnaccented.contains("tong chi phi")
                    && !trimmedUnaccented.contains("thanh tien")) {
                fields.put("service_name", trimmed);
                break;
            }
        }

        Pattern amountPattern = Pattern.compile("(?:Tong chi phi|Tong cong tien thanh toan|Tong tien thanh toan|Tong tien|Thanh tien|Tong cong)[:\\s]*([0-9\\.,]+)", Pattern.CASE_INSENSITIVE);
        String amountRaw = captureGroupFromOriginal(amountPattern, text);
        if (amountRaw != null) {
            fields.put("total_amount", parseAmount(amountRaw));
        }

        return fields;
    }

    // 5. Regex bóc tách Hợp đồng / Biên bản quyết toán
    private Map<String, Object> extractContractFields(String text) {
        Map<String, Object> fields = new HashMap<>();

        Pattern partyAPattern = Pattern.compile("(?:Ben A)[:\\s]*([^\\n]+)", Pattern.CASE_INSENSITIVE);
        String partyA = captureGroupFromOriginal(partyAPattern, text);
        if (partyA != null) {
            fields.put("party_a", partyA.trim());
        }

        Pattern partyBPattern = Pattern.compile("(?:Ben B)[:\\s]*([^\\n]+)", Pattern.CASE_INSENSITIVE);
        String partyB = captureGroupFromOriginal(partyBPattern, text);
        if (partyB != null) {
            fields.put("party_b", partyB.trim());
        }

        Pattern amountPattern = Pattern.compile("(?:Gia tri quyet toan|Gia tri hop dong|Tong cong|Tong tien)[:\\s]*([0-9\\.,]+)", Pattern.CASE_INSENSITIVE);
        String amountRaw = captureGroupFromOriginal(amountPattern, text);
        if (amountRaw != null) {
            fields.put("total_amount", parseAmount(amountRaw));
        }

        return fields;
    }

    // ==========================================================================
    // Tiện ích bỏ dấu tiếng Việt cho việc so khớp regex (không ảnh hưởng dữ liệu lưu DB)
    // ==========================================================================

    /**
     * Bỏ dấu tiếng Việt (NFD + xóa combining marks + xử lý riêng đ/Đ vì Unicode NFD
     * không phân rã 2 ký tự này thành base + dấu như các nguyên âm có dấu khác).
     */
    private static String stripAccents(String input) {
        if (input == null) {
            return "";
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        String noMarks = normalized.replaceAll("\\p{Mn}+", "");
        return noMarks.replace('đ', 'd').replace('Đ', 'D');
    }

    /**
     * So khớp pattern (viết KHÔNG DẤU) trên bản text đã bỏ dấu, sau đó lấy lại đúng đoạn
     * text GỐC (còn dấu) theo vị trí match — để không làm mất dấu tên công ty/nội dung khi lưu DB.
     * Độ dài chuỗi trước/sau khi bỏ dấu luôn bằng nhau với text tiếng Việt (mỗi ký tự có dấu
     * phân rã NFD thành đúng 1 base char + 1 combining mark bị xóa), nên vị trí match luôn khớp
     * 1-1 giữa 2 bản. Có fallback an toàn nếu lệch vị trí (hiếm khi xảy ra).
     */
    private static String captureGroupFromOriginal(Pattern unaccentedPattern, String originalText) {
        if (originalText == null) {
            return null;
        }
        String stripped = stripAccents(originalText);
        Matcher m = unaccentedPattern.matcher(stripped);
        if (!m.find()) {
            return null;
        }
        if (m.groupCount() < 1) {
            return m.group();
        }
        int start = m.start(1);
        int end = m.end(1);
        if (start < 0 || end > originalText.length() || start > end) {
            return m.group(1); // fallback: lấy từ bản đã bỏ dấu nếu vị trí lệch
        }
        return originalText.substring(start, end);
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