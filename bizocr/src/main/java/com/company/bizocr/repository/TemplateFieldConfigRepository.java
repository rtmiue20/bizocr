package com.company.bizocr.repository;

import com.company.bizocr.model.entity.TemplateFieldConfig;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;

public interface TemplateFieldConfigRepository extends R2dbcRepository<TemplateFieldConfig, Long> {

    @Query("SELECT * FROM template_field_config WHERE doc_type_code = :docTypeCode ORDER BY display_order ASC")
    Flux<TemplateFieldConfig> findByDocTypeCodeOrderByDisplayOrder(String docTypeCode);
}