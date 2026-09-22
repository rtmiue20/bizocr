package com.company.bizocr.classify;

import com.company.bizocr.client.PythonMlClient;
import com.company.bizocr.model.dto.ClassifyResult;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClassifierServiceTest {

    @Mock
    private TemplateMatcher templateMatcher;

    @Mock
    private PythonMlClient pythonMlClient;

    @Mock
    private AiClassifierFallback aiClassifierFallback;

    private ClassifierService classifierService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        classifierService = new ClassifierService(templateMatcher, pythonMlClient, aiClassifierFallback);
        ReflectionTestUtils.setField(classifierService, "aiConfidenceThreshold", 0.85);
    }

    @Test
    @DisplayName("1. Ưu tiên khớp bằng TemplateMatcher khi độ tin cậy >= ngưỡng")
    void testClassifyWithTemplateMatchingSuccess() {
        ClassifyResult templateResult = ClassifyResult.builder()
                .documentType("VAT_INVOICE")
                .confidenceScore(0.95)
                .matchedBy("TEMPLATE_MATCHING")
                .success(true)
                .build();

        when(templateMatcher.match(anyString())).thenReturn(Optional.of(templateResult));

        StepVerifier.create(classifierService.classifyDocument("Hóa đơn giá trị gia tăng số 123 MST 0101234567"))
                .assertNext(result -> {
                    assertEquals("VAT_INVOICE", result.getDocumentType());
                    assertEquals(0.95, result.getConfidenceScore());
                    assertEquals("TEMPLATE_MATCHING", result.getMatchedBy());
                    assertTrue(result.isSuccess());
                })
                .verifyComplete();

        verifyNoInteractions(pythonMlClient);
        verifyNoInteractions(aiClassifierFallback);
    }

    @Test
    @DisplayName("2. Khi Template không khớp, dùng Python ML nếu độ tin cậy >= 0.85")
    void testClassifyWithPythonMlSuccess() throws Exception {
        when(templateMatcher.match(anyString())).thenReturn(Optional.empty());

        String json = "{\"predictedType\": \"ELECTRICITY_BILL\", \"confidence\": 0.92}";
        when(pythonMlClient.predictDocumentType(anyString()))
                .thenReturn(Mono.just(objectMapper.readTree(json)));

        StepVerifier.create(classifierService.classifyDocument("EVN Hà Nội thông báo tiền điện tháng 8"))
                .assertNext(result -> {
                    assertEquals("ELECTRICITY_BILL", result.getDocumentType());
                    assertEquals(0.92, result.getConfidenceScore());
                    assertEquals("ML_SYSTEM", result.getMatchedBy());
                    assertTrue(result.isSuccess());
                })
                .verifyComplete();

        verifyNoInteractions(aiClassifierFallback);
    }

    @Test
    @DisplayName("3. Khi Python ML không tin cậy hoặc lỗi, tự động Fallback sang Ollama Local (Qwen/LLM)")
    void testClassifyWithOllamaFallback() throws Exception {
        when(templateMatcher.match(anyString())).thenReturn(Optional.empty());

        // ML trả về confidence thấp hoặc UNKNOWN
        String json = "{\"predictedType\": \"UNKNOWN\", \"confidence\": 0.40}";
        when(pythonMlClient.predictDocumentType(anyString()))
                .thenReturn(Mono.just(objectMapper.readTree(json)));

        ClassifyResult aiResult = ClassifyResult.builder()
                .documentType("CONTRACT")
                .confidenceScore(0.85)
                .matchedBy("AI_FALLBACK")
                .success(true)
                .build();
        when(aiClassifierFallback.classify(anyString())).thenReturn(Mono.just(aiResult));

        StepVerifier.create(classifierService.classifyDocument("Biên bản nghiệm thu bàn giao gói thầu CNTT"))
                .assertNext(result -> {
                    assertEquals("CONTRACT", result.getDocumentType());
                    assertEquals("OLLAMA_LOCAL", result.getMatchedBy());
                    assertTrue(result.isSuccess());
                })
                .verifyComplete();

        verify(aiClassifierFallback, times(1)).classify(anyString());
    }
}
