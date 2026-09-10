package com.company.bizocr.controller;

import com.company.bizocr.model.dto.request.DocumentApproveRequest;
import com.company.bizocr.model.dto.response.DocumentApproveResponse;
import com.company.bizocr.repository.DocumentRepository;
import com.company.bizocr.service.DocumentApprovalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.security.Principal;

@Slf4j
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentApprovalController {

    private final DocumentApprovalService documentApprovalService;
    private final DocumentRepository documentRepository;

    @PostMapping("/{id}/approve")
    public Mono<ResponseEntity<DocumentApproveResponse>> approveDocument(
            @PathVariable("id") Long documentId,
            @RequestBody DocumentApproveRequest request,
            Mono<Principal> principalMono
    ) {
        return getCurrentCompanyId(principalMono)
                .flatMap(companyId -> documentRepository.findByIdAndCompanyId(documentId, companyId))
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy chứng từ hợp lệ để duyệt"
                )))
                .flatMap(doc -> documentApprovalService.approveDocument(documentId, request))
                .map(ResponseEntity::ok);
    }

    private Mono<Long> getCurrentCompanyId(Mono<Principal> principalMono) {
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
                    Long companyId = parseClaimAsLong(token.getClaim("companyId"));
                    if (companyId == null) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.FORBIDDEN, "Token thiếu hoặc sai định dạng claim companyId"
                        ));
                    }
                    return Mono.just(companyId);
                });
    }

    private Long parseClaimAsLong(Object claim) {
        if (claim == null) return null;
        try {
            if (claim instanceof Number n) return n.longValue();
            if (claim instanceof String s && !s.isBlank()) return Long.valueOf(s.trim());
        } catch (NumberFormatException e) {
            log.warn("[Auth] Claim companyId không parse được về Long: {}", claim);
        }
        return null;
    }
}