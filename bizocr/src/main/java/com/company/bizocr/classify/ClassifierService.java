package com.company.bizocr.classify;

import com.company.bizocr.client.PythonMlClient;
import com.company.bizocr.model.dto.ClassifyResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClassifierService {

    private final TemplateMatcher templateMatcher;
    private final PythonMlClient pythonMlClient;
    private final AiClassifierFallback aiClassifierFallback;

    @Value("${bizocr.classification.ai-confidence-threshold:0.85}")
    private double aiConfidenceThreshold;

    public Mono<ClassifyResult> classifyDocument(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return Mono.just(unknownResult("OCR_EMPTY"));
        }

        // Bước 1: Thử phân loại bằng Template Matcher nội bộ (Xử lý Optional)
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

        // Bước 2: Không khớp Template -> Gọi mô hình Python ML nội bộ
        log.info("[Classifier] Không khớp Template tin cậy. Gửi request sang Python System ML...");
        return pythonMlClient.predictDocumentType(rawText)
                .flatMap(jsonNode -> {
                    ClassifyResult mlResult = new ClassifyResult();

                    // Lấy dữ liệu từ JSON trả về của Python (predictedType, confidence)
                    if (jsonNode != null && jsonNode.has("predictedType")) {
                        mlResult.setDocumentType(jsonNode.get("predictedType").asText());
                        mlResult.setConfidenceScore(jsonNode.get("confidence").asDouble());
                    } else {
                        mlResult.setDocumentType("UNKNOWN");
                        mlResult.setConfidenceScore(0.0);
                    }

                    // Kiểm tra độ tin cậy
                    if (isReliable(mlResult) && !"UNKNOWN".equalsIgnoreCase(mlResult.getDocumentType())) {
                        mlResult.setMatchedBy("ML_SYSTEM");
                        mlResult.setSuccess(true);
                        return Mono.just(mlResult);
                    } else {
                        log.info("[Classifier] Python ML độ tin cậy thấp hoặc chưa xác định ({}). Fallback sang Ollama local...", mlResult.getDocumentType());
                        return callOllamaFallback(rawText);
                    }
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.info("[Classifier] Python ML không khả dụng hoặc trả về rỗng. Fallback sang Ollama local...");
                    return callOllamaFallback(rawText);
                }));
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
}