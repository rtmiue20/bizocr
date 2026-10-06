package com.company.bizocr.repository;

import com.company.bizocr.model.entity.DocumentImage;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface DocumentImageRepository extends R2dbcRepository<DocumentImage, Long> {

    /** Lấy ảnh theo documentId + side (FRONT / BACK). */
    Mono<DocumentImage> findByDocumentIdAndSide(Long documentId, String side);

    /** Lấy tất cả ảnh của một document (tối đa 2: FRONT + BACK). */
    Flux<DocumentImage> findByDocumentId(Long documentId);

    /** Kiểm tra sha256 để tránh upload ảnh trùng. */
    Mono<DocumentImage> findBySha256(String sha256);
}
