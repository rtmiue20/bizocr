package com.company.bizocr.classify;

import com.company.bizocr.model.dto.ClassifyResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Component
public class AiClassifierFallback {

    private final ChatClient chatClient;

    public AiClassifierFallback(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * Nhận text OCR và nhờ Ollama local phân loại.
     */
    public Mono<ClassifyResult> classify(String rawText) {
        if (rawText == null || rawText.trim().length() < 10) {
            return Mono.just(ClassifyResult.builder()
                    .documentType("UNKNOWN")
                    .confidenceScore(0.0)
                    .matchedBy("AI_FALLBACK")
                    .success(false)
                    .build());
        }

        return Mono.fromCallable(() -> {
            String prompt = """
                    Bạn là một chuyên gia phân loại tài liệu.
                    Dựa vào nội dung OCR sau, hãy phân loại tài liệu vào
                    MỘT trong các nhóm:

                    VAT_INVOICE, SERVICE_PAYMENT_VOUCHER, ELECTRICITY_BILL,
                    WATER_BILL, CONTRACT, BANK_STATEMENT, ID_CARD,
                    NAMECARD, OTHER.

                    CHỈ trả về đúng một từ khóa nhóm.
                    Không giải thích, không thêm dấu câu.

                    Nội dung OCR:
                    %s
                    """.formatted(rawText);

            log.info("Đang gọi Ollama để phân loại tài liệu");

            String aiResponse = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            String documentType = aiResponse == null || aiResponse.isBlank()
                    ? "OTHER"
                    : aiResponse.trim()
                    .toUpperCase()
                    .replaceAll("[^A-Z_]", "");

            log.info("Ollama phân loại kết quả: {}", documentType);
            
            boolean isOutlier = !"UNKNOWN".equalsIgnoreCase(documentType) && !"OTHER".equalsIgnoreCase(documentType);

            return ClassifyResult.builder()
                    .documentType(documentType)
                    .confidenceScore(0.85)
                    .matchedBy("OLLAMA_LOCAL")
                    .success(true)
                    .outlier(isOutlier)
                    .build();

        }).subscribeOn(Schedulers.boundedElastic());
    }
}