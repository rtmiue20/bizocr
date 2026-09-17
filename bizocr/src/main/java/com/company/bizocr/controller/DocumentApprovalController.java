package com.company.bizocr.controller;

import com.company.bizocr.model.dto.request.DocumentApproveRequest;
import com.company.bizocr.model.dto.response.DocumentApproveResponse;
import com.company.bizocr.repository.DocumentRepository;
import com.company.bizocr.service.DocumentApprovalService;
import com.company.bizocr.security.CurrentUserResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentApprovalController {

    private final DocumentApprovalService documentApprovalService;
    private final DocumentRepository documentRepository;
    private final CurrentUserResolver currentUserResolver;

    @PostMapping("/{id}/approve")
    public Mono<ResponseEntity<DocumentApproveResponse>> approveDocument(
            @PathVariable("id") Long documentId,
            @RequestBody DocumentApproveRequest request
    ) {
        return currentUserResolver.getCurrentUser()
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Truy cập bị từ chối: Không tìm thấy thông tin xác thực"
                )))
                // FIX: Chặn đứng lỗi IDOR nếu user hợp lệ nhưng không được gán companyId
                .flatMap(currentUser -> currentUser.companyId() != null
                                ? Mono.just(currentUser.companyId())
                                : Mono.error(new ResponseStatusException(
                                HttpStatus.FORBIDDEN, "Token thiếu claim companyId, không thể xác định quyền truy cập dữ liệu."
                        ))
                )
                .flatMap(companyId -> documentRepository.findByIdAndOwnerCompanyId(documentId, companyId))
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy chứng từ hợp lệ để duyệt hoặc bạn không có quyền."
                )))
                .flatMap(doc -> documentApprovalService.approveDocument(documentId, request))
                .map(ResponseEntity::ok);
    }
}