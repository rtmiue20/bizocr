package com.company.bizocr.repository;

import com.company.bizocr.model.entity.Industry;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.List;

public interface IndustryRepository extends ReactiveCrudRepository<Industry, String> {
    Flux<Industry> findByCodeInAndIsActiveTrue(List<String> codes);
}