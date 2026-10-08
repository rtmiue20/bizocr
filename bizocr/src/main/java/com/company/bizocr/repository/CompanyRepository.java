package com.company.bizocr.repository;

import com.company.bizocr.model.entity.Company;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CompanyRepository extends R2dbcRepository<Company, Long> {
    Mono<Company> findByTaxCode(String taxCode);
    Mono<Company> findByCompanyCode(String companyCode);

    /**
     * Trả tất cả dòng trùng normalized_name (chịu được DB có nhiều dòng cùng tên).
     * Dùng thay cho findByNormalizedName ở mọi luồng tìm-theo-tên.
     */
    Flux<Company> findAllByNormalizedName(String normalizedName);

    /**
     * @deprecated Dùng findAllByNormalizedName(...).next() thay thế.
     *             Giữ lại để không vỡ compile, nhưng không gọi nữa.
     */
    @Deprecated
    Mono<Company> findByNormalizedName(String normalizedName);
}