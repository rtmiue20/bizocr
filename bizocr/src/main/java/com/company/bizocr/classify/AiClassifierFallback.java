package com.company.bizocr.classify;

import com.company.bizocr.model.dto.ClassifyResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Set;

@Slf4j
@Component
public class AiClassifierFallback {

    // Danh sách 9 loại tài liệu hợp lệ hệ thống hỗ trợ
    private static final Set<String> VALID_TYPES = Set.of(
            "VAT_INVOICE",
            "SERVICE_PAYMENT_VOUCHER",
            "ELECTRICITY_BILL",
            "WATER_BILL",
            "CONTRACT",
            "BANK_STATEMENT",
            "ID_CARD",
            "NAME_CARD",
            "OTHER"
    );

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
                    Bạn là một chuyên gia phân loại tài liệu doanh nghiệp hàng đầu.
                    Dựa vào nội dung OCR sau, hãy phân loại tài liệu vào MỘT trong các nhóm:

                    VAT_INVOICE, SERVICE_PAYMENT_VOUCHER, ELECTRICITY_BILL,
                    WATER_BILL, CONTRACT, BANK_STATEMENT, ID_CARD,
                    NAME_CARD, OTHER.

                    HƯỚNG DẪN ĐẶC BIỆT ĐỂ TRÁNH PHÂN LOẠI NHẦM LẪN:
                    - NAME_CARD (Danh thiếp/Card Visit): Văn bản ngắn gọn, chứa thông tin cá nhân và liên hệ của một người (Họ tên, Chức vụ/Chức danh như Giám Đốc/CFO/CEO/Manager/Trưởng phòng, Mobile/Tel/Fax, E-mail, Website, Địa chỉ văn phòng, có thể có Logo công ty hoặc Tax code). Dù có chứa từ 'Công ty', 'Tax code', 'Giám Đốc' nhưng là danh thiếp cá nhân thì BẮT BUỘC phân loại là NAME_CARD, TUYỆT ĐỐI KHÔNG phân loại là CONTRACT.
                    - CONTRACT (Hợp đồng): Văn bản dài hoặc có tính pháp lý hợp đồng, thường có các bên tham gia (Bên A, Bên B), căn cứ pháp lý, điều khoản hợp đồng (Điều 1, Điều 2...), biên bản nghiệm thu/thanh lý/quyết toán hợp đồng, quyền và nghĩa vụ các bên. Không nhầm danh thiếp thành CONTRACT.
                    - VAT_INVOICE: Hóa đơn giá trị gia tăng, hóa đơn điện tử, tiền thuế GTGT, thuế suất.

                    CHỈ trả về đúng một từ khóa nhóm (ví dụ: NAME_CARD hoặc CONTRACT).
                    Không giải thích, không thêm dấu câu.

                    Nội dung OCR:
                    %s
                    """.formatted(rawText);

            log.info("Đang gọi Ollama để phân loại tài liệu...");

            String aiResponse = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            String parsedType = aiResponse == null || aiResponse.isBlank()
                    ? "OTHER"
                    : aiResponse.trim()
                    .toUpperCase()
                    .replaceAll("[^A-Z_]", "");

            // Chuẩn hóa alias nếu AI trả về NAMECARD thành NAME_CARD
            if ("NAMECARD".equalsIgnoreCase(parsedType)) {
                parsedType = "NAME_CARD";
            }

            String documentType;
            double confidenceScore;

            // Step 1: Validate loại tài liệu trả về từ Ollama
            if (VALID_TYPES.contains(parsedType)) {
                documentType = parsedType;
                // Step 2: Gán confidence score thực tế
                // Nếu phân loại ra loại cụ thể -> 0.65 (Gợi ý AI, yêu cầu NEED_REVIEW)
                // Nếu ra OTHER -> 0.30
                confidenceScore = "OTHER".equalsIgnoreCase(documentType) ? 0.30 : 0.65;
            } else {
                log.warn("Ollama trả về documentType không hợp lệ: '{}' (gốc: '{}'), fallback về OTHER", parsedType, aiResponse);
                documentType = "OTHER";
                confidenceScore = 0.30;
            }

            log.info("Ollama phân loại kết quả: {} (Confidence: {})", documentType, confidenceScore);

            boolean isOutlier = !"UNKNOWN".equalsIgnoreCase(documentType) && !"OTHER".equalsIgnoreCase(documentType);

            return ClassifyResult.builder()
                    .documentType(documentType)
                    .confidenceScore(confidenceScore)
                    .matchedBy("OLLAMA_LOCAL")
                    .success(true)
                    .outlier(isOutlier)
                    .build();

        }).subscribeOn(Schedulers.boundedElastic());
    }
}