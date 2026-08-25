package com.company.bizocr.extract;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class FieldExtractorService {

    private final ObjectMapper objectMapper;
    private final ChatModel chatModel; // Spring AI Auto-configured

    public Mono<String> extractFields(String documentType, String rawText) {
        return Mono.fromCallable(() -> {
            if (rawText == null || rawText.isBlank()) {
                return "{}";
            }

            Map<String, Object> regexFields = new HashMap<>();

            // Step 1: Thử trích xuất bằng Regex trước
            if ("HOA_DON_VAT".equalsIgnoreCase(documentType)) {
                regexFields = extractInvoiceVatFields(rawText);
            }

            // Step 2: Đánh giá xem Regex có bóc tách đủ thông tin quan trọng không
            boolean isRegexSuccess = regexFields.containsKey("seller_tax_code") && regexFields.containsKey("total_amount");

            if (isRegexSuccess) {
                log.info(">> [Regex Engine] Bóc tách dữ liệu thành công 100%");
                return objectMapper.writeValueAsString(regexFields);
            }

            // Step 3: LLM Fallback - Nếu Regex thiếu thông tin hoặc là văn bản tự do, gọi OpenAI GPT-4o-mini
            log.warn(">> [Regex Engine] Không đủ thông tin cơ bản. Đang chuyển sang [LLM Fallback Engine]...");
            return callLlmFallback(documentType, rawText);
        });
    }

    private String callLlmFallback(String documentType, String rawText) {
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

            OpenAiChatOptions options = OpenAiChatOptions.builder()
                    .model("gpt-4o-mini")
                    .temperature(0.1)
                    .build();
            String response = chatModel.call(new Prompt(promptText)).getResult().getOutput().getText();

            // Làm sạch chuỗi JSON phản hồi từ LLM
            String cleanJson = response.replaceAll("```json", "").replaceAll("```", "").trim();
            log.info(">> [LLM Fallback Engine] Bóc tách JSON thành công!");
            return cleanJson;

        } catch (Exception e) {
            log.error(">> [LLM Fallback Engine] Lỗi khi gọi OpenAI API: {}", e.getMessage());
            Map<String, String> fallbackError = new HashMap<>();
            fallbackError.put("error", "LLM Processing failed: " + e.getMessage());
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