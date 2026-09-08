package com.company.bizocr.controller;

import com.company.bizocr.classify.ClassifierService;
import com.company.bizocr.model.dto.ClassifyResult;
import com.company.bizocr.model.dto.request.DocumentApproveRequest;
import com.company.bizocr.model.dto.response.DocumentApproveResponse;
import com.company.bizocr.service.FieldAuditService;
import com.company.bizocr.model.dto.request.DocumentUpdateRequest;
import com.company.bizocr.model.dto.response.DocumentPageResponse;
import com.company.bizocr.model.dto.response.UploadResponse;
import com.company.bizocr.model.entity.Document;
import com.company.bizocr.repository.DocumentRepository;
import com.company.bizocr.service.DocumentReviewService;
import com.company.bizocr.service.ExcelExportService;
import com.company.bizocr.storage.StorageService;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
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
    private final FieldAuditService fieldAuditService;
    private final ObjectMapper objectMapper;

    // --- Helper function: Trích xuất companyId từ JWT Token hoặc Security Context ---
    private Mono<Long> getCurrentCompanyId(Mono<java.security.Principal> principalMono) {
        return principalMono
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Không tìm thấy thông tin xác thực"
                )))
                .flatMap(principal -> {
                    if (!(principal instanceof JwtAuthenticationToken jwtAuth)) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED, "Định dạng xác thực không hợp lệ"
                        ));
                    }

                    Jwt token = jwtAuth.getToken();
                    Object raw = token.getClaim("companyId");

                    Long companyId = parseClaimAsLong(raw);
                    if (companyId == null) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.FORBIDDEN, "Token thiếu hoặc sai định dạng claim companyId"
                        ));
                    }

                    return Mono.just(companyId);
                });
    }

    /**
     * Ép kiểu linh hoạt claim companyId về Long, bất kể IdP trả về dạng nào
     * (JWT numeric claim có thể decode thành Long, Integer, Double, hoặc String tùy thư viện).
     */
    private Long parseClaimAsLong(Object claim) {
        if (claim == null) {
            return null;
        }
        try {
            if (claim instanceof Long l) {
                return l;
            }
            if (claim instanceof Integer i) {
                return i.longValue();
            }
            if (claim instanceof Double d) {
                return d.longValue();
            }
            if (claim instanceof Number n) {
                return n.longValue();
            }
            if (claim instanceof String s && !s.isBlank()) {
                return Long.valueOf(s.trim());
            }
        } catch (NumberFormatException e) {
            log.warn("[Auth] Claim companyId không parse được về Long: {}", claim);
        }
        return null;
    }

    // 1. C - Create
    // API Upload tập trung (Gắn thêm companyId khi tạo mới)
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<UploadResponse> uploadDocument(
            @RequestPart("file") FilePart filePart,
            Mono<java.security.Principal> principalMono) {

        return getCurrentCompanyId(principalMono)
                .flatMap(companyId -> storageService.saveFile(filePart)
                        .flatMap(savedFilePath -> {
                            Document record = Document.builder()
                                    .fileName(filePart.filename())
                                    .filePath(savedFilePath)
                                    .status("PENDING")
                                    .companyId(companyId) // Gán companyId ngay từ lúc tạo file
                                    .receivedAt(LocalDateTime.now().toString())
                                    .build();

                            return documentRepository.save(record);
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

    // API Phân loại nhanh văn bản OCR
    @PostMapping("/classify")
    public Mono<ClassifyResult> classifyDocument(@RequestBody Map<String, String> request) {
        String rawOcrText = request.get("rawOcrText");
        return classifierService.classifyDocument(rawOcrText);
    }

//    // API Phê duyệt chứng từ (Kiểm tra quyền sở hữu công ty trước khi duyệt)
//    @PostMapping("/{id}/approve")
//    public Mono<ResponseEntity<Void>> approveDocument(
//            @PathVariable Long id,
//            @RequestParam String approvedType,
//            Mono<java.security.Principal> principalMono) {
//
//        return getCurrentCompanyId(principalMono)
//                // Chặn đứng IDOR: Phải là document của đúng công ty mới cho phép duyệt
//                .flatMap(companyId -> documentRepository.findByIdAndCompanyId(id, companyId))
//                .switchIfEmpty(Mono.error(new ResponseStatusException(
//                        HttpStatus.NOT_FOUND, "Không tìm thấy chứng từ hợp lệ để duyệt"
//                )))
//                .flatMap(doc -> principalMono.map(java.security.Principal::getName))
//                .switchIfEmpty(Mono.error(new ResponseStatusException(
//                        HttpStatus.UNAUTHORIZED, "Truy cập bị từ chối: Không tìm thấy thông tin xác thực"
//                )))
//                .flatMap(currentUser -> documentReviewService.approveDocument(id, approvedType, currentUser))
//                .thenReturn(ResponseEntity.ok().build());
//    }

    // 2. R - Read
    // API Lấy file vật lý để hiển thị UI (Đã bọc companyId)
    @GetMapping("/{id}/file")
    public Mono<ResponseEntity<Resource>> getDocumentFile(
            @PathVariable Long id,
            Mono<java.security.Principal> principalMono) {

        return getCurrentCompanyId(principalMono)
                .flatMap(companyId -> documentRepository.findByIdAndCompanyId(id, companyId))
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy chứng từ hoặc không có quyền truy cập"
                )))
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

    // API Lấy danh sách chứng từ (Đã vá lỗi Multi-tenant)
    @GetMapping
    public Mono<DocumentPageResponse> getDocuments(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String documentType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Mono<java.security.Principal> principalMono) {

        long offset = (long) page * size;

        return getCurrentCompanyId(principalMono).flatMap(companyId -> {
            Mono<List<Document>> itemsMono = documentRepository
                    .findByFilterAndCompanyId(status, documentType, companyId, size, offset)
                    .collectList();
            Mono<Long> totalMono = documentRepository
                    .countByFilterAndCompanyId(status, documentType, companyId);

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

    // API Xem chi tiết 1 chứng từ (Đã vá lỗi Multi-tenant IDOR)
    @GetMapping("/{id}")
    public Mono<Document> getDocumentById(
            @PathVariable Long id,
            Mono<java.security.Principal> principalMono) {

        return getCurrentCompanyId(principalMono)
                .flatMap(companyId -> documentRepository.findByIdAndCompanyId(id, companyId))
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy chứng từ hoặc bạn không có quyền truy cập dữ liệu của công ty khác"
                )));
    }
    // API Xuất báo cáo Excel (Đã cách ly dữ liệu export theo tenant)
    @GetMapping("/export")
    public Mono<ResponseEntity<byte[]>> exportApprovedDocuments(Mono<java.security.Principal> principalMono) {
        return getCurrentCompanyId(principalMono)
                .flatMap(companyId -> documentRepository.findByStatusAndCompanyId("APPROVED", companyId)
                        .collectList()
                        .flatMap(excelExportService::exportToExcel)
                )
                .map(bytes -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=bao_cao_ke_toan.xlsx")
                        .header(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        .body(bytes));
    }

    // 3. U - Update, P - Put
    // API Cập nhật thông tin chứng từ tổng quát (Đã tích hợp Validate JSON & Audit Diff)
    @PutMapping("/{id}")
    public Mono<Document> updateDocument(
            @PathVariable Long id,
            @RequestBody DocumentUpdateRequest request,
            Mono<java.security.Principal> principalMono) {

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

        // 2. TÍNH NĂNG MỚI: Validate định dạng JSON từ Client
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
        return getCurrentCompanyId(principalMono)
                .flatMap(companyId -> documentRepository.findByIdAndCompanyId(id, companyId))
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy chứng từ hoặc không có quyền cập nhật"
                )))
                .flatMap(doc -> principalMono
                        .map(java.security.Principal::getName)
                        .defaultIfEmpty("admin")
                        .flatMap(reviewer -> {
                            // Ghi Log Audit nếu có truyền extractedFields mới
                            Mono<Void> auditMono = (request.getExtractedFields() != null)
                                    ? fieldAuditService.logFieldCorrections(doc, request.getExtractedFields(), reviewer)
                                    : Mono.empty();

                            return auditMono.then(Mono.defer(() -> {
                                if (request.getStatus() != null) {
                                    doc.setStatus(request.getStatus());
                                }
                                if (request.getExtractedFields() != null) {
                                    doc.setExtractedFields(request.getExtractedFields());
                                }
                                doc.setReviewedBy(reviewer);
                                doc.setReviewedAt(LocalDateTime.now().toString());

                                return documentRepository.save(doc);
                            }));
                        })
                );
    }
}