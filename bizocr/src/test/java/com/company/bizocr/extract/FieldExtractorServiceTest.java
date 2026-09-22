package com.company.bizocr.extract;

import com.company.bizocr.model.dto.ClassifyResult;
import com.company.bizocr.service.OllamaQueueService;
import com.company.bizocr.service.TemplateConfigLoaderService;
import com.company.bizocr.validator.ExtractionValidatorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FieldExtractorServiceTest {

    @Mock
    private TemplateConfigLoaderService templateConfigLoaderService;

    @Mock
    private OllamaQueueService ollamaQueueService;

    private FieldExtractorService fieldExtractorService;
    private RegexExtractorService regexExtractorService;
    private ExtractionValidatorService extractionValidatorService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        extractionValidatorService = new ExtractionValidatorService(validator, objectMapper);
        CandidateFilterService candidateFilterService = new CandidateFilterService();
        regexExtractorService = new RegexExtractorService(candidateFilterService);

        fieldExtractorService = new FieldExtractorService(
                objectMapper,
                templateConfigLoaderService,
                regexExtractorService,
                extractionValidatorService,
                ollamaQueueService
        );

        when(templateConfigLoaderService.getFieldConfigs(any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("Gating PASS khi trích xuất đủ trường bắt buộc và Extraction Confidence >= 80%")
    void testGatingPassWhenFieldsValid() {
        ClassifyResult classifyResult = ClassifyResult.builder()
                .documentType("VAT_INVOICE")
                .confidenceScore(0.95)
                .matchedBy("TEMPLATE_MATCHING")
                .success(true)
                .build();

        String rawText = """
                HÓA ĐƠN GIÁ TRỊ GIA TĂNG
                Mã số thuế: 0100109106
                Tổng tiền thanh toán: 1200000
                """;

        StepVerifier.create(fieldExtractorService.extractFields("doc-123", classifyResult, rawText))
                .expectNextMatches(json -> {
                    return json.contains("0100109106")
                            && json.contains("AUTO_APPROVED");
                })
                .verifyComplete();

        // Không enqueue vào Ollama vì đã gating pass
        verify(ollamaQueueService, never()).enqueueTask(any(), any(), any());
    }

    @Test
    @DisplayName("Gating REJECT và fallback sang Ollama Queue khi MST fail checksum (thiếu identifier hợp lệ)")
    void testGatingRejectWhenTaxCodeChecksumFails() {
        ClassifyResult classifyResult = ClassifyResult.builder()
                .documentType("VAT_INVOICE")
                .confidenceScore(0.95)
                .matchedBy("TEMPLATE_MATCHING")
                .success(true)
                .build();

        // MST sai checksum: 0100109107
        String rawText = """
                HÓA ĐƠN GIÁ TRỊ GIA TĂNG
                Mã số thuế: 0100109107
                Tổng tiền thanh toán: 1200000
                """;

        StepVerifier.create(fieldExtractorService.extractFields("doc-456", classifyResult, rawText))
                .expectNextMatches(json -> json.contains("PENDING_AI_WORKER"))
                .verifyComplete();

        // Enqueue vào Ollama Queue
        verify(ollamaQueueService, times(1)).enqueueTask(eq("doc-456"), eq("VAT_INVOICE"), eq(rawText));
    }
}
