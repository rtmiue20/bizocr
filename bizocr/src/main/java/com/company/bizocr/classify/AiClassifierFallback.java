package com.company.bizocr.classify;

import com.company.bizocr.model.dto.ClassifyResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class AiClassifierFallback {

    private final ChatClient chatClient;

    // Spring AI tự động inject ChatClient.Builder
    public AiClassifierFallback(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * Nhận Text thô và nhờ OpenAI phân loại (Chạy bất đồng bộ - Non-blocking)
     */
    public Mono<ClassifyResult> classify(String rawText) {
        // Nếu văn bản quá ngắn hoặc rỗng, trả về luôn là KHÔNG_XAC_DINH
        if (rawText == null || rawText.trim().length() < 10) {
            return Mono.just(ClassifyResult.builder()
                    .documentType("UNKNOWN")
                    .confidenceScore(0.0)
                    .matchedBy("AI_FALLBACK")
                    .success(false)
                    .build());
        }

        // Đóng gói tác vụ gọi API (vốn là blocking) vào một luồng riêng bằng Mono.fromCallable
        return Mono.fromCallable(() -> {

            // 1. Viết Prompt kỹ thuật (Prompt Engineering) ép AI trả về đúng định dạng
            String prompt = """
                    Bạn là một chuyên gia phân loại tài liệu. Dựa vào nội dung văn bản OCR sau, 
                    hãy phân loại tài liệu vào MỘT trong các nhóm: 
                    VAT_INVOICE, CONTRACT, NAME_CARD, ID_CARD, UTILITY_BILL, RECEIPT, OTHER.
                    
                    Quy tắc nghiêm ngặt: CHỈ trả về đúng 1 TỪ KHÓA (ID) của nhóm đó, KHÔNG giải thích, KHÔNG thêm dấu câu.
                    
                    Văn bản cần phân loại:
                    "%s"
                    """.formatted(rawText);

            // 2. Gọi API OpenAI thông qua Spring AI
            String aiResponse = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            // 3. Xử lý chuỗi trả về cho sạch sẽ
            String documentType = (aiResponse != null && !aiResponse.isBlank())
                    ? aiResponse.trim().toUpperCase()
                    : "OTHER";

            // 4. Trả về kết quả DTO
            return ClassifyResult.builder()
                    .documentType(documentType)
                    .confidenceScore(0.85) // Giả định AI tự tin ở mức 85%
                    .matchedBy("AI_FALLBACK")
                    .success(true)
                    .build();

        }).subscribeOn(Schedulers.boundedElastic());
        // Lệnh subscribeOn cực kỳ quan trọng: Ép Spring Boot đẩy tác vụ chờ API mạng này
        // sang pool luồng riêng, giúp hệ thống không bị treo khi có hàng ngàn ảnh upload cùng lúc.
    }
}