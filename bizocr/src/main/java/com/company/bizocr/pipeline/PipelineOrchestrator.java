package com.company.bizocr.pipeline;

import com.company.bizocr.classify.ClassifierService;
import com.company.bizocr.company.CompanyResolverService;
import com.company.bizocr.extract.FieldExtractorService;
import com.company.bizocr.model.entity.Document;
import com.company.bizocr.ocr.OcrClientService;
import com.company.bizocr.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineOrchestrator {

    // Enum quản lý các chiến lược phân loại & bóc tách chứng từ
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

    /**
     * Luồng xử lý chính 1: Chạy trực tiếp từ FilePart
     */
    public Mono<Document> processDocument(FilePart filePart, Document initialRecord) {
        log.info("Bắt đầu pipeline xử lý cho file: {}", filePart.filename());
        initialRecord.setStatus("PROCESSING");

        return documentRepository.save(initialRecord)
                .flatMap(savedRecord -> ocrClientService.extractText(filePart))
                .flatMap(ocrResult -> {
                    String rawText = ocrResult.getRawText();
                    initialRecord.setRawOcrText(rawText);
                    initialRecord.setOcrEngine(ocrResult.getEngine());

                    if (rawText == null || rawText.isBlank()) {
                        return markAsFailed(initialRecord);
                    }

                    // Bước 1: Đối chiếu công ty
                    return companyResolverService.resolve(rawText)
                            .flatMap(companyInfo -> {
                                if (companyInfo.isFound()) {
                                    log.info("Đã match công ty: {} với score {}",
                                            companyInfo.getCompanyName(),
                                            companyInfo.getMatchScore());
                                    initialRecord.setCompanyId(companyInfo.getCompanyId());
                                }

                                // Bước 2: Phân loại chứng từ
                                return classifierService.classifyDocument(rawText)
                                        .flatMap(classifyResult -> {
                                            initialRecord.setDocumentType(classifyResult.getDocumentType());
                                            initialRecord.setConfidenceScore(classifyResult.getConfidenceScore());

                                            // Kiểm tra và chuẩn hóa nhãn Engine Fallback
                                            boolean usedAiFallback = MatchStrategy.isAiFallback(classifyResult.getMatchedBy());
                                            initialRecord.setUsedAiFallback(usedAiFallback);

                                            // Thống nhất ghi nhận nhãn OLLAMA_LOCAL vào DB nếu dùng AI Fallback
                                            String normalizedMatchedBy = usedAiFallback
                                                    ? MatchStrategy.OLLAMA_LOCAL.name()
                                                    : classifyResult.getMatchedBy();
                                            initialRecord.setMatchedBy(normalizedMatchedBy);

                                            initialRecord.setModelVersion("template-v1");

                                            // Bước 3: Bóc tách dữ liệu
                                            return fieldExtractorService.extractFields(classifyResult, rawText)
                                                    .map(extractedFields -> {
                                                        initialRecord.setExtractedFields(extractedFields);

                                                        boolean lowConfidence = classifyResult.getConfidenceScore() == null
                                                                || classifyResult.getConfidenceScore() < 0.90;

                                                        boolean fieldsIncomplete = extractedFields == null
                                                                || extractedFields.isBlank()
                                                                || "{}".equals(extractedFields);

                                                        // Nếu AI nhận diện yếu, thiếu field, HOẶC không tìm thấy công ty -> Needs Review
                                                        if (usedAiFallback || lowConfidence || fieldsIncomplete || !companyInfo.isFound()) {
                                                            initialRecord.setStatus("NEED_REVIEW");
                                                        } else {
                                                            initialRecord.setStatus("COMPLETED");
                                                        }

                                                        log.info("Document {}: type={}, matchedBy={}, status={}",
                                                                initialRecord.getId(),
                                                                classifyResult.getDocumentType(),
                                                                initialRecord.getMatchedBy(),
                                                                initialRecord.getStatus());

                                                        return initialRecord;
                                                    });
                                        });
                            });
                })
                .flatMap(documentRepository::save)
                .onErrorResume(error -> {
                    log.error("Pipeline thất bại cho document ID {}", initialRecord.getId(), error);
                    initialRecord.setStatus("FAILED");
                    return documentRepository.save(initialRecord)
                            .onErrorResume(saveError -> {
                                log.error("Không thể cập nhật trạng thái FAILED cho document ID {}", initialRecord.getId(), saveError);
                                return Mono.just(initialRecord);
                            })
                            // Đã lưu status FAILED vào DB, nhưng vẫn phải "ném" lại lỗi gốc để
                            // OcrQueueConsumer nhận biết đây là job thất bại (log đúng, đẩy vào DLQ)
                            // thay vì bị coi là một luồng thành công bình thường.
                            .then(Mono.error(error));
                });
    }

    /**
     * Luồng xử lý chính 2: Chạy từ Resource (Dùng trong Queue/Consumer)
     */
    public Mono<Document> processDocument(Resource resource, Document record) {
        record.setStatus("PROCESSING");

        return documentRepository.save(record)
                .flatMap(savedRecord -> ocrClientService.scanDocument(resource))
                .flatMap(ocrResult -> {
                    String rawText = ocrResult.getRawText();
                    record.setRawOcrText(rawText);
                    record.setOcrEngine(ocrResult.getEngine());

                    if (rawText == null || rawText.isBlank()) {
                        return markAsFailed(record);
                    }

                    // Bước 1: Đối chiếu công ty
                    return companyResolverService.resolve(rawText)
                            .flatMap(companyInfo -> {
                                if (companyInfo.isFound()) {
                                    log.info("Đã match công ty: {} với score {}",
                                            companyInfo.getCompanyName(),
                                            companyInfo.getMatchScore());
                                    record.setCompanyId(companyInfo.getCompanyId());
                                }

                                // Bước 2: Phân loại chứng từ
                                return classifierService.classifyDocument(rawText)
                                        .flatMap(classifyResult -> {
                                            record.setDocumentType(classifyResult.getDocumentType());
                                            record.setConfidenceScore(classifyResult.getConfidenceScore());

                                            // Kiểm tra và chuẩn hóa nhãn Engine Fallback
                                            boolean usedAiFallback = MatchStrategy.isAiFallback(classifyResult.getMatchedBy());
                                            record.setUsedAiFallback(usedAiFallback);

                                            // Thống nhất ghi nhận nhãn OLLAMA_LOCAL vào DB nếu dùng AI Fallback
                                            String normalizedMatchedBy = usedAiFallback
                                                    ? MatchStrategy.OLLAMA_LOCAL.name()
                                                    : classifyResult.getMatchedBy();
                                            record.setMatchedBy(normalizedMatchedBy);

                                            record.setModelVersion("template-v1");

                                            // Bước 3: Bóc tách dữ liệu
                                            return fieldExtractorService.extractFields(classifyResult, rawText)
                                                    .map(extractedFields -> {
                                                        record.setExtractedFields(extractedFields);

                                                        boolean lowConfidence = classifyResult.getConfidenceScore() == null
                                                                || classifyResult.getConfidenceScore() < 0.90;

                                                        boolean fieldsIncomplete = extractedFields == null
                                                                || extractedFields.isBlank()
                                                                || "{}".equals(extractedFields);

                                                        if (usedAiFallback || lowConfidence || fieldsIncomplete || !companyInfo.isFound()) {
                                                            record.setStatus("NEED_REVIEW");
                                                        } else {
                                                            record.setStatus("COMPLETED");
                                                        }

                                                        log.info("Document {}: type={}, matchedBy={}, confidence={}, status={}",
                                                                record.getId(),
                                                                record.getDocumentType(),
                                                                record.getMatchedBy(),
                                                                record.getConfidenceScore(),
                                                                record.getStatus());

                                                        return record;
                                                    });
                                        });
                            });
                })
                .flatMap(documentRepository::save)
                .onErrorResume(error -> {
                    log.error("Pipeline thất bại cho document ID {}", record.getId(), error);
                    record.setStatus("FAILED");
                    return documentRepository.save(record)
                            .onErrorResume(saveError -> {
                                log.error("Không thể cập nhật trạng thái FAILED cho document ID {}", record.getId(), saveError);
                                return Mono.just(record);
                            })
                            // Đã lưu status FAILED vào DB, nhưng vẫn phải "ném" lại lỗi gốc để
                            // OcrQueueConsumer nhận biết đây là job thất bại (log đúng, đẩy vào DLQ)
                            // thay vì bị coi là một luồng thành công bình thường.
                            .then(Mono.error(error));
                });
    }

    // Hàm tiện ích: Đánh dấu file lỗi nếu có sự cố
    private Mono<Document> markAsFailed(Document record) {
        record.setStatus("FAILED");
        return documentRepository.save(record);
    }
}