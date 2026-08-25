package com.company.bizocr.classify;

import com.company.bizocr.model.dto.ClassifyResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClassifierService {

    private final AiClassifierFallback aiClassifierFallback;
    private final TemplateMatcher templateMatcher;

    public Mono<ClassifyResult> classifyDocument(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return Mono.just(ClassifyResult.builder()
                    .documentType("UNKNOWN")
                    .confidenceScore(0.0)
                    .build());
        }

        // 1. Thử phân loại bằng Template Matcher (Rule-based mạnh mẽ hơn)
        return Mono.justOrEmpty(templateMatcher.match(rawText))
                .switchIfEmpty(Mono.defer(() -> {
                    // 2. AI Fallback nếu Template Matcher không khớp rõ ràng
                    log.info("[Classifier] Không khớp mẫu cứng (Template), chuyển sang AI phân loại...");
                    return aiClassifierFallback.classify(rawText);
                }));
    }
}