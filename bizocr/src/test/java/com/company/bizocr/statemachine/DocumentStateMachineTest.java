package com.company.bizocr.statemachine;

import com.company.bizocr.model.entity.Document;
import com.company.bizocr.model.entity.DocumentStatus;
import com.company.bizocr.repository.DocumentRepository;
import com.company.bizocr.statemachine.DocumentStateMachine.IllegalStateTransitionException;
import com.company.bizocr.statemachine.DocumentStateMachine.TransitionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * Unit test cho {@link DocumentStateMachine}.
 *
 * <p>Mỗi test tập trung vào một tình huống cụ thể:
 * <ul>
 *   <li>Transition hợp lệ → SUCCESS</li>
 *   <li>Idempotent retry (đã ở trạng thái to) → IDEMPOTENT</li>
 *   <li>Race-condition thật (đang ở trạng thái khác) → MISMATCH</li>
 *   <li>Transition nằm ngoài bảng FSM → error signal</li>
 *   <li>Bảng FSM: kiểm tra toàn bộ transitions hợp lệ và bất hợp lệ</li>
 * </ul>
 */
class DocumentStateMachineTest {

    private DocumentRepository documentRepository;
    private DocumentStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        documentRepository = mock(DocumentRepository.class);
        stateMachine = new DocumentStateMachine(documentRepository);
    }

    // =========================================================================
    // 1. Happy path — rowsAffected = 1 → SUCCESS
    // =========================================================================

    @Test
    @DisplayName("Transition hợp lệ PENDING → PROCESSING: rowsAffected=1 → SUCCESS")
    void transition_validPath_returnsSuccess() {
        // GIVEN
        given(documentRepository.updateStatusIfMatch(1L, "PENDING", "PROCESSING"))
                .willReturn(Mono.just(1));

        // WHEN
        Mono<TransitionResult> result = stateMachine.transition(1L,
                DocumentStatus.PENDING, DocumentStatus.PROCESSING);

        // THEN
        StepVerifier.create(result)
                .assertNext(r -> assertThat(r).isEqualTo(TransitionResult.SUCCESS))
                .verifyComplete();

        verify(documentRepository).updateStatusIfMatch(1L, "PENDING", "PROCESSING");
        // Không cần findById — rowsAffected = 1 không cần tra DB lần 2
        verify(documentRepository, never()).findById(anyLong());
    }

    // =========================================================================
    // 2. Idempotent — rowsAffected = 0, nhưng document đã ở trạng thái to
    // =========================================================================

    @Test
    @DisplayName("rowsAffected=0 + doc đã ở PROCESSING → IDEMPOTENT (retry an toàn)")
    void transition_idempotentRetry_returnsIdempotent() {
        // GIVEN: UPDATE không ảnh hưởng row nào (doc đã PROCESSING rồi)
        given(documentRepository.updateStatusIfMatch(2L, "PENDING", "PROCESSING"))
                .willReturn(Mono.just(0));

        Document alreadyProcessing = Document.builder()
                .id(2L).status("PROCESSING").build();
        given(documentRepository.findById(2L))
                .willReturn(Mono.just(alreadyProcessing));

        // WHEN
        Mono<TransitionResult> result = stateMachine.transition(2L,
                DocumentStatus.PENDING, DocumentStatus.PROCESSING);

        // THEN
        StepVerifier.create(result)
                .assertNext(r -> assertThat(r).isEqualTo(TransitionResult.IDEMPOTENT))
                .verifyComplete();
    }

    // =========================================================================
    // 3. MISMATCH — rowsAffected = 0, doc đang ở trạng thái KHÁC (race condition)
    // =========================================================================

    @Test
    @DisplayName("rowsAffected=0 + doc ở FAILED (không phải from, không phải to) → MISMATCH")
    void transition_stateMismatch_returnsMismatch() {
        // GIVEN: UPDATE không ảnh hưởng — doc đã bị luồng khác đưa sang FAILED
        given(documentRepository.updateStatusIfMatch(3L, "PENDING", "PROCESSING"))
                .willReturn(Mono.just(0));

        Document wrongState = Document.builder()
                .id(3L).status("FAILED").build();
        given(documentRepository.findById(3L))
                .willReturn(Mono.just(wrongState));

        // WHEN
        Mono<TransitionResult> result = stateMachine.transition(3L,
                DocumentStatus.PENDING, DocumentStatus.PROCESSING);

        // THEN
        StepVerifier.create(result)
                .assertNext(r -> assertThat(r).isEqualTo(TransitionResult.MISMATCH))
                .verifyComplete();
    }

    // =========================================================================
    // 4. Illegal transition (nằm ngoài bảng FSM)
    // =========================================================================

    @Test
    @DisplayName("Transition APPROVED → PROCESSING không có trong bảng FSM → error")
    void transition_illegalTransition_returnsError() {
        // WHEN — không cần mock vì kiểm tra bảng FSM trước khi chạm DB
        Mono<TransitionResult> result = stateMachine.transition(4L,
                DocumentStatus.APPROVED, DocumentStatus.PROCESSING);

        // THEN
        StepVerifier.create(result)
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(IllegalStateTransitionException.class);
                    assertThat(error.getMessage()).contains("APPROVED → PROCESSING");
                })
                .verify();

        verify(documentRepository, never()).updateStatusIfMatch(any(), any(), any());
    }

    @Test
    @DisplayName("Transition FAILED → PROCESSING không có trong bảng FSM → error")
    void transition_fromTerminalState_returnsError() {
        Mono<TransitionResult> result = stateMachine.transition(5L,
                DocumentStatus.FAILED, DocumentStatus.PROCESSING);

        StepVerifier.create(result)
                .expectError(IllegalStateTransitionException.class)
                .verify();
    }

    // =========================================================================
    // 5. forceMarkFailed — không kiểm tra FSM, UPDATE bất điều kiện
    // =========================================================================

    @Test
    @DisplayName("forceMarkFailed luôn UPDATE và trả về rowsAffected")
    void forceMarkFailed_alwaysUpdates() {
        // GIVEN
        given(documentRepository.updateStatusForced(6L, "FAILED"))
                .willReturn(Mono.just(1));

        // WHEN
        Mono<Integer> result = stateMachine.forceMarkFailed(6L, "PROCESSING");

        // THEN
        StepVerifier.create(result)
                .assertNext(rows -> assertThat(rows).isEqualTo(1))
                .verifyComplete();
    }

    // =========================================================================
    // 6. Bảng FSM — kiểm tra hàng loạt transitions hợp lệ
    // =========================================================================

    @Nested
    @DisplayName("DocumentStatus.isAllowed — bảng transition đầy đủ")
    class AllowedTransitionsTest {

        @ParameterizedTest(name = "{0} → {1} phải ĐƯỢC PHÉP")
        @CsvSource({
                "PENDING,          PROCESSING",
                "PENDING,          FAILED",
                "PROCESSING,       OCR_DONE",
                "PROCESSING,       NEEDS_REVIEW",
                "PROCESSING,       FAILED",
                "OCR_DONE,         CLASSIFIED",
                "OCR_DONE,         NEEDS_REVIEW",
                "OCR_DONE,         FAILED",
                "CLASSIFIED,       EXTRACTED",
                "CLASSIFIED,       NEEDS_REVIEW",
                "CLASSIFIED,       FAILED",
                "EXTRACTED,        VALIDATED",
                "EXTRACTED,        NEEDS_REVIEW",
                "EXTRACTED,        FAILED",
                "VALIDATED,        APPROVED",
                "VALIDATED,        NEEDS_REVIEW",
                "VALIDATED,        FAILED",
                "NEEDS_REVIEW,     APPROVED",
                "NEEDS_REVIEW,     REJECTED_QUALITY",
        })
        void allowed(String from, String to) {
            assertThat(DocumentStatus.isAllowed(
                    DocumentStatus.valueOf(from.trim()),
                    DocumentStatus.valueOf(to.trim())
            )).isTrue();
        }

        @ParameterizedTest(name = "{0} → {1} phải BỊ CHẶN")
        @CsvSource({
                "APPROVED,         PROCESSING",
                "APPROVED,         NEEDS_REVIEW",
                "REJECTED_QUALITY, APPROVED",
                "FAILED,           PROCESSING",
                "FAILED,           PENDING",
                "EXTRACTED,        PENDING",
                "CLASSIFIED,       PENDING",
                "OCR_DONE,         PENDING",
        })
        void blocked(String from, String to) {
            assertThat(DocumentStatus.isAllowed(
                    DocumentStatus.valueOf(from.trim()),
                    DocumentStatus.valueOf(to.trim())
            )).isFalse();
        }
    }

    // =========================================================================
    // 7. fromString helper
    // =========================================================================

    @Test
    @DisplayName("fromString xử lý null, blank, uppercase, unknown gracefully")
    void fromString_edgeCases() {
        assertThat(DocumentStatus.fromString(null)).isNull();
        assertThat(DocumentStatus.fromString("")).isNull();
        assertThat(DocumentStatus.fromString("  ")).isNull();
        assertThat(DocumentStatus.fromString("unknown_xyz")).isNull();
        assertThat(DocumentStatus.fromString("pending")).isEqualTo(DocumentStatus.PENDING);
        assertThat(DocumentStatus.fromString("NEEDS_REVIEW")).isEqualTo(DocumentStatus.NEEDS_REVIEW);
        assertThat(DocumentStatus.fromString(" approved ")).isEqualTo(DocumentStatus.APPROVED);
    }
}
