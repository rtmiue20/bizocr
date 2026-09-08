package com.company.bizocr.controller;

import com.company.bizocr.model.dto.request.DocumentApproveRequest;
import com.company.bizocr.model.dto.response.DocumentApproveResponse;
import com.company.bizocr.service.DocumentApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentApprovalController {

    private final DocumentApprovalService documentApprovalService;

    @PostMapping("/{id}/approve")
    public Mono<ResponseEntity<DocumentApproveResponse>> approveDocument(
            @PathVariable("id") Long documentId,
            @RequestBody DocumentApproveRequest request
    ) {
        return documentApprovalService.approveDocument(documentId, request)
                .map(ResponseEntity::ok);
    }
}