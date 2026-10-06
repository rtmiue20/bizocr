package com.company.bizocr.controller;

import com.company.bizocr.classify.ClassifierService;
import com.company.bizocr.company.CompanyResolverService;
import com.company.bizocr.model.dto.ClassifyResult;
import com.company.bizocr.model.dto.CompanySuggestion;
import com.company.bizocr.model.entity.DocumentStatus; // Thêm import DocumentStatus
import com.company.bizocr.service.FieldAuditService;
import com.company.bizocr.model.dto.request.DocumentUpdateRequest;
import com.company.bizocr.model.dto.response.DocumentPageResponse;
import com.company.bizocr.model.dto.response.UploadResponse;
import com.company.bizocr.model.entity.Document;
import com.company.bizocr.repository.DocumentRepository;
import com.company.bizocr.service.ExcelExportService;
import com.company.bizocr.storage.StorageService;
import com.company.bizocr.security.CurrentUserResolver; // Nhớ check lại package nếu khác
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import com.company.bizocr.model.entity.DocumentImage;
import com.company.bizocr.repository.DocumentImageRepository;
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
    private final DocumentImageRepository documentImageRepository;
    private final StorageService storageService;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ExcelExportService excelExportService;
    private final ClassifierService classifierService;
    private final FieldAuditService fieldAuditService;
    private final ObjectMapper objectMapper;
    private final CompanyResolverService companyResolverService;
    private final CurrentUserResolver currentUserResolver; // Inject Resolver chuẩn

    // 1. C - Create
    // API Upload tập trung (Gắn thêm companyId khi tạo mới)
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<UploadResponse> uploadDocument(@RequestPart("file") FilePart filePart) {

        return currentUserResolver.getCurrentUser()
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Không tìm thấy thông tin xác thực")))
                .flatMap(currentUser -> storageService.saveFile(filePart)
                        .flatMap(savedFilePath -> {
                            Document record = Document.builder()
                                    .fileName(filePart.filename())
                                    .filePath(savedFilePath)
                                    .status("PENDING")
                                    .ownerCompanyId(currentUser.companyId())
                                    .receivedAt(LocalDateTime.now().toString())
                                    .build();

                            return documentRepository.save(record)
                                    .flatMap(savedRecord -> {
                                        DocumentImage frontImg = DocumentImage.builder()
                                                .documentId(savedRecord.getId())
                                                .side("FRONT")
                                                .storageKey(savedFilePath)
                                                .ocrStatus("PENDING")
                                                .uploadedAt(LocalDateTime.now())
                                                .build();
                                        return documentImageRepository.save(frontImg)
                                                .thenReturn(savedRecord)
                                                .onErrorResume(e -> {
                                                    log.warn("[Upload] Không thể tạo document_images FRONT: {}", e.getMessage());
                                                    return Mono.just(savedRecord);
                                                });
                                    });
                        })
                )
                .flatMap(savedRecord ->
                        redisTemplate.opsForList()
                                .leftPush("ocr_task_queue", savedRecord.getId().toString())
                                .doOnNext(queueSize -> log.info("REDIS PUSH thành công, documentId={}, queueSize={}", savedRecord.getId(), queueSize))
                                .doOnError(error -> log.error("REDIS PUSH thất bại, documentId={}", savedRecord.getId(), error))
                                .thenReturn(savedRecord)
                )
                .map(savedRecord -> UploadResponse.builder()
                        .documentId(savedRecord.getId())
                        .fileName(savedRecord.getFileName())
                        .status(savedRecord.getStatus())
                        .message("File đã được tiếp nhận và đưa vào hàng đợi xử lý ngầm (Redis Queue)")
                        .build());
    }

    // API Upload ảnh bổ sung (VD: mặt sau namecard)
    @PostMapping(value = "/{id}/images/{side}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<DocumentImage> uploadDocumentImage(
            @PathVariable Long id,
            @PathVariable String side,
            @RequestPart("file") FilePart filePart) {
        String cleanSide = side.toUpperCase().trim();
        return currentUserResolver.getCurrentUser()
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Không tìm thấy thông tin xác thực")))
                .flatMap(currentUser -> documentRepository.findByIdAndOwnerCompanyId(id, currentUser.companyId())
                        .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy chứng từ")))
                        .flatMap(doc -> storageService.saveFile(filePart)
                                .flatMap(savedKey -> {
                                    DocumentImage img = DocumentImage.builder()
                                            .documentId(doc.getId())
                                            .side(cleanSide)
                                            .storageKey(savedKey)
                                            .ocrStatus("PENDING")
                                            .uploadedAt(LocalDateTime.now())
                                            .build();
                                    return documentImageRepository.findByDocumentIdAndSide(doc.getId(), cleanSide)
                                            .flatMap(existing -> {
                                                existing.setStorageKey(savedKey);
                                                existing.setUploadedAt(LocalDateTime.now());
                                                return documentImageRepository.save(existing);
                                            })
                                            .switchIfEmpty(Mono.defer(() -> documentImageRepository.save(img)));
                                })
                        )
                );
    }

    @GetMapping("/{id}/images")
    public Mono<List<DocumentImage>> getDocumentImages(@PathVariable Long id) {
        return currentUserResolver.getCurrentUser()
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Không tìm thấy thông tin xác thực")))
                .flatMap(currentUser -> documentRepository.findByIdAndOwnerCompanyId(id, currentUser.companyId())
                        .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy chứng từ")))
                        .flatMap(doc -> documentImageRepository.findByDocumentId(doc.getId()).collectList())
                );
    }

    // API Phân loại nhanh văn bản OCR
    @PostMapping("/classify")
    public Mono<ClassifyResult> classifyDocument(@RequestBody Map<String, String> request) {
        String rawOcrText = request.get("rawOcrText");
        return classifierService.classifyDocument(rawOcrText);
    }

    // 2. R - Read
    // API Lấy file vật lý để hiển thị UI
    @GetMapping("/{id}/file")
    public Mono<ResponseEntity<Resource>> getDocumentFile(@PathVariable Long id) {
        return currentUserResolver.getCurrentUser()
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Không tìm thấy thông tin xác thực")))
                .flatMap(currentUser -> documentRepository.findByIdAndOwnerCompanyId(id, currentUser.companyId()))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy chứng từ hoặc không có quyền truy cập")))
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

    // API Lấy danh sách chứng từ
    @GetMapping
    public Mono<DocumentPageResponse> getDocuments(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String documentType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        long offset = (long) page * size;

        return currentUserResolver.getCurrentUser()
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Không tìm thấy thông tin xác thực")))
                .flatMap(currentUser -> {
                    Mono<List<Document>> itemsMono = documentRepository
                            .findByFilterAndOwnerCompanyId(status, documentType, currentUser.companyId(), size, offset)
                            .collectList();
                    Mono<Long> totalMono = documentRepository
                            .countByFilterAndOwnerCompanyId(status, documentType, currentUser.companyId());

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
                });
    }

    // API Xem chi tiết 1 chứng từ
    @GetMapping("/{id}")
    public Mono<Document> getDocumentById(@PathVariable Long id) {
        return currentUserResolver.getCurrentUser()
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Không tìm thấy thông tin xác thực")))
                .flatMap(currentUser -> documentRepository.findByIdAndOwnerCompanyId(id, currentUser.companyId()))
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy chứng từ hoặc bạn không có quyền truy cập dữ liệu của công ty khác"
                )));
    }

    // API Xuất báo cáo Excel
    @GetMapping("/export")
    public Mono<ResponseEntity<byte[]>> exportApprovedDocuments() {
        return currentUserResolver.getCurrentUser()
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Không tìm thấy thông tin xác thực")))
                .flatMap(currentUser -> documentRepository.findByStatusAndOwnerCompanyId("APPROVED", currentUser.companyId())
                        .collectList()
                        .flatMap(excelExportService::exportToExcel)
                )
                .map(bytes -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=bao_cao_ke_toan.xlsx")
                        .header(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        .body(bytes));
    }

    @GetMapping("/{id}/company-suggestion")
    public Mono<CompanySuggestion> getCompanySuggestion(@PathVariable Long id) {
        return currentUserResolver.getCurrentUser()
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Không tìm thấy thông tin xác thực")))
                .flatMap(currentUser -> documentRepository.findByIdAndOwnerCompanyId(id, currentUser.companyId()))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy chứng từ hoặc không có quyền truy cập")))
                .map(doc -> companyResolverService.suggestFields(doc.getRawOcrText()));
    }

    // 3. U - Update, P - Put
    // API Cập nhật thông tin chứng từ tổng quát
    @PutMapping("/{id}")
    public Mono<Document> updateDocument(
            @PathVariable Long id,
            @RequestBody DocumentUpdateRequest request) {

        // 1. Validate Status Whitelist
        if (request.getStatus() != null) {
            List<String> allowedManualStatuses = List.of("PENDING", "REJECTED", "NEED_REVIEW");

            if (request.getStatus().equals("APPROVED")) {
                return Mono.error(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Không được phép set trạng thái APPROVED qua API cập nhật chung. Vui lòng gọi API POST /approve."
                ));
            }

            if (!allowedManualStatuses.contains(request.getStatus())) {
                return Mono.error(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Trạng thái không hợp lệ: " + allowedManualStatuses
                ));
            }
        }

        // 2. Validate định dạng JSON từ Client
        if (request.getExtractedFields() != null && !request.getExtractedFields().isBlank()) {
            try {
                objectMapper.readTree(request.getExtractedFields());
            } catch (Exception e) {
                return Mono.error(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Dữ liệu extractedFields không phải là định dạng JSON hợp lệ."
                ));
            }
        }

        // 3. Cập nhật và ghi Audit Vết sửa đổi
        return currentUserResolver.getCurrentUser()
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Không tìm thấy thông tin xác thực")))
                .flatMap(currentUser -> documentRepository.findByIdAndOwnerCompanyId(id, currentUser.companyId())
                        .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy chứng từ hoặc không có quyền cập nhật")))
                        .flatMap(doc -> {

                            // CHẶN SỬA KHI ĐÃ APPROVED
                            if (DocumentStatus.APPROVED.name().equals(doc.getStatus())) {
                                return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                                        "Tài liệu đã APPROVED, không thể sửa"));
                            }

                            // Gọi FieldAuditService mà không cần truyền currentUser (vì Service đã tự handle)
                            Mono<Void> auditMono = (request.getExtractedFields() != null)
                                    ? fieldAuditService.logFieldCorrections(doc, request.getExtractedFields())
                                    : Mono.empty();

                            return auditMono.then(Mono.defer(() -> {
                                if (request.getStatus() != null) {
                                    doc.setStatus(request.getStatus());
                                }
                                if (request.getExtractedFields() != null) {
                                    doc.setExtractedFields(request.getExtractedFields());
                                }
                                doc.setReviewedBy(currentUser.username());
                                doc.setReviewedAt(LocalDateTime.now().toString());

                                return documentRepository.save(doc);
                            }));
                        })
                );
    }
}