package com.company.bizocr.validator;

import com.company.bizocr.model.dto.schema.ContractFields;
import com.company.bizocr.model.dto.schema.NameCardField;
import com.company.bizocr.model.dto.schema.ServicePaymentFields;
import com.company.bizocr.model.dto.schema.UtilityBillFields;
import com.company.bizocr.model.dto.schema.VatInvoiceFields;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ExtractionValidatorService hỗ trợ 2 chế độ:
 * - SHADOW MODE (mặc định true): Chạy toàn bộ các rule (Schema, MST Check-digit, Date, Amount, Grounding),
 *   ghi log cảnh báo và lưu telemetry/thống kê tỷ lệ vi phạm mà KHÔNG chặn hay cưỡng chế chuyển sang NEEDS_REVIEW.
 * - ACTIVE/BLOCKING MODE: Bật chặn thật sự, nếu vi phạm sẽ đánh dấu NEEDS_REVIEW ngay lập tức.
 */
@Slf4j
@Service
public class ExtractionValidatorService {

    private final Validator validator;
    private final ObjectMapper objectMapper;
    private final BusinessRuleValidator businessRuleValidator;

    public ExtractionValidatorService(Validator validator, ObjectMapper objectMapper) {
        this(validator, objectMapper, new BusinessRuleValidator());
    }

    @Autowired
    public ExtractionValidatorService(Validator validator, ObjectMapper objectMapper, BusinessRuleValidator businessRuleValidator) {
        this.validator = validator;
        this.objectMapper = objectMapper;
        this.businessRuleValidator = businessRuleValidator;
        this.shadowMode = true;
    }

    @Value("${bizocr.validator.shadow-mode:true}")
    private boolean shadowMode = true;

    // Telemetry đếm số lượng thống kê trong Shadow Mode
    private final AtomicLong totalValidations = new AtomicLong(0);
    private final AtomicLong schemaViolationsCount = new AtomicLong(0);
    private final AtomicLong businessRuleViolationsCount = new AtomicLong(0);
    private final AtomicLong groundingViolationsCount = new AtomicLong(0);

    public boolean isShadowMode() {
        return shadowMode;
    }

    public void setShadowMode(boolean shadowMode) {
        this.shadowMode = shadowMode;
    }

    /**
     * Phương thức cũ tương thích ngược.
     */
    public String validateAndSanitize(String documentType, String jsonOutput) {
        return validateAndSanitize(documentType, jsonOutput, null);
    }

    /**
     * Phương thức mới tích hợp kiểm tra sâu và Shadow Mode telemetry.
     */
    public String validateAndSanitize(String documentType, String jsonOutput, String rawOcrText) {
        totalValidations.incrementAndGet();
        ValidationReport report = new ValidationReport();
        report.setValid(true);

        try {
            Object parsedDto = parseToDto(documentType, jsonOutput);
            JsonNode rootNode = objectMapper.readTree(jsonOutput);
            ObjectNode outputNode = rootNode.isObject() ? (ObjectNode) rootNode : objectMapper.createObjectNode();

            // 1. Kiểm tra Jakarta Validation Schema
            if (parsedDto != null) {
                Set<ConstraintViolation<Object>> violations = validator.validate(parsedDto);
                if (!violations.isEmpty()) {
                    schemaViolationsCount.incrementAndGet();
                    for (ConstraintViolation<Object> v : violations) {
                        String msg = String.format("Schema vi phạm ở field '%s': %s", v.getPropertyPath(), v.getMessage());
                        report.addError(msg);
                        outputNode.put("_warn_" + v.getPropertyPath(), v.getMessage());
                    }
                }
            }

            // 2. Kiểm tra Business Rules chi tiết (MST, Date, Amount, Grounding)
            checkBusinessRules(documentType, outputNode, rawOcrText, report);

            // 3. Xử lý kết quả dựa trên Shadow Mode
            if (!report.isValid()) {
                if (shadowMode) {
                    log.warn("[VALIDATOR - SHADOW MODE] Phát hiện {} vi phạm cho [{}]: {} (Không chặn luồng, chỉ ghi nhận)",
                            report.getErrors().size(), documentType, report.getErrors());
                    outputNode.put("validator_shadow_mode", true);
                    outputNode.put("validator_has_violations", true);
                    outputNode.set("validator_errors", objectMapper.valueToTree(report.getErrors()));
                    // Shadow mode: Không đổi review_status để không làm gián đoạn luồng hiện tại
                    if (!outputNode.has("review_status")) {
                        outputNode.put("review_status", "AUTO_APPROVED");
                    }
                } else {
                    log.warn("[VALIDATOR - BLOCKING] Chặn và yêu cầu review cho [{}]: {}",
                            documentType, report.getErrors());
                    outputNode.put("review_status", "PENDING_REVIEW");
                    outputNode.put("validator_shadow_mode", false);
                    outputNode.set("validator_errors", objectMapper.valueToTree(report.getErrors()));
                }
            } else {
                if (!outputNode.has("review_status")) {
                    outputNode.put("review_status", "AUTO_APPROVED");
                }
            }

            // Gắn grounding status
            if (!report.getUngroundedFields().isEmpty()) {
                outputNode.set("ungrounded_fields", objectMapper.valueToTree(report.getUngroundedFields()));
            }

            return outputNode.toString();

        } catch (Exception e) {
            log.error(">> [Validator] Lỗi phân tích JSON khi validate. Fallback", e);
            if (shadowMode) {
                return jsonOutput;
            }
            return "{\"review_status\":\"PENDING_REVIEW\", \"error\":\"schema_mismatch_or_malformed_json\"}";
        }
    }

    private void checkBusinessRules(String docType, ObjectNode node, String rawOcrText, ValidationReport report) {
        // Kiểm tra Tax Code
        if (node.hasNonNull("tax_code")) {
            String taxCode = node.get("tax_code").asText();
            if (!taxCode.isBlank()) {
                if (!businessRuleValidator.validateTaxCode(taxCode)) {
                    businessRuleViolationsCount.incrementAndGet();
                    report.addError("Mã số thuế không hợp lệ theo thuật toán check-digit: " + taxCode);
                }
                checkGrounding("tax_code", taxCode, rawOcrText, report);
            }
        }

        // Kiểm tra Ngày tháng (invoice_date, payment_date, effective_date)
        for (String dateField : new String[]{"invoice_date", "payment_date", "effective_date"}) {
            if (node.hasNonNull(dateField)) {
                String dateVal = node.get(dateField).asText();
                if (!dateVal.isBlank()) {
                    if (!businessRuleValidator.validateDate(dateVal)) {
                        businessRuleViolationsCount.incrementAndGet();
                        report.addError(String.format("Ngày '%s' không hợp lệ hoặc nằm ngoài khoảng cho phép: %s", dateField, dateVal));
                    }
                    checkGrounding(dateField, dateVal, rawOcrText, report);
                }
            }
        }

        // Kiểm tra Tổng tiền & VAT
        Double totalAmount = node.hasNonNull("total_amount") ? node.get("total_amount").asDouble() : null;
        Double vatAmount = node.hasNonNull("vat_amount") ? node.get("vat_amount").asDouble() : null;
        if (totalAmount != null || vatAmount != null) {
            if (!businessRuleValidator.validateAmounts(totalAmount, vatAmount)) {
                businessRuleViolationsCount.incrementAndGet();
                report.addError(String.format("Tổng tiền (%s) hoặc VAT (%s) không hợp lệ", totalAmount, vatAmount));
            }
            if (totalAmount != null) {
                checkGrounding("total_amount", String.valueOf(totalAmount.longValue()), rawOcrText, report);
            }
        }

        // Kiểm tra các trường định danh khác nếu có (seller_name, party_a, party_b)
        for (String nameField : new String[]{"seller_name", "party_a", "party_b", "customer_name"}) {
            if (node.hasNonNull(nameField)) {
                String val = node.get(nameField).asText();
                checkGrounding(nameField, val, rawOcrText, report);
            }
        }
    }

    private void checkGrounding(String fieldName, String value, String rawOcrText, ValidationReport report) {
        if (rawOcrText != null && !rawOcrText.isBlank() && value != null && !value.isBlank()) {
            if (businessRuleValidator.isGrounded(value, rawOcrText)) {
                report.addGrounded(fieldName);
            } else {
                groundingViolationsCount.incrementAndGet();
                report.addUngrounded(fieldName);
                report.addError(String.format("Field '%s' (giá trị: '%s') không tìm thấy trong văn bản OCR gốc (Grounding Fail)", fieldName, value));
            }
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

    public long getTotalValidations() {
        return totalValidations.get();
    }

    public long getSchemaViolationsCount() {
        return schemaViolationsCount.get();
    }

    public long getBusinessRuleViolationsCount() {
        return businessRuleViolationsCount.get();
    }

    public long getGroundingViolationsCount() {
        return groundingViolationsCount.get();
    }
}