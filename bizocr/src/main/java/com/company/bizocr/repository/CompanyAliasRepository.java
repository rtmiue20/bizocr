package com.company.bizocr.repository;

import com.company.bizocr.model.entity.CompanyAlias;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface CompanyAliasRepository
        extends ReactiveCrudRepository<CompanyAlias, Long> {

    Mono<CompanyAlias> findByNormalizedAlias(
            String normalizedAlias
    );
}