package com.company.bizocr.classify;

import com.company.bizocr.client.PythonMlClient;
import com.company.bizocr.model.dto.ClassifyResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.text.Normalizer;
import java.util.Optional;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClassifierService {

    private final TemplateMatcher templateMatcher;
    private final PythonMlClient pythonMlClient;
    private final AiClassifierFallback aiClassifierFallback;

    /** Ngưỡng chấp nhận thẳng kết quả Python ML (không cần luật, không cần Ollama). */
    @Value("${bizocr.classification.ai-confidence-threshold:0.85}")
    private double aiConfidenceThreshold;

    /** Ngưỡng "gợi ý": Python đoán được nhưng chưa chắc -> cần luật xác nhận mới dùng. */
    @Value("${bizocr.classification.hint-threshold:0.5}")
    private double hintThreshold;

    public Mono<ClassifyResult> classifyDocument(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return Mono.just(unknownResult("OCR_EMPTY"));
        }

        // Bước 1: Template Matcher nội bộ
        Optional<ClassifyResult> templateOpt = templateMatcher.match(rawText);
        if (templateOpt.isPresent() && isReliable(templateOpt.get())) {
            ClassifyResult templateResult = templateOpt.get();
            if (templateResult.getMatchedBy() == null || templateResult.getMatchedBy().isBlank()) {
                templateResult.setMatchedBy("TEMPLATE_MATCHING");
            }
            templateResult.setSuccess(true);
            log.info("[Classifier] Dùng kết quả Template: type={}, confidence={}",
                    templateResult.getDocumentType(), templateResult.getConfidenceScore());
            return Mono.just(templateResult);
        }

        // Bước 2: Python ML -> luật xác nhận -> (nếu cần) Ollama rồi đối chiếu
        log.info("[Classifier] Không khớp Template tin cậy. Gửi request sang Python System ML...");
        return pythonMlClient.predictDocumentType(rawText)
                .onErrorResume(e -> {
                    log.warn("[Classifier] Python ML lỗi ({}). Coi như không có kết quả ML.", e.toString());
                    return Mono.empty();
                })
                .map(node -> {
                    ClassifyResult ml = new ClassifyResult();
                    if (node != null && node.has("predictedType")) {
                        ml.setDocumentType(AiClassifierFallback.normalizeType(node.get("predictedType").asText()));
                        ml.setConfidenceScore(node.has("confidence") ? node.get("confidence").asDouble() : 0.0);
                    } else {
                        ml.setDocumentType("UNKNOWN");
                        ml.setConfidenceScore(0.0);
                    }
                    return ml;
                })
                .flatMap(ml -> decide(ml, rawText))
                .switchIfEmpty(Mono.defer(() -> {
                    log.info("[Classifier] Python ML không khả dụng hoặc trả về rỗng.");
                    return decide(null, rawText);
                }));
    }

    // ------------------------------------------------------------------
    // Quyết định cuối cùng
    // ------------------------------------------------------------------

    private Mono<ClassifyResult> decide(ClassifyResult ml, String rawText) {
        String mlType = ml == null ? "UNKNOWN" : ml.getDocumentType();
        double mlConf = ml == null ? 0.0 : getConfidence(ml);
        boolean mlKnown = !isNoOpinion(mlType);

        // Log số thật của Python để biết nên chỉnh ngưỡng bao nhiêu
        log.info("[Classifier] Python ML: type={}, confidence={}", mlType, mlConf);

        // 2a. Python đủ tin cậy -> dùng luôn
        if (mlKnown && mlConf >= aiConfidenceThreshold) {
            return Mono.just(accept(ml, "ML_SYSTEM"));
        }

        // 2b. Python chưa chắc nhưng luật XÁC NHẬN -> dùng luôn, khỏi gọi Ollama
        if (mlKnown && mlConf >= hintThreshold && TypeRules.verdict(mlType, rawText) == Verdict.CONFIRMED) {
            log.info("[Classifier] Python ML {} (conf={}) được luật xác nhận -> bỏ qua Ollama.", mlType, mlConf);
            return Mono.just(accept(ml, "ML_SYSTEM"));
        }

        // 2c. Còn lại: hỏi Ollama rồi ĐỐI CHIẾU (không để Ollama tự động đè Python)
        log.info("[Classifier] Chưa đủ chắc chắn ({} / {}). Hỏi Ollama local để đối chiếu...", mlType, mlConf);
        return callOllamaFallback(rawText)
                .map(ai -> reconcile(ml, ai, rawText));
    }

    private ClassifyResult reconcile(ClassifyResult ml, ClassifyResult ai, String rawText) {
        String mlType = ml == null ? "UNKNOWN" : ml.getDocumentType();
        double mlConf = ml == null ? 0.0 : getConfidence(ml);
        String aiType = AiClassifierFallback.normalizeType(ai.getDocumentType());

        boolean mlUseful = !isNoOpinion(mlType) && mlConf >= hintThreshold;
        boolean aiUseful = !isNoOpinion(aiType);

        // Cả hai đều không có ý kiến
        if (!mlUseful && !aiUseful) {
            return ruleGuessOr(ai, rawText);
        }

        // Chỉ Python có ý kiến
        if (!aiUseful) {
            log.info("[Classifier] Ollama không có ý kiến, dùng gợi ý của Python: {}", mlType);
            return accept(ml, "ML_SYSTEM");
        }

        Verdict aiV = TypeRules.verdict(aiType, rawText);

        // Chỉ Ollama có ý kiến
        if (!mlUseful) {
            if (aiV == Verdict.REJECTED) {
                log.warn("[Classifier] Luật bác bỏ nhãn {} của Ollama -> thử đoán bằng luật.", aiType);
                return ruleGuessOr(unknownResult("CLASSIFY_CONFLICT"), rawText);
            }
            return ai;
        }

        // Cả hai đều có ý kiến
        if (aiType.equals(mlType)) {
            log.info("[Classifier] Python và Ollama đồng ý: {}", aiType);
            return ai;
        }

        Verdict mlV = TypeRules.verdict(mlType, rawText);
        log.warn("[Classifier] BẤT ĐỒNG: Python={}({}, {}) vs Ollama={}({})", mlType, mlConf, mlV, aiType, aiV);

        if (aiV == Verdict.REJECTED && mlV != Verdict.REJECTED) return accept(ml, "ML_SYSTEM");
        if (mlV == Verdict.REJECTED && aiV != Verdict.REJECTED) return ai;
        if (aiV == Verdict.CONFIRMED && mlV != Verdict.CONFIRMED) return ai;
        if (mlV == Verdict.CONFIRMED && aiV != Verdict.CONFIRMED) return accept(ml, "ML_SYSTEM");

        // Không phân xử được -> để người dùng chọn ở Bước 4, tránh gán bừa
        return unknownResult("CLASSIFY_CONFLICT");
    }

    /** Phương án cuối: nếu luật nhận ra được danh thiếp thì dùng, không thì trả fallback. */
    private ClassifyResult ruleGuessOr(ClassifyResult fallback, String rawText) {
        if (TypeRules.verdict("NAMECARD", rawText) == Verdict.CONFIRMED) {
            log.info("[Classifier] Luật nhận diện NAMECARD (ngắn + có email + có số điện thoại).");
            return ClassifyResult.builder()
                    .documentType("NAMECARD")
                    .confidenceScore(Math.max(hintThreshold, 0.6))
                    .matchedBy("RULE_ENGINE")
                    .success(true)
                    .build();
        }
        return fallback;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private ClassifyResult accept(ClassifyResult r, String matchedBy) {
        r.setMatchedBy(matchedBy);
        r.setSuccess(true);
        return r;
    }

    private boolean isNoOpinion(String type) {
        return type == null || type.isBlank()
                || "UNKNOWN".equalsIgnoreCase(type) || "OTHER".equalsIgnoreCase(type);
    }

    private boolean isReliable(ClassifyResult result) {
        return result != null && getConfidence(result) >= aiConfidenceThreshold;
    }

    private double getConfidence(ClassifyResult result) {
        return result.getConfidenceScore() == null ? 0.0 : result.getConfidenceScore();
    }

    private Mono<ClassifyResult> callOllamaFallback(String rawText) {
        return aiClassifierFallback
                .classify(rawText)
                .map(aiResult -> {
                    aiResult.setMatchedBy("OLLAMA_LOCAL");
                    aiResult.setSuccess(true);
                    return aiResult;
                })
                .onErrorResume(error -> {
                    log.error("[Classifier] Ollama không khả dụng, chuyển sang phê duyệt thủ công", error);
                    return Mono.just(unknownResult("AI_UNAVAILABLE"));
                });
    }

    private ClassifyResult unknownResult(String matchedBy) {
        return ClassifyResult.builder()
                .documentType("UNKNOWN")
                .confidenceScore(0.0)
                .matchedBy(matchedBy)
                .success(false)
                .build();
    }

    // ------------------------------------------------------------------
    // Luật xác nhận / bác bỏ nhãn theo nội dung OCR
    // ------------------------------------------------------------------

    private enum Verdict {CONFIRMED, REJECTED, UNSURE}

    private static final class TypeRules {

        private static final Pattern EMAIL =
                Pattern.compile("[A-Z0-9._%+\\-]+@[A-Z0-9.\\-]+\\.[A-Z]{2,}");
        private static final Pattern PHONE =
                Pattern.compile("(?<!\\d)(?:\\+?84|0)[\\d .\\-]{8,14}\\d");
        private static final Pattern PHONE_LABEL =
                Pattern.compile("\\b(TEL|MOBILE|HOTLINE|SDT|DT)\\b");
        private static final Pattern PARTY =
                Pattern.compile("\\bBEN [AB]\\b");

        static Verdict verdict(String type, String rawText) {
            if (type == null || rawText == null) return Verdict.UNSURE;
            String t = fold(rawText);
            int len = t.length();

            switch (type) {
                case "NAMECARD": {
                    boolean email = EMAIL.matcher(t).find();
                    boolean phone = PHONE.matcher(t).find() || PHONE_LABEL.matcher(t).find();
                    if (len <= 600 && email && phone) return Verdict.CONFIRMED;
                    if (len > 1500) return Verdict.REJECTED;
                    return Verdict.UNSURE;
                }
                case "ID_CARD": {
                    if (t.contains("CAN CUOC") || t.contains("CHUNG MINH NHAN DAN")
                            || t.contains("CCCD") || t.contains("CMND")
                            || t.contains("CONG HOA XA HOI CHU NGHIA")) {
                        return Verdict.CONFIRMED;
                    }
                    // Giấy tờ tùy thân không có email / tên công ty / mã số thuế
                    if (EMAIL.matcher(t).find() || t.contains("CONG TY") || t.contains("TNHH")
                            || t.contains("MA SO THUE") || t.contains("MST")) {
                        return Verdict.REJECTED;
                    }
                    return Verdict.UNSURE;
                }
                case "CONTRACT": {
                    if (t.contains("HOP DONG") || PARTY.matcher(t).find() || t.contains("DIEU 1")
                            || t.contains("NGHIEM THU") || t.contains("THANH LY")) {
                        return Verdict.CONFIRMED;
                    }
                    if (len < 500) return Verdict.REJECTED;
                    return Verdict.UNSURE;
                }
                case "VAT_INVOICE": {
                    if (t.contains("HOA DON GIA TRI GIA TANG") || t.contains("VAT INVOICE")) {
                        return Verdict.CONFIRMED;
                    }
                    return Verdict.UNSURE;
                }
                default:
                    return Verdict.UNSURE;
            }
        }

        /** Bỏ dấu tiếng Việt + viết hoa + gom khoảng trắng, để so khớp không phụ thuộc dấu OCR. */
        private static String fold(String s) {
            String n = Normalizer.normalize(s, Normalizer.Form.NFD)
                    .replaceAll("\\p{M}+", "")
                    .replace('đ', 'd').replace('Đ', 'D')
                    .toUpperCase();
            return n.replaceAll("\\s+", " ").trim();
        }
    }
}