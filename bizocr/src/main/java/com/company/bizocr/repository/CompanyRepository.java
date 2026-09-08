package com.company.bizocr.repository;

import com.company.bizocr.model.entity.Company;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Mono;

public interface CompanyRepository extends R2dbcRepository<Company, Long> {
    Mono<Company> findByTaxCode(String taxCode);
    Mono<Company> findByCompanyCode(String companyCode);
    Mono<Company> findByNormalizedName(String normalizedName);
}