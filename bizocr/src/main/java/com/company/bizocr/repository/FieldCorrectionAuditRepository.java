package com.company.bizocr.repository;

import com.company.bizocr.model.entity.FieldCorrectionAudit;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface FieldCorrectionAuditRepository extends ReactiveCrudRepository<FieldCorrectionAudit, Long> {
}