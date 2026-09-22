package com.company.bizocr.statemachine;

import com.company.bizocr.model.entity.DocumentStatus;
import com.company.bizocr.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * <h2>Document State Machine</h2>
 *
 * <p>Điểm kiểm soát duy nhất (Single Source of Truth) cho mọi thay đổi trạng thái
 * chứng từ trong hệ thống. Thay vì gọi {@code doc.setStatus()} trực tiếp rồi
 * {@code repo.save()}, tất cả các bước pipeline và reviewer action phải đi qua
 * {@link #transition(Long, DocumentStatus, DocumentStatus)}.
 *
 * <h3>Tại sao cần FSM trong môi trường Reactive (WebFlux)?</h3>
 * <p>Java Spring WebFlux xử lý nhiều event song song trên thread-pool nhỏ.
 * Nếu 2 luồng cùng nhận một document và đều gọi {@code setStatus("PROCESSING")}
 * rồi {@code save()}, trạng thái cuối cùng trong DB phụ thuộc vào ai write sau cùng —
 * dữ liệu bị ghi đè ngầm (lost update).
 *
 * <h3>Giải pháp: Optimistic-lock bằng điều kiện WHERE</h3>
 * <pre>
 *   UPDATE documents SET status = :to WHERE id = :id AND status = :from
 * </pre>
 * <ul>
 *   <li>Nếu {@code rowsAffected == 1} → transition thành công.</li>
 *   <li>Nếu {@code rowsAffected == 0}:
 *     <ul>
 *       <li>Document đã ở trạng thái {@code to} → <b>idempotent</b> (retry/duplicate request vô hại).</li>
 *       <li>Document đang ở trạng thái khác {@code from} và khác {@code to} → <b>state mismatch</b>
 *           (lệch luồng race-condition thật sự).</li>
 *     </ul>
 *   </li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentStateMachine {

    private final DocumentRepository documentRepository;

    /**
     * Thực hiện chuyển trạng thái chứng từ {@code from → to} một cách an toàn.
     *
     * <p>Phương thức phát ra:
     * <ul>
     *   <li>{@link TransitionResult#SUCCESS} — UPDATE thành công (1 row affected)</li>
     *   <li>{@link TransitionResult#IDEMPOTENT} — Document đã ở trạng thái {@code to} rồi
     *       (0 row affected + currentStatus == to), retry an toàn</li>
     *   <li>{@link TransitionResult#MISMATCH} — Document đang ở trạng thái khác, không phải
     *       {@code from} cũng không phải {@code to} (0 row affected + currentStatus khác),
     *       báo hiệu race-condition thật sự</li>
     *   <li>error signal — transition không nằm trong bảng FSM (illegal transition)</li>
     * </ul>
     *
     * @param documentId ID của document
     * @param from       trạng thái hiện tại mong đợi
     * @param to         trạng thái muốn chuyển sang
     * @return {@code Mono<TransitionResult>}
     */
    public Mono<TransitionResult> transition(Long documentId, DocumentStatus from, DocumentStatus to) {

        // ── Kiểm tra transition hợp lệ theo bảng FSM trước khi chạm DB ──────
        if (!DocumentStatus.isAllowed(from, to)) {
            log.error("[FSM] Transition không hợp lệ: {} → {} cho documentId={}",
                    from, to, documentId);
            return Mono.error(new IllegalStateTransitionException(
                    String.format("Transition %s → %s không được phép trong FSM", from, to)));
        }

        // ── Gọi UPDATE có điều kiện WHERE status = :from ──────────────────────
        return documentRepository.updateStatusIfMatch(documentId, from.name(), to.name())
                .flatMap(rowsAffected -> {
                    if (rowsAffected == 1) {
                        // Thành công bình thường
                        log.info("[FSM] documentId={} {} → {} ✓ (rowsAffected=1)",
                                documentId, from, to);
                        return Mono.just(TransitionResult.SUCCESS);
                    }

                    // rowsAffected == 0 → cần phân biệt idempotent vs mismatch
                    return documentRepository.findById(documentId)
                            .map(current -> {
                                DocumentStatus currentStatus =
                                        DocumentStatus.fromString(current.getStatus());

                                if (to.equals(currentStatus)) {
                                    // Document đã ở trạng thái to → idempotent (retry vô hại)
                                    log.warn("[FSM] documentId={} {} → {} IDEMPOTENT — " +
                                                    "document đã ở trạng thái {} rồi (rowsAffected=0)",
                                            documentId, from, to, currentStatus);
                                    return TransitionResult.IDEMPOTENT;
                                } else {
                                    // Document đang ở trạng thái khác → lệch luồng thật sự
                                    log.error("[FSM] documentId={} {} → {} MISMATCH — " +
                                                    "trạng thái thực tế trong DB là {} (rowsAffected=0). " +
                                                    "Có thể do race-condition hoặc job trùng lặp.",
                                            documentId, from, to, currentStatus);
                                    return TransitionResult.MISMATCH;
                                }
                            })
                            .defaultIfEmpty(TransitionResult.MISMATCH); // Document không tồn tại
                });
    }

    /**
     * Chuyển trạng thái sang {@code FAILED} từ bất kỳ trạng thái nào (emergency path).
     * Dùng trong {@code onErrorResume} của pipeline — không kiểm tra bảng FSM.
     *
     * <p><b>Lưu ý:</b> Chỉ dùng khi đã có lỗi kỹ thuật không thể phục hồi.
     * Mọi transition thông thường phải đi qua {@link #transition}.
     *
     * @param documentId ID của document
     * @param fromStatus trạng thái hiện tại (để log, không dùng làm điều kiện WHERE)
     * @return số row bị ảnh hưởng
     */
    public Mono<Integer> forceMarkFailed(Long documentId, String fromStatus) {
        log.warn("[FSM] documentId={} forceMarkFailed từ trạng thái={}", documentId, fromStatus);
        return documentRepository.updateStatusForced(documentId, DocumentStatus.FAILED.name());
    }

    // =========================================================================
    // Inner types
    // =========================================================================

    /**
     * Kết quả của một lần gọi {@link #transition}.
     */
    public enum TransitionResult {
        /** UPDATE thành công — 1 row affected */
        SUCCESS,
        /**
         * Document đã ở trạng thái {@code to} — 0 row affected nhưng an toàn.
         * Caller có thể bỏ qua và tiếp tục pipeline bình thường.
         */
        IDEMPOTENT,
        /**
         * Document đang ở trạng thái khác — 0 row affected, dấu hiệu race-condition.
         * Caller nên dừng xử lý document này và ghi log nghiêm trọng.
         */
        MISMATCH
    }

    /**
     * Exception ném ra khi transition không nằm trong bảng FSM.
     */
    public static class IllegalStateTransitionException extends RuntimeException {
        public IllegalStateTransitionException(String message) {
            super(message);
        }
    }
}
