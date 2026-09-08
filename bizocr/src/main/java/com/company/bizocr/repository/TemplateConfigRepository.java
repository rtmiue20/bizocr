package com.company.bizocr.repository;

import com.company.bizocr.model.entity.TemplateConfig;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;

public interface TemplateConfigRepository extends R2dbcRepository<TemplateConfig, Long> {

    Flux<TemplateConfig> findByStatus(String status);
}