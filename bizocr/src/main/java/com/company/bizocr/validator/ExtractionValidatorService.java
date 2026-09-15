package com.company.bizocr.validator;

import com.company.bizocr.model.dto.schema.ContractFields;
import com.company.bizocr.model.dto.schema.NameCardField;
import com.company.bizocr.model.dto.schema.ServicePaymentFields;
import com.company.bizocr.model.dto.schema.UtilityBillFields;
import com.company.bizocr.model.dto.schema.VatInvoiceFields;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExtractionValidatorService {

    private final Validator validator;
    private final ObjectMapper objectMapper;

    public String validateAndSanitize(String documentType, String jsonOutput) {
        try {
            Object parsedDto = parseToDto(documentType, jsonOutput);

            if (parsedDto == null) {
                return jsonOutput;
            }

            Set<ConstraintViolation<Object>> violations = validator.validate(parsedDto);

            if (!violations.isEmpty()) {
                log.warn(">> [Validator] Dữ liệu vi phạm schema đối với loại chứng từ: {}", documentType);

                ObjectNode jsonNode = (ObjectNode) objectMapper.readTree(jsonOutput);

                for (ConstraintViolation<Object> violation : violations) {
                    String fieldName = violation.getPropertyPath().toString();
                    String errorMsg = violation.getMessage();

                    log.debug(" - Field '{}' vi phạm: {}", fieldName, errorMsg);

                    jsonNode.putNull(fieldName);
                    jsonNode.put("_" + fieldName + "_warning", errorMsg);
                }

                jsonNode.put("review_status", "PENDING_REVIEW");
                return jsonNode.toString();
            }

            ObjectNode cleanNode = (ObjectNode) objectMapper.readTree(jsonOutput);
            cleanNode.put("review_status", "AUTO_APPROVED");
            return cleanNode.toString();

        } catch (Exception e) {
            log.error(">> [Validator] Lỗi không thể map JSON vào Schema. Fallback về PENDING_REVIEW", e);
            return "{\"review_status\":\"PENDING_REVIEW\", \"error\":\"schema_mismatch_or_malformed_json\"}";
        }
    }

    private Object parseToDto(String documentType, String json) throws Exception {
        if (documentType == null) return null;
        return switch (documentType.toUpperCase()) {
            case "VAT_INVOICE", "HOA_DON_VAT" -> objectMapper.readValue(json, VatInvoiceFields.class);
            case "CONTRACT", "HOP_DONG" -> objectMapper.readValue(json, ContractFields.class);
            case "NAMECARD", "NAME_CARD", "DANH_THIEP" -> objectMapper.readValue(json, NameCardField.class);
            case "ELECTRICITY_BILL", "HOA_DON_DIEN", "WATER_BILL", "HOA_DON_NUOC", "UTILITY_BILL" -> objectMapper.readValue(json, UtilityBillFields.class);
            case "SERVICE_PAYMENT_VOUCHER", "RECEIPT", "SERVICE_BILL", "PHIEU_THANH_TOAN" -> objectMapper.readValue(json, ServicePaymentFields.class);
            default -> null;
        };
    }
}