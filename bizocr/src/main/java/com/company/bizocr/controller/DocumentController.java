package com.company.bizocr.controller;

import com.company.bizocr.model.dto.request.DocumentUpdateRequest;
import com.company.bizocr.model.dto.response.DocumentPageResponse;
import com.company.bizocr.model.dto.response.UploadResponse;
import com.company.bizocr.model.entity.DocumentRecord;
import com.company.bizocr.repository.DocumentRepository;
import com.company.bizocr.service.ExcelExportService;
import com.company.bizocr.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentRepository documentRepository;
    private final StorageService storageService;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ExcelExportService excelExportService;

    // API Upload tập trung: Lưu đĩa -> Tạo DB -> Đẩy vào Redis Queue (Đã thêm log theo dõi Push)
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<UploadResponse> uploadDocument(@RequestPart("file") FilePart filePart) {
        return storageService.saveFile(filePart)
                .flatMap(savedFilePath -> {
                    DocumentRecord record = DocumentRecord.builder()
                            .fileName(filePart.filename())
                            .filePath(savedFilePath)
                            .status("PENDING")
                            .receivedAt(LocalDateTime.now().toString())
                            .build();

                    return documentRepository.save(record);
                })
                .flatMap(savedRecord ->
                        redisTemplate.opsForList()
                                .leftPush("ocr_task_queue", savedRecord.getId().toString())
                                .doOnNext(queueSize ->
                                        log.info(
                                                "REDIS PUSH thành công, documentId={}, queueSize={}",
                                                savedRecord.getId(),
                                                queueSize
                                        ))
                                .doOnError(error ->
                                        log.error(
                                                "REDIS PUSH thất bại, documentId={}",
                                                savedRecord.getId(),
                                                error
                                        ))
                                .thenReturn(savedRecord)
                )
                .map(savedRecord -> UploadResponse.builder()
                        .documentId(savedRecord.getId())
                        .fileName(savedRecord.getFileName())
                        .status(savedRecord.getStatus())
                        .message("File đã được tiếp nhận và đưa vào hàng đợi xử lý ngầm (Redis Queue)")
                        .build());
    }

    @GetMapping("/{id}/file")
    public Mono<ResponseEntity<Resource>> getDocumentFile(@PathVariable Long id) {
        return documentRepository.findById(id)
                .switchIfEmpty(Mono.error(new RuntimeException("Không tìm thấy chứng từ với ID: " + id)))
                .flatMap(record -> storageService.loadFileAsResource(record.getFilePath()))
                .map(resource -> {
                    String filename = resource.getFilename();
                    MediaType mediaType = MediaTypeFactory.getMediaType(filename)
                            .orElse(MediaType.APPLICATION_OCTET_STREAM);

                    return ResponseEntity.ok()
                            .contentType(mediaType)
                            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                            .body(resource);
                });
    }

    // 1. API Lấy danh sách chứng từ (Có phân trang & Lọc)
    @GetMapping
    public Mono<DocumentPageResponse> getDocuments(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String documentType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        long offset = (long) page * size;

        Mono<List<DocumentRecord>> itemsMono = documentRepository.findByFilter(status, documentType, size, offset)
                .collectList();
        Mono<Long> totalMono = documentRepository.countByFilter(status, documentType);

        return Mono.zip(itemsMono, totalMono)
                .map(tuple -> {
                    List<DocumentRecord> items = tuple.getT1();
                    long total = tuple.getT2();
                    int totalPages = (int) Math.ceil((double) total / size);

                    return DocumentPageResponse.builder()
                            .items(items)
                            .totalElements(total)
                            .page(page)
                            .size(size)
                            .totalPages(totalPages)
                            .build();
                });
    }

    // 2. API Xem chi tiết 1 chứng từ
    @GetMapping("/{id}")
    public Mono<DocumentRecord> getDocumentById(@PathVariable Long id) {
        return documentRepository.findById(id)
                .switchIfEmpty(Mono.error(new RuntimeException("Không tìm thấy chứng từ với ID: " + id)));
    }

    // 3. API Kiểm duyệt & Chỉnh sửa chứng từ (Human-in-the-Loop)
    @PutMapping("/{id}")
    public Mono<DocumentRecord> updateDocument(
            @PathVariable Long id,
            @RequestBody DocumentUpdateRequest request) {
        return documentRepository.findById(id)
                .switchIfEmpty(Mono.error(new RuntimeException("Không tìm thấy chứng từ với ID: " + id)))
                .flatMap(record -> {
                    record.setStatus(request.getStatus());
                    record.setExtractedFields(request.getExtractedFields());
                    record.setReviewedBy(request.getReviewer() != null ? request.getReviewer() : "admin");
                    record.setReviewedAt(LocalDateTime.now().toString());
                    return documentRepository.save(record);
                });
    }

    // 4. API Xuất báo cáo Excel
    @GetMapping("/export")
    public Mono<ResponseEntity<byte[]>> exportApprovedDocuments() {
        return documentRepository.findAll()
                .collectList()
                .flatMap(excelExportService::exportToExcel)
                .map(bytes -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=bao_cao_ke_toan.xlsx")
                        .header(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        .body(bytes));
    }
}