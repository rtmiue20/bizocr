package com.company.bizocr.controller;

import com.company.bizocr.classify.ClassifierService;
import com.company.bizocr.model.dto.ClassifyResult;
import com.company.bizocr.model.dto.request.DocumentUpdateRequest;
import com.company.bizocr.model.dto.response.DocumentPageResponse;
import com.company.bizocr.model.dto.response.UploadResponse;
import com.company.bizocr.model.entity.Document;
import com.company.bizocr.repository.DocumentRepository;
import com.company.bizocr.service.DocumentReviewService;
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
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentRepository documentRepository;
    private final StorageService storageService;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ExcelExportService excelExportService;
    private final ClassifierService classifierService;
    private final DocumentReviewService documentReviewService;

    // 1. API Upload tập trung (Lưu đĩa -> Tạo DB -> Đẩy Redis Queue)
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<UploadResponse> uploadDocument(@RequestPart("file") FilePart filePart) {
        return storageService.saveFile(filePart)
                .flatMap(savedFilePath -> {
                    Document record = Document.builder()
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

    // 2. API Phân loại nhanh văn bản OCR (Fix lỗi 405 Method Not Allowed trên Postman)
    @PostMapping("/classify")
    public Mono<ClassifyResult> classifyDocument(@RequestBody Map<String, String> request) {
        String rawOcrText = request.get("rawOcrText");
        return classifierService.classifyDocument(rawOcrText);
    }

    // 3. API Phê duyệt chứng từ & Đồng bộ dữ liệu sang Python ML (Fix lỗi 404 Not Found trên Postman)
    @PostMapping("/{id}/approve")
    public Mono<Void> approveDocument(
            @PathVariable Long id,
            @RequestParam(required = false) String approvedType,
            @RequestBody(required = false) Map<String, String> body) {

        String type = (approvedType != null && !approvedType.isBlank())
                ? approvedType
                : (body != null ? body.get("approvedType") : "UNKNOWN");

        return documentReviewService.approveDocument(id, type);
    }

    // 4. API Lấy file vật lý để hiển thị UI
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

    // 5. API Lấy danh sách chứng từ (Phân trang & Lọc)
    @GetMapping
    public Mono<DocumentPageResponse> getDocuments(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String documentType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        long offset = (long) page * size;

        Mono<List<Document>> itemsMono = documentRepository.findByFilter(status, documentType, size, offset)
                .collectList();
        Mono<Long> totalMono = documentRepository.countByFilter(status, documentType);

        return Mono.zip(itemsMono, totalMono)
                .map(tuple -> {
                    List<Document> items = tuple.getT1();
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

    // 6. API Xem chi tiết 1 chứng từ
    @GetMapping("/{id}")
    public Mono<Document> getDocumentById(@PathVariable Long id) {
        return documentRepository.findById(id)
                .switchIfEmpty(Mono.error(new RuntimeException("Không tìm thấy chứng từ với ID: " + id)));
    }

    // 7. API Cập nhật thông tin chứng từ tổng quát
    @PutMapping("/{id}")
    public Mono<Document> updateDocument(
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

    // 8. API Xuất báo cáo Excel
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