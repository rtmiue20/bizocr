package com.company.bizocr.extract;

import com.company.bizocr.model.dto.ClassifyResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class FieldExtractorService {

    private final ObjectMapper objectMapper;
    private final ChatClient chatClient;

    public FieldExtractorService(ObjectMapper objectMapper, ChatClient.Builder chatClientBuilder) {
        this.objectMapper = objectMapper;
        this.chatClient = chatClientBuilder.build();
    }

    public Mono<String> extractFields(
            ClassifyResult classifyResult,
            String rawText
    ) {
        return Mono.fromCallable(() -> {
            if (rawText == null || rawText.isBlank()) {
                return "{}";
            }
            String documentType = classifyResult.getDocumentType();
            Map<String, Object> regexFields = new HashMap<>();

            // Bước 1: Thử trích xuất bằng Regex theo từng loại chứng từ
            if ("VAT_INVOICE".equalsIgnoreCase(documentType) || "HOA_DON_VAT".equalsIgnoreCase(documentType)) {
                regexFields = extractInvoiceVatFields(rawText);
            } else if ("ELECTRICITY_BILL".equalsIgnoreCase(documentType) || "HOA_DON_DIEN".equalsIgnoreCase(documentType)) {
                regexFields = extractElectricityFields(rawText);
            } else if ("WATER_BILL".equalsIgnoreCase(documentType) || "HOA_DON_NUOC".equalsIgnoreCase(documentType)) {
                regexFields = extractWaterFields(rawText);
            }

            // Đã SỬA: Dùng tax_code thay vì seller_tax_code
            boolean regexSuccess = (regexFields.containsKey("tax_code") || regexFields.containsKey("customer_id"))
                    && regexFields.containsKey("total_amount");

            if (regexSuccess) {
                log.info("Bóc tách bằng Regex thành công cho loại chứng từ: {}", documentType);
                return objectMapper.writeValueAsString(regexFields);
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

    // 2. Regex bóc tách Hóa đơn Điện (giữ nguyên)
    private Map<String, Object> extractElectricityFields(String text) {
        Map<String, Object> fields = new HashMap<>();
        Pattern custPattern = Pattern.compile("(?:Thẻ khách hàng|Mã KH|Mã khách hàng)[:\\s]*([A-Z0-9]+)", Pattern.CASE_INSENSITIVE);
        Matcher custMatcher = custPattern.matcher(text);
        if (custMatcher.find()) {
            fields.put("customer_id", custMatcher.group(1));
        }
        Pattern amountPattern = Pattern.compile("(?:Tổng cộng tiền thanh toán|Tổng tiền thanh toán|Tổng tiền|Thành tiền)[:\\s]*([0-9\\.,]+)", Pattern.CASE_INSENSITIVE);
        Matcher amountMatcher = amountPattern.matcher(text);
        if (amountMatcher.find()) {
            fields.put("total_amount", parseAmount(amountMatcher.group(1)));
        }
        return fields;
    }

    // 3. Regex bóc tách Hóa đơn Nước (giữ nguyên)
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