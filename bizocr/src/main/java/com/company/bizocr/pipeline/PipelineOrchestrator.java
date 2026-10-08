package com.company.bizocr.pipeline;

import com.company.bizocr.classify.ClassifierService;
import com.company.bizocr.company.CompanyResolverService;
import com.company.bizocr.extract.FieldExtractorService;
import com.company.bizocr.model.entity.Document;
import com.company.bizocr.model.entity.DocumentStatus;
import com.company.bizocr.ocr.OcrClientService;
import com.company.bizocr.repository.DocumentRepository;
import com.company.bizocr.service.OllamaQueueService;
import com.company.bizocr.statemachine.DocumentStateMachine;
import com.company.bizocr.statemachine.DocumentStateMachine.TransitionResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Điều phối luồng xử lý chứng từ OCR.
 *
 * <h3>Thay đổi so với phiên bản cũ</h3>
 * <p>Mọi cập nhật trạng thái nay đi qua {@link DocumentStateMachine#transition},
 * không còn {@code setStatus(String)} + {@code save()} rải rác.
 * Điều này đảm bảo:
 * <ul>
 *   <li>Race-condition được phát hiện tại DB (UPDATE WHERE status = :from).</li>
 *   <li>Phân biệt rõ <b>idempotent retry</b> (vô hại) vs <b>state mismatch</b> (lệch luồng thật).</li>
 *   <li>Bảng FSM là nguồn sự thật duy nhất — transition sai bị reject ngay trước khi chạm DB.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineOrchestrator {

    /**
     * Enum quản lý các chiến lược phân loại & bóc tách chứng từ.
     */
    public enum MatchStrategy {
        TEMPLATE_MATCHING,
        REGEX_PARSER,
        OLLAMA_LOCAL,
        AI_FALLBACK;

        public static boolean isAiFallback(String matchedBy) {
            if (matchedBy == null) return false;
            return OLLAMA_LOCAL.name().equalsIgnoreCase(matchedBy)
                    || AI_FALLBACK.name().equalsIgnoreCase(matchedBy);
        }
    }

    private final OcrClientService ocrClientService;
    private final ClassifierService classifierService;
    private final FieldExtractorService fieldExtractorService;
    private final DocumentRepository documentRepository;
    private final CompanyResolverService companyResolverService;
    private final ObjectMapper objectMapper;
    private final DocumentStateMachine stateMachine;
    private final OllamaQueueService ollamaQueueService;
    private final com.company.bizocr.score.CompletenessScoreService completenessScoreService;

    // =========================================================================
    // Public entry points
    // =========================================================================

    /** Luồng 1: Chạy trực tiếp từ FilePart (upload sync) */
    public Mono<Document> processDocument(FilePart filePart, Document initialRecord) {
        log.info("[Pipeline] Bắt đầu xử lý file: {}", filePart.filename());
        return processInternal(ocrClientService.extractText(filePart), initialRecord);
    }

    /** Luồng 2: Chạy từ Resource (Queue/Consumer) */
    public Mono<Document> processDocument(Resource resource, Document record) {
        return processInternal(ocrClientService.scanDocument(resource), record);
    }

    // =========================================================================
    // Internal pipeline
    // =========================================================================

    private Mono<Document> processInternal(
            Mono<com.company.bizocr.model.dto.OcrResult> ocrResultMono,
            Document record) {

        // ── Step 0: PENDING → PROCESSING ─────────────────────────────────────
        return stateMachine.transition(record.getId(), DocumentStatus.PENDING, DocumentStatus.PROCESSING)
                .flatMap(result -> handleMismatch(result, record, DocumentStatus.PROCESSING))

                // ── Step 1: Gọi OCR engine ────────────────────────────────────
                .then(ocrResultMono)
                .flatMap(ocrResult -> {
                    String rawText = ocrResult.getRawText();
                    record.setRawOcrText(rawText);
                    record.setOcrEngine(ocrResult.getEngine());

                    final Double contourAreaRatio = ocrResult.getContourAreaRatio();
                    final Double avgOcrConfidence = ocrResult.getConfidence();

                    if (rawText == null || rawText.isBlank()) {
                        return forceFailAndError(record, "OCR trả về text rỗng");
                    }

                    // ── Step 2: PROCESSING → OCR_DONE ────────────────────────
                    return stateMachine.transition(record.getId(),
                                    DocumentStatus.PROCESSING, DocumentStatus.OCR_DONE)
                            .flatMap(r -> handleMismatch(r, record, DocumentStatus.OCR_DONE))

                            // ── Step 3: Đối chiếu công ty (không đổi trạng thái) ─
                            .then(companyResolverService.resolve(rawText))
                            .flatMap(companyInfo -> {
                                if (companyInfo.isFound()) {
                                    log.info("[Pipeline] Matched công ty: {} score={}",
                                            companyInfo.getCompanyName(),
                                            companyInfo.getMatchScore());
                                    record.setCompanyId(companyInfo.getCompanyId());
                                }

                                // ── Step 4: OCR_DONE → CLASSIFIED ────────────
                                return classifierService.classifyDocument(rawText)
                                        .flatMap(classifyResult -> {
                                            record.setDocumentType(classifyResult.getDocumentType());
                                            record.setConfidenceScore(classifyResult.getConfidenceScore());

                                            boolean usedAiFallback = MatchStrategy.isAiFallback(
                                                    classifyResult.getMatchedBy());
                                            record.setUsedAiFallback(usedAiFallback);
                                            String matchedBy = usedAiFallback
                                                    ? MatchStrategy.OLLAMA_LOCAL.name()
                                                    : classifyResult.getMatchedBy();
                                            record.setMatchedBy(matchedBy);

                                            String realModelVersion;
                                            if (usedAiFallback) {
                                                realModelVersion = "ollama-local";
                                            } else if ("ML_SYSTEM".equalsIgnoreCase(matchedBy)) {
                                                realModelVersion = "python-ml-v1";
                                            } else {
                                                realModelVersion = "rule-template-v1";
                                            }
                                            record.setModelVersion(realModelVersion);

                                            return stateMachine.transition(record.getId(),
                                                            DocumentStatus.OCR_DONE, DocumentStatus.CLASSIFIED)
                                                    .flatMap(r -> handleMismatch(r, record, DocumentStatus.CLASSIFIED))

                                                    // ── Step 5: CLASSIFIED → EXTRACTED ──────
                                                    .then(Mono.defer(() -> {
                                                                String docId = record.getId() != null
                                                                        ? String.valueOf(record.getId()) : null;
                                                                Mono<String> extractMono = docId != null
                                                                        ? fieldExtractorService.extractFields(
                                                                        docId, classifyResult, rawText)
                                                                        : fieldExtractorService.extractFields(
                                                                        classifyResult, rawText);
                                                                return extractMono;
                                                            })
                                                            .flatMap(extractedFields -> {
                                                                // Tính Completeness Score & nhúng _meta vào extractedFields JSON
                                                                String enrichedFields = extractedFields;
                                                                try {
                                                                    com.company.bizocr.score.CompletenessScoreResult csResult =
                                                                            completenessScoreService.compute(
                                                                                    record.getDocumentType(),
                                                                                    extractedFields,
                                                                                    contourAreaRatio,
                                                                                    avgOcrConfidence
                                                                            );

                                                                    com.fasterxml.jackson.databind.node.ObjectNode root =
                                                                            (extractedFields != null && !extractedFields.isBlank())
                                                                                    ? (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(extractedFields)
                                                                                    : objectMapper.createObjectNode();

                                                                    com.fasterxml.jackson.databind.node.ObjectNode meta = objectMapper.createObjectNode();
                                                                    meta.put("completeness_score", csResult.score());
                                                                    meta.put("completeness_grade", csResult.grade());
                                                                    meta.put("completeness_label", csResult.label());
                                                                    meta.put("mandatory_fields_ratio", csResult.mandatoryFieldsRatio());
                                                                    meta.put("contour_area_ratio", csResult.contourAreaRatio());
                                                                    meta.put("avg_ocr_confidence", csResult.avgOcrConfidence());
                                                                    meta.put("mandatory_filled", csResult.mandatoryFilled());
                                                                    meta.put("mandatory_total", csResult.mandatoryTotal());
                                                                    meta.put("confirm_prompt", completenessScoreService.buildConfirmPrompt(csResult));

                                                                    root.set("_meta", meta);
                                                                    enrichedFields = objectMapper.writeValueAsString(root);
                                                                    log.info("[Pipeline] documentId={} completeness={} grade={}",
                                                                            record.getId(), csResult.score(), csResult.grade());
                                                                } catch (Exception e) {
                                                                    log.warn("[Pipeline] Không thể tính CompletenessScore: {}", e.getMessage());
                                                                }

                                                                record.setExtractedFields(enrichedFields);

                                                                return stateMachine.transition(record.getId(),
                                                                                DocumentStatus.CLASSIFIED,
                                                                                DocumentStatus.EXTRACTED)
                                                                        .flatMap(r -> handleMismatch(r, record, DocumentStatus.EXTRACTED))

                                                                        // ── Step 6: Quyết định VALIDATED vs NEED_REVIEW
                                                                        .then(Mono.defer(() -> {
                                                                            boolean lowConfidence =
                                                                                    classifyResult.getConfidenceScore() == null
                                                                                            || classifyResult.getConfidenceScore() < 0.90;
                                                                            boolean fieldsIncomplete =
                                                                                    extractedFields == null
                                                                                            || extractedFields.isBlank()
                                                                                            || "{}".equals(extractedFields);

                                                                            boolean hasValidationReviewRequirement = false;
                                                                            if (extractedFields != null && !extractedFields.isBlank()) {
                                                                                try {
                                                                                    JsonNode jsonTree = objectMapper.readTree(extractedFields);
                                                                                    if (jsonTree.has("review_status") &&
                                                                                            "PENDING_AI_WORKER".equalsIgnoreCase(jsonTree.get("review_status").asText())) {
                                                                                        hasValidationReviewRequirement = true;
                                                                                    }
                                                                                } catch (Exception ignored) {
                                                                                }
                                                                            }

                                                                            if (usedAiFallback || lowConfidence
                                                                                    || fieldsIncomplete
                                                                                    || !companyInfo.isFound()
                                                                                    || hasValidationReviewRequirement) {
                                                                                // → NEED_REVIEW
                                                                                boolean finalHasValidationReviewRequirement = hasValidationReviewRequirement;
                                                                                return stateMachine.transition(
                                                                                                record.getId(),
                                                                                                DocumentStatus.EXTRACTED,
                                                                                                DocumentStatus.NEED_REVIEW)
                                                                                        .flatMap(r -> handleMismatch(r, record, DocumentStatus.NEED_REVIEW))
                                                                                        .then(Mono.defer(() -> {
                                                                                            record.setStatus(DocumentStatus.NEED_REVIEW.name());
                                                                                            return persistRecord(record)
                                                                                                    .doOnSuccess(savedDoc -> {
                                                                                                        // Chỉ push queue SAU KHI đã lưu DB thành công
                                                                                                        if (finalHasValidationReviewRequirement && savedDoc.getId() != null) {
                                                                                                            log.info("[Pipeline] Đẩy task AI cho documentId={} vào queue", savedDoc.getId());
                                                                                                            ollamaQueueService.enqueueTask(
                                                                                                                    String.valueOf(savedDoc.getId()),
                                                                                                                    savedDoc.getDocumentType(),
                                                                                                                    savedDoc.getRawOcrText()
                                                                                                            );
                                                                                                        }
                                                                                                    });
                                                                                        }));
                                                                            } else {
                                                                                // → VALIDATED → (APPROVED chờ reviewer)
                                                                                return stateMachine.transition(
                                                                                                record.getId(),
                                                                                                DocumentStatus.EXTRACTED,
                                                                                                DocumentStatus.VALIDATED)
                                                                                        .flatMap(r -> handleMismatch(r,
                                                                                                record,
                                                                                                DocumentStatus.VALIDATED))
                                                                                        .then(Mono.defer(() -> {
                                                                                            record.setStatus(DocumentStatus.VALIDATED.name());
                                                                                            return persistRecord(record);
                                                                                        }));
                                                                            }
                                                                        }));
                                                            }));
                                        });
                            });
                })
                .onErrorResume(error -> {
                    log.error("[Pipeline] Lỗi kỹ thuật cho documentId={}", record.getId(), error);
                    return documentRepository.updateStatusForced(record.getId(), DocumentStatus.FAILED.name())
                            .doOnNext(rows -> {
                                if (rows == 0) {
                                    log.warn("[Pipeline] updateStatusForced không update được row nào " +
                                            "cho documentId={} (có thể đã FAILED rồi)", record.getId());
                                }
                            })
                            .then(Mono.error(error));
                });
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Xử lý kết quả transition:
     * <ul>
     *   <li>SUCCESS → tiếp tục bình thường</li>
     *   <li>IDEMPOTENT → log warn + tiếp tục (retry an toàn)</li>
     *   <li>MISMATCH → ném lỗi, dừng pipeline</li>
     * </ul>
     */
    private Mono<Void> handleMismatch(TransitionResult result, Document record,
                                      DocumentStatus expectedTarget) {
        return switch (result) {
            case SUCCESS -> Mono.empty();
            case IDEMPOTENT -> {
                log.warn("[Pipeline] documentId={} đã ở {} (idempotent, bỏ qua)",
                        record.getId(), expectedTarget);
                yield Mono.empty();
            }
            case MISMATCH -> Mono.error(new DocumentStateMachine.IllegalStateTransitionException(
                    String.format("documentId=%d state mismatch khi chuyển sang %s — dừng pipeline",
                            record.getId(), expectedTarget)));
        };
    }

    /** Lưu snapshot field xuống DB có điều kiện (chỉ khi reviewed_by IS NULL) */
    private Mono<Document> persistRecord(Document record) {
        log.info("[Pipeline] documentId={} type={} matchedBy={} confidence={} status={}",
                record.getId(), record.getDocumentType(), record.getMatchedBy(),
                record.getConfidenceScore(), record.getStatus());

        return documentRepository.updatePipelineResultsIfNotReviewed(
                record.getId(),
                record.getDocumentType(),
                record.getConfidenceScore(),
                record.getMatchedBy(),
                record.getUsedAiFallback(),
                record.getModelVersion(),
                record.getRawOcrText(),
                record.getOcrEngine(),
                record.getCompanyId(),
                record.getExtractedFields()
        ).doOnNext(rows -> {
            if (rows == 0) {
                log.warn("[Pipeline] documentId={} không thể persist: reviewed_by IS NOT NULL hoặc status không khớp (bảo vệ can thiệp của người dùng)", record.getId());
            }
        }).thenReturn(record);
    }

    /** Ép FAILED và propagate lỗi ra ngoài (dùng trong onErrorResume đơn giản) */
    private Mono<Document> forceFailAndError(Document record, String reason) {
        log.error("[Pipeline] documentId={} → FAILED: {}", record.getId(), reason);
        return documentRepository.updateStatusForced(record.getId(), DocumentStatus.FAILED.name())
                .then(Mono.error(new IllegalStateException(reason)));
    }
}