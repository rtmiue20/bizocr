package com.company.bizocr.service;


/*
 * GHI CHÚ TRƯỚC KHI DÙNG:
 * - Test này giả định Document có constructor không tham số + setter chuẩn
 *   (setId, setOwnerCompanyId, setCompanyId, setExtractedFields). Nếu Document
 *   dùng @Builder thay vì setter, đổi buildDoc() bên dưới cho khớp.
 * - Cần các dependency: JUnit 5, Mockito (mockito-junit-jupiter), reactor-test,
 *   AssertJ - đều đã có sẵn nếu project dùng spring-boot-starter-test.
 * - Test này target đúng bug đã fix: companyId trong audit phải lấy từ
 *   doc.getOwnerCompanyId() (tenant, luôn có giá trị) chứ không phải
 *   doc.getCompanyId() (company OCR nhận diện, có thể null).
 */

import com.company.bizocr.model.entity.Document;
import com.company.bizocr.model.entity.FieldCorrectionAudit;
import com.company.bizocr.repository.FieldCorrectionAuditRepository;
import com.company.bizocr.security.CurrentUser;
import com.company.bizocr.security.CurrentUserResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FieldAuditServiceTest {

    @Mock
    private FieldCorrectionAuditRepository auditRepository;

    @Mock
    private CurrentUserResolver currentUserResolver;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private FieldAuditService service;

    @BeforeEach
    void setUp() {
        service = new FieldAuditService(auditRepository, objectMapper, currentUserResolver);
        // lenient(): 2 test "bỏ qua hoàn toàn" (null/blank JSON) return sớm, không gọi tới
        // currentUserResolver -> nếu stub strict sẽ bị UnnecessaryStubbingException.
        lenient().when(currentUserResolver.getCurrentUser())
                .thenReturn(Mono.just(new CurrentUser("minh", List.of("REVIEWER"), 10L)));
    }

    private Document buildDoc(Long id, Long ownerCompanyId, Long companyId, String extractedFields) {
        Document doc = new Document();
        doc.setId(id);
        doc.setOwnerCompanyId(ownerCompanyId);
        doc.setCompanyId(companyId);
        doc.setExtractedFields(extractedFields);
        return doc;
    }

    @SuppressWarnings("unchecked")
    private static List<FieldCorrectionAudit> toList(Iterable<FieldCorrectionAudit> iterable) {
        return Flux.fromIterable(iterable).collectList().block();
    }

    // ---------------------------------------------------------------------
    // Trường hợp KHÔNG cần audit
    // ---------------------------------------------------------------------
    @Nested
    @DisplayName("Không cần audit")
    class NoAuditNeeded {

        @Test
        @DisplayName("extractedFields mới = null -> bỏ qua hoàn toàn, không đụng repository")
        void nullJson_shouldSkipAudit() {
            Document doc = buildDoc(1L, 10L, null, "{}");

            StepVerifier.create(service.logFieldCorrections(doc, null))
                    .verifyComplete();

            verifyNoInteractions(auditRepository);
        }

        @Test
        @DisplayName("extractedFields mới = chuỗi rỗng/blank -> bỏ qua, không đụng repository")
        void blankJson_shouldSkipAudit() {
            Document doc = buildDoc(1L, 10L, null, "{}");

            StepVerifier.create(service.logFieldCorrections(doc, "   "))
                    .verifyComplete();

            verifyNoInteractions(auditRepository);
        }

        @Test
        @DisplayName("JSON cũ và mới giống hệt nhau -> saveAll được gọi với danh sách RỖNG")
        void identicalJson_shouldProduceNoAudit() {
            String json = "{\"party_a\":\"Cong ty A\"}";
            Document doc = buildDoc(1L, 10L, null, json);
            when(auditRepository.saveAll(anyIterable())).thenReturn(Flux.empty());

            StepVerifier.create(service.logFieldCorrections(doc, json))
                    .verifyComplete();

            ArgumentCaptor<Iterable<FieldCorrectionAudit>> captor = ArgumentCaptor.forClass(Iterable.class);
            verify(auditRepository).saveAll(captor.capture());
            assertThat(toList(captor.getValue())).isEmpty();
        }
    }

    // ---------------------------------------------------------------------
    // Trường hợp CÓ thay đổi field -> phải tạo audit đúng
    // ---------------------------------------------------------------------
    @Nested
    @DisplayName("Có thay đổi field -> tạo audit đúng")
    class AuditCreated {

        @Test
        @DisplayName("1 field đổi giá trị -> tạo đúng 1 audit record với đủ thông tin")
        void singleFieldChanged_createsOneAuditWithFullData() {
            String oldJson = "{\"party_a\":\"Cong ty A\"}";
            String newJson = "{\"party_a\":\"Cong ty A - Sua\"}";
            Document doc = buildDoc(1L, 10L, 99L, oldJson);
            when(auditRepository.saveAll(anyIterable()))
                    .thenAnswer(inv -> Flux.fromIterable((Iterable<FieldCorrectionAudit>) inv.getArgument(0)));

            StepVerifier.create(service.logFieldCorrections(doc, newJson))
                    .verifyComplete();

            ArgumentCaptor<Iterable<FieldCorrectionAudit>> captor = ArgumentCaptor.forClass(Iterable.class);
            verify(auditRepository).saveAll(captor.capture());
            List<FieldCorrectionAudit> audits = toList(captor.getValue());

            assertThat(audits).hasSize(1);
            FieldCorrectionAudit audit = audits.get(0);
            assertThat(audit.getDocumentId()).isEqualTo(1L);
            assertThat(audit.getFieldName()).isEqualTo("party_a");
            assertThat(audit.getOriginalValue()).isEqualTo("Cong ty A");
            assertThat(audit.getCorrectedValue()).isEqualTo("Cong ty A - Sua");
            assertThat(audit.getReviewedBy()).isEqualTo("minh");
            assertThat(audit.getReviewedAt()).isNotBlank();
        }

        @Test
        @DisplayName("REGRESSION GUARD: companyId trong audit PHẢI lấy từ ownerCompanyId, không phải companyId (OCR)")
        void auditCompanyId_mustUseOwnerCompanyId_notOcrCompanyId() {
            String oldJson = "{\"party_a\":\"A\"}";
            String newJson = "{\"party_a\":\"B\"}";
            // companyId (OCR-resolved) = null (case document NEED_REVIEW, chưa resolve được),
            // ownerCompanyId (tenant sở hữu) = 10L -> đây là bug đã fix, test để chặn regression.
            Document doc = buildDoc(1L, 10L, null, oldJson);
            when(auditRepository.saveAll(anyIterable()))
                    .thenAnswer(inv -> Flux.fromIterable((Iterable<FieldCorrectionAudit>) inv.getArgument(0)));

            StepVerifier.create(service.logFieldCorrections(doc, newJson))
                    .verifyComplete();

            ArgumentCaptor<Iterable<FieldCorrectionAudit>> captor = ArgumentCaptor.forClass(Iterable.class);
            verify(auditRepository).saveAll(captor.capture());
            List<FieldCorrectionAudit> audits = toList(captor.getValue());

            assertThat(audits).hasSize(1);
            assertThat(audits.get(0).getCompanyId())
                    .as("companyId audit phải = ownerCompanyId (10), KHÔNG được null dù companyId OCR = null")
                    .isEqualTo(10L);
        }

        @Test
        @DisplayName("Nhiều field đổi cùng lúc -> tạo đủ audit cho từng field thay đổi, bỏ qua field không đổi")
        void multipleFieldsChanged_createsAuditOnlyForChangedFields() {
            String oldJson = "{\"party_a\":\"A\",\"party_b\":\"B\",\"contract_value\":\"1000\"}";
            String newJson = "{\"party_a\":\"A2\",\"party_b\":\"B2\",\"contract_value\":\"1000\"}";
            Document doc = buildDoc(2L, 10L, null, oldJson);
            when(auditRepository.saveAll(anyIterable()))
                    .thenAnswer(inv -> Flux.fromIterable((Iterable<FieldCorrectionAudit>) inv.getArgument(0)));

            StepVerifier.create(service.logFieldCorrections(doc, newJson))
                    .verifyComplete();

            ArgumentCaptor<Iterable<FieldCorrectionAudit>> captor = ArgumentCaptor.forClass(Iterable.class);
            verify(auditRepository).saveAll(captor.capture());
            List<FieldCorrectionAudit> audits = toList(captor.getValue());

            assertThat(audits).hasSize(2);
            assertThat(audits).extracting(FieldCorrectionAudit::getFieldName)
                    .containsExactlyInAnyOrder("party_a", "party_b");
        }

        @Test
        @DisplayName("Field mới được thêm (không có trong bản cũ) -> originalValue = null")
        void newFieldAdded_originalValueIsNull() {
            String oldJson = "{\"party_a\":\"A\"}";
            String newJson = "{\"party_a\":\"A\",\"summary\":\"Tom tat moi\"}";
            Document doc = buildDoc(3L, 10L, null, oldJson);
            when(auditRepository.saveAll(anyIterable()))
                    .thenAnswer(inv -> Flux.fromIterable((Iterable<FieldCorrectionAudit>) inv.getArgument(0)));

            StepVerifier.create(service.logFieldCorrections(doc, newJson))
                    .verifyComplete();

            ArgumentCaptor<Iterable<FieldCorrectionAudit>> captor = ArgumentCaptor.forClass(Iterable.class);
            verify(auditRepository).saveAll(captor.capture());
            List<FieldCorrectionAudit> audits = toList(captor.getValue());

            assertThat(audits).hasSize(1);
            assertThat(audits.get(0).getFieldName()).isEqualTo("summary");
            assertThat(audits.get(0).getOriginalValue()).isNull();
            assertThat(audits.get(0).getCorrectedValue()).isEqualTo("Tom tat moi");
        }

        @Test
        @DisplayName("Field bị xoá ở bản mới -> correctedValue = null")
        void fieldRemoved_correctedValueIsNull() {
            String oldJson = "{\"party_a\":\"A\",\"party_b\":\"B\"}";
            String newJson = "{\"party_a\":\"A\"}";
            Document doc = buildDoc(4L, 10L, null, oldJson);
            when(auditRepository.saveAll(anyIterable()))
                    .thenAnswer(inv -> Flux.fromIterable((Iterable<FieldCorrectionAudit>) inv.getArgument(0)));

            StepVerifier.create(service.logFieldCorrections(doc, newJson))
                    .verifyComplete();

            ArgumentCaptor<Iterable<FieldCorrectionAudit>> captor = ArgumentCaptor.forClass(Iterable.class);
            verify(auditRepository).saveAll(captor.capture());
            List<FieldCorrectionAudit> audits = toList(captor.getValue());

            assertThat(audits).hasSize(1);
            assertThat(audits.get(0).getFieldName()).isEqualTo("party_b");
            assertThat(audits.get(0).getCorrectedValue()).isNull();
        }

        @Test
        @DisplayName("Document chưa có extractedFields cũ (null) -> coi như '{}', mọi field mới đều được audit")
        void oldExtractedFieldsNull_treatsAsEmptyObject() {
            String newJson = "{\"party_a\":\"A\"}";
            Document doc = buildDoc(5L, 10L, null, null);
            when(auditRepository.saveAll(anyIterable()))
                    .thenAnswer(inv -> Flux.fromIterable((Iterable<FieldCorrectionAudit>) inv.getArgument(0)));

            StepVerifier.create(service.logFieldCorrections(doc, newJson))
                    .verifyComplete();

            ArgumentCaptor<Iterable<FieldCorrectionAudit>> captor = ArgumentCaptor.forClass(Iterable.class);
            verify(auditRepository).saveAll(captor.capture());
            List<FieldCorrectionAudit> audits = toList(captor.getValue());

            assertThat(audits).hasSize(1);
            assertThat(audits.get(0).getOriginalValue()).isNull();
        }
    }

    // ---------------------------------------------------------------------
    // Metadata keys (field_confidences, anomaly_analysis) không được audit
    // ---------------------------------------------------------------------
    @Nested
    @DisplayName("Metadata keys phải bị loại khỏi audit")
    class MetadataExclusion {

        @Test
        @DisplayName("field_confidences đổi giá trị -> KHÔNG tạo audit")
        void fieldConfidencesChanged_shouldBeIgnored() {
            String oldJson = "{\"party_a\":\"A\",\"field_confidences\":\"0.8\"}";
            String newJson = "{\"party_a\":\"A\",\"field_confidences\":\"0.99\"}";
            Document doc = buildDoc(6L, 10L, null, oldJson);
            when(auditRepository.saveAll(anyIterable())).thenReturn(Flux.empty());

            StepVerifier.create(service.logFieldCorrections(doc, newJson))
                    .verifyComplete();

            ArgumentCaptor<Iterable<FieldCorrectionAudit>> captor = ArgumentCaptor.forClass(Iterable.class);
            verify(auditRepository).saveAll(captor.capture());
            assertThat(toList(captor.getValue())).isEmpty();
        }

        @Test
        @DisplayName("anomaly_analysis đổi giá trị -> KHÔNG tạo audit")
        void anomalyAnalysisChanged_shouldBeIgnored() {
            String oldJson = "{\"anomaly_analysis\":\"none\"}";
            String newJson = "{\"anomaly_analysis\":\"suspicious\"}";
            Document doc = buildDoc(7L, 10L, null, oldJson);
            when(auditRepository.saveAll(anyIterable())).thenReturn(Flux.empty());

            StepVerifier.create(service.logFieldCorrections(doc, newJson))
                    .verifyComplete();

            ArgumentCaptor<Iterable<FieldCorrectionAudit>> captor = ArgumentCaptor.forClass(Iterable.class);
            verify(auditRepository).saveAll(captor.capture());
            assertThat(toList(captor.getValue())).isEmpty();
        }

        @Test
        @DisplayName("Field thường đổi CÙNG LÚC với metadata đổi -> chỉ audit field thường, bỏ qua metadata")
        void mixedChange_onlyBusinessFieldAudited() {
            String oldJson = "{\"party_a\":\"A\",\"field_confidences\":\"0.8\",\"anomaly_analysis\":\"none\"}";
            String newJson = "{\"party_a\":\"A2\",\"field_confidences\":\"0.95\",\"anomaly_analysis\":\"flagged\"}";
            Document doc = buildDoc(8L, 10L, null, oldJson);
            when(auditRepository.saveAll(anyIterable()))
                    .thenAnswer(inv -> Flux.fromIterable((Iterable<FieldCorrectionAudit>) inv.getArgument(0)));

            StepVerifier.create(service.logFieldCorrections(doc, newJson))
                    .verifyComplete();

            ArgumentCaptor<Iterable<FieldCorrectionAudit>> captor = ArgumentCaptor.forClass(Iterable.class);
            verify(auditRepository).saveAll(captor.capture());
            List<FieldCorrectionAudit> audits = toList(captor.getValue());

            assertThat(audits).hasSize(1);
            assertThat(audits.get(0).getFieldName()).isEqualTo("party_a");
        }
    }

    // ---------------------------------------------------------------------
    // Trường hợp lỗi
    // ---------------------------------------------------------------------
    @Nested
    @DisplayName("Trường hợp lỗi")
    class ErrorCases {

        @Test
        @DisplayName("newExtractedFieldsJson không phải JSON hợp lệ -> Mono lỗi, KHÔNG gọi repository")
        void invalidNewJson_shouldPropagateError_withoutTouchingRepository() {
            Document doc = buildDoc(1L, 10L, null, "{}");

            StepVerifier.create(service.logFieldCorrections(doc, "khong-phai-json"))
                    .expectError()
                    .verify();

            verifyNoInteractions(auditRepository);
        }

        @Test
        @DisplayName("extractedFields cũ trong Document bị lưu sai định dạng JSON -> Mono lỗi")
        void invalidOldJsonInDocument_shouldPropagateError() {
            // Trường hợp dữ liệu cũ trong DB đã bị hỏng (vd do lỗi index.html như đã gặp trước đó)
            Document doc = buildDoc(1L, 10L, null, "{khong-hop-le");

            StepVerifier.create(service.logFieldCorrections(doc, "{\"party_a\":\"A\"}"))
                    .expectError()
                    .verify();

            verifyNoInteractions(auditRepository);
        }
    }
}