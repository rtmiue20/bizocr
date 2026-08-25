package com.company.bizocr.pipeline;

import com.company.bizocr.classify.ClassifierService;
import com.company.bizocr.extract.FieldExtractorService;
import com.company.bizocr.model.entity.DocumentRecord;
import com.company.bizocr.ocr.OcrClientService;
import com.company.bizocr.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineOrchestrator {

    private final OcrClientService ocrClientService;
    private final ClassifierService classifierService;
    private final FieldExtractorService fieldExtractorService;
    private final DocumentRepository documentRepository;

    /**
     * Luồng xử lý chính: Chạy trực tiếp từ FilePart (nếu cần)
     */
    public Mono<DocumentRecord> processDocument(FilePart filePart, DocumentRecord initialRecord) {
        log.info("Bắt đầu pipeline xử lý cho file: {}", filePart.filename());

        return ocrClientService.extractText(filePart)
                .flatMap(ocrResult -> {
                    String rawText = ocrResult.getRawText();

                    if (rawText == null || rawText.isBlank()) {
                        return markAsFailed(initialRecord);
                    }

                    // Bước 2: Gọi AI/Template Phân loại chứng từ
                    return classifierService.classifyDocument(rawText)
                            .flatMap(classifyResult -> {
                                // Cập nhật thông tin phân loại vào bản ghi
                                initialRecord.setDocumentType(classifyResult.getDocumentType());
                                initialRecord.setConfidenceScore(classifyResult.getConfidenceScore());

                                // Bước 3: Gọi AI bóc tách thông tin động thành JSON
                                return fieldExtractorService.extractFields(classifyResult.getDocumentType(), rawText)
                                        .flatMap(extractedJson -> {

                                            // Bước 4: Lưu toàn bộ kết quả xuống Database
                                            initialRecord.setExtractedFields(extractedJson);
                                            initialRecord.setStatus("COMPLETED");

                                            log.info("Xử lý thành công file {}. Loại: {}", filePart.filename(), classifyResult.getDocumentType());
                                            return documentRepository.save(initialRecord);
                                        });
                            });
                })
                .onErrorResume(error -> {
                    log.error("Pipeline thất bại cho file {}: {}", filePart.filename(), error.getMessage());
                    return markAsFailed(initialRecord);
                });
    }

    // Hàm tiện ích: Đánh dấu file lỗi nếu có sự cố
    private Mono<DocumentRecord> markAsFailed(DocumentRecord record) {
        record.setStatus("FAILED");
        return documentRepository.save(record);
    }

    public Mono<DocumentRecord> processDocument(org.springframework.core.io.Resource resource, DocumentRecord record) {
        record.setStatus("PROCESSING");

        return documentRepository.save(record)
                .flatMap(savedRecord -> ocrClientService.scanDocument(resource))
                .flatMap(ocrResult -> {
                    if (ocrResult.getRawText() == null || ocrResult.getRawText().isBlank()) {
                        return markAsFailed(record);
                    }
                    // Xử lý phân loại và bóc tách dữ liệu
                    return classifierService.classifyDocument(ocrResult.getRawText())
                            .flatMap(classifyResult ->
                                    fieldExtractorService.extractFields(classifyResult.getDocumentType(), ocrResult.getRawText())
                                            .map(extractedFields -> {
                                                record.setDocumentType(classifyResult.getDocumentType());
                                                record.setConfidenceScore(classifyResult.getConfidenceScore());
                                                record.setExtractedFields(extractedFields);
                                                record.setStatus("COMPLETED");
                                                return record;
                                            })
                            );
                })
                .flatMap(documentRepository::save)
                .onErrorResume(error -> {
                    log.error(
                            "Pipeline thất bại cho document ID {}",
                            record.getId(),
                            error
                    );

                    record.setStatus("FAILED");

                    return documentRepository.save(record)
                            .onErrorResume(saveError -> {
                                log.error(
                                        "Không thể cập nhật trạng thái FAILED cho document ID {}",
                                        record.getId(),
                                        saveError
                                );
                                return Mono.just(record);
                            });
                });
    }
}