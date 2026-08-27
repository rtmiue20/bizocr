package com.company.bizocr.repository;

import com.company.bizocr.model.entity.Company;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface CompanyRepository
        extends ReactiveCrudRepository<Company, Long> {
    Mono<Company> findByTaxCode(String taxCode);
    Mono<Company> findByCompanyCode(String companyCode);
    Mono<Company> findByNormalizedName(String normalizedName);
}