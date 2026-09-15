package com.company.bizocr.service;

import com.company.bizocr.model.entity.Document;
import com.company.bizocr.model.entity.FieldCorrectionAudit;
import com.company.bizocr.repository.FieldCorrectionAuditRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class FieldAuditService {

    private final FieldCorrectionAuditRepository auditRepository;
    private final ObjectMapper objectMapper;

    /**
     * So sánh JSON cũ và JSON mới, tự động ghi vết sự khác biệt vào DB
     */
    public Mono<Void> logFieldCorrections(Document doc, String newExtractedFieldsJson, String reviewer) {
        if (newExtractedFieldsJson == null || newExtractedFieldsJson.isBlank()) {
            return Mono.empty();
        }

        return Mono.fromCallable(() -> calculateDiff(doc, newExtractedFieldsJson, reviewer))
                .flatMapMany(auditRepository::saveAll)
                .doOnNext(audit -> log.info("[Audit] Lưu vết sửa đổi docId={}: field '{}' ('{}' -> '{}')",
                        audit.getDocumentId(), audit.getFieldName(), audit.getOriginalValue(), audit.getCorrectedValue()))
                .then();
    }

    private static final Set<String> METADATA_KEYS = Set.of("field_confidences", "anomaly_analysis");

    private List<FieldCorrectionAudit> calculateDiff(Document doc, String newJsonStr, String reviewer) throws Exception {
        String oldJsonStr = (doc.getExtractedFields() != null && !doc.getExtractedFields().isBlank())
                ? doc.getExtractedFields()
                : "{}";

        JsonNode oldNode = objectMapper.readTree(oldJsonStr);
        JsonNode newNode = objectMapper.readTree(newJsonStr);

        Set<String> allKeys = new HashSet<>();
        oldNode.fieldNames().forEachRemaining(allKeys::add);
        newNode.fieldNames().forEachRemaining(allKeys::add);
        allKeys.removeAll(METADATA_KEYS); // Loại field hệ thống tự sinh, chỉ audit field nghiệp vụ do người dùng sửa

        List<FieldCorrectionAudit> auditList = new ArrayList<>();
        String now = LocalDateTime.now().toString();

        for (String key : allKeys) {
            String oldVal = oldNode.has(key) ? oldNode.get(key).asText() : null;
            String newVal = newNode.has(key) ? newNode.get(key).asText() : null;

            if (!Objects.equals(oldVal, newVal)) {
                auditList.add(FieldCorrectionAudit.builder()
                        .documentId(doc.getId())
                        .companyId(doc.getOwnerCompanyId())   // ← đổi từ doc.getCompanyId()
                        .fieldName(key)
                        .originalValue(oldVal)
                        .correctedValue(newVal)
                        .reviewedBy(reviewer)
                        .reviewedAt(now)
                        .build());
            }
        }
        return auditList;
    }
}