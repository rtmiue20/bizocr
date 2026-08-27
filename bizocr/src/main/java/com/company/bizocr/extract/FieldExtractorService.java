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
            
            // Bước 1: Thử trích xuất bằng Regex
            if ("VAT_INVOICE".equalsIgnoreCase(documentType) || "HOA_DON_VAT".equalsIgnoreCase(documentType)) {
                regexFields = extractInvoiceVatFields(rawText);
            }
            
            boolean regexSuccess =
                    regexFields.containsKey("seller_tax_code")
                            && regexFields.containsKey("total_amount");
            if (regexSuccess) {
                log.info("Bóc tách bằng Regex thành công");
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
                return objectMapper.writeValueAsString(regexFields); // Trả về kết quả regex hiện có thay vì {}
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
                2. Với Hóa đơn VAT, trích xuất các key: seller_tax_code, seller_name, invoice_date, total_amount, vat_amount.
                3. Với Hợp đồng/Chứng từ khác, trích xuất các key: party_a, party_b, contract_value, effective_date, summary.
                """.formatted(documentType, rawText);


            String response = chatClient.prompt()
                    .user(promptText)
                    .call()
                    .content();

            // Làm sạch chuỗi JSON phản hồi từ LLM
            String cleanJson = response.replaceAll("```json", "").replaceAll("```", "").trim();
            log.info(">> [Ollama Engine] Bóc tách JSON thành công!");
            return cleanJson;

        } catch (Exception e) {
            log.error(">> [Ollama Engine] Lỗi khi gọi Ollama API: {}", e.getMessage());
            Map<String, String> fallbackError = new HashMap<>();
            fallbackError.put("error", "Ollama Processing failed: " + e.getMessage());
            try {
                return objectMapper.writeValueAsString(fallbackError);
            } catch (Exception ex) {
                return "{}";
            }
        }
    }

    private Map<String, Object> extractInvoiceVatFields(String text) {
        Map<String, Object> fields = new HashMap<>();

        Pattern mstPattern = Pattern.compile("(?:Mã số thuế|MST|Tax code)[:\\s]*([0-9]{10}(?:-[0-9]{3})?)", Pattern.CASE_INSENSITIVE);
        Matcher mstMatcher = mstPattern.matcher(text);
        if (mstMatcher.find()) {
            fields.put("seller_tax_code", mstMatcher.group(1));
        }

        Pattern amountPattern = Pattern.compile("(?:Tổng cộng tiền thanh toán|Cộng tiền hàng|Tổng tiền|Thành tiền)[:\\s]*([0-9\\.,]+)", Pattern.CASE_INSENSITIVE);
        Matcher amountMatcher = amountPattern.matcher(text);
        if (amountMatcher.find()) {
            fields.put("total_amount", amountMatcher.group(1));
        }

        return fields;
    }
}