package com.company.bizocr.classify;

import com.company.bizocr.model.dto.ClassifyResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
public class AiClassifierFallback {

    // Danh sách loại tài liệu hợp lệ hệ thống hỗ trợ
    private static final Set<String> VALID_TYPES = Set.of(
            "VAT_INVOICE",
            "SERVICE_PAYMENT_VOUCHER",
            "ELECTRICITY_BILL",
            "WATER_BILL",
            "CONTRACT",
            "BANK_STATEMENT",
            "ID_CARD",
            "NAMECARD",
            "OTHER"
    );

    // token có thể xuất hiện trong câu trả lời của model -> nhãn chuẩn
    private static final Map<String, String> TOKENS = new LinkedHashMap<>();

    static {
        for (String t : VALID_TYPES) {
            TOKENS.put(t, t);
        }
        TOKENS.put("NAME_CARD", "NAMECARD");
        TOKENS.put("BUSINESS_CARD", "NAMECARD");
        TOKENS.put("DANH_THIEP", "NAMECARD");
        TOKENS.put("IDCARD", "ID_CARD");
    }

    private final ChatClient chatClient;

    /**
     * Ollama chỉ là GỢI Ý (chưa được luật xác nhận) nên không được có confidence cao.
     * Giá trị này thay cho 0.65 hardcode cũ; ClassifierService sẽ đối chiếu với Python ML + luật.
     */
    @Value("${bizocr.classification.ollama-hint-confidence:0.4}")
    private double hintConfidence;

    public AiClassifierFallback(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * Chuẩn hóa mã loại về dạng chuẩn (NAME_CARD/name_card/DANH_THIEP -> NAMECARD).
     * Dùng chung cho kết quả từ Python ML và từ Ollama.
     */
    public static String normalizeType(String raw) {
        if (raw == null || raw.isBlank()) {
            return "UNKNOWN";
        }
        String up = raw.trim().toUpperCase().replace(' ', '_').replace('-', '_');
        String mapped = TOKENS.get(up);
        return mapped != null ? mapped : up;
    }

    /**
     * Tìm nhãn hợp lệ xuất hiện SỚM NHẤT trong câu trả lời của model.
     * Chịu được trường hợp model trả cả câu ("Đây là NAMECARD") thay vì đúng 1 từ.
     * Trả về null nếu không tìm thấy nhãn nào.
     */
    static String extractType(String response) {
        if (response == null || response.isBlank()) {
            return null;
        }
        String up = response.toUpperCase().replace(' ', '_').replace('-', '_');
        String best = null;
        int bestIdx = Integer.MAX_VALUE;
        int bestLen = 0;
        for (Map.Entry<String, String> e : TOKENS.entrySet()) {
            int idx = up.indexOf(e.getKey());
            if (idx < 0) continue;
            if (idx < bestIdx || (idx == bestIdx && e.getKey().length() > bestLen)) {
                best = e.getValue();
                bestIdx = idx;
                bestLen = e.getKey().length();
            }
        }
        return best;
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
                    NAMECARD, OTHER.

                    HƯỚNG DẪN ĐẶC BIỆT ĐỂ TRÁNH PHÂN LOẠI NHẦM LẪN:
                    - NAMECARD (Danh thiếp/Card Visit): Văn bản ngắn gọn, chứa thông tin cá nhân và liên hệ của một người (Họ tên, Chức vụ/Chức danh như Giám Đốc/CFO/CEO/Manager/Trưởng phòng, Mobile/Tel/Fax, E-mail, Website, Địa chỉ văn phòng, có thể có Logo công ty hoặc Tax code/MST). Dù có chứa từ 'Công ty', 'Tax code', 'Giám Đốc' nhưng là danh thiếp cá nhân thì BẮT BUỘC phân loại là NAMECARD, TUYỆT ĐỐI KHÔNG phân loại là CONTRACT hay ID_CARD.
                    - ID_CARD (Căn cước công dân/CMND): CHỈ chọn khi văn bản có các cụm như 'Căn cước công dân', 'Chứng minh nhân dân', 'Cộng hòa xã hội chủ nghĩa Việt Nam' hoặc dãy số định danh 9/12 chữ số. Văn bản có E-mail, Website, tên công ty, mã số thuế thì KHÔNG phải ID_CARD.
                    - CONTRACT (Hợp đồng): Văn bản dài hoặc có tính pháp lý hợp đồng, thường có các bên tham gia (Bên A, Bên B), căn cứ pháp lý, điều khoản hợp đồng (Điều 1, Điều 2...), biên bản nghiệm thu/thanh lý/quyết toán hợp đồng, quyền và nghĩa vụ các bên. Không nhầm danh thiếp thành CONTRACT.
                    - VAT_INVOICE: Hóa đơn giá trị gia tăng, hóa đơn điện tử, tiền thuế GTGT, thuế suất.
                    - Nếu không chắc chắn thì trả về OTHER.

                    CHỈ trả về đúng một từ khóa nhóm (ví dụ: NAMECARD hoặc CONTRACT).
                    Không giải thích, không thêm dấu câu.

                    Nội dung OCR:
                    %s
                    """.formatted(rawText);

            log.info("Đang gọi Ollama để phân loại tài liệu...");

            String aiResponse = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            String documentType;
            double confidenceScore;

            String parsedType = extractType(aiResponse);

            if (parsedType != null) {
                documentType = parsedType;
                // Ollama chỉ là gợi ý: confidence thấp, chờ luật/Python ML xác nhận ở ClassifierService
                confidenceScore = "OTHER".equals(documentType) ? Math.min(0.30, hintConfidence) : hintConfidence;
            } else {
                log.warn("Ollama trả về không chứa loại hợp lệ nào: '{}', fallback về OTHER", aiResponse);
                documentType = "OTHER";
                confidenceScore = Math.min(0.30, hintConfidence);
            }

            log.info("Ollama phân loại kết quả: {} (hint confidence: {}, raw='{}')",
                    documentType, confidenceScore, aiResponse == null ? null : aiResponse.trim());

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