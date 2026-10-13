package com.company.bizocr.score;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompletenessScoreService {

    private final ObjectMapper objectMapper;

    /**
     * Danh sách các trường cốt lõi (Mandatory Fields) cho từng loại chứng từ.
     * Tỷ lệ các trường này quyết định 50% Completeness Score.
     */
    private static final Map<String, List<String>> MANDATORY_FIELDS_MAP = Map.ofEntries(
            Map.entry("NAMECARD", List.of("contact_name", "phone", "email", "company_name")),
            Map.entry("name_card", List.of("contact_name", "phone", "email", "company_name")),
            Map.entry("VAT_INVOICE", List.of("tax_code", "seller_name", "invoice_date", "total_amount")),
            Map.entry("invoice_supermarket", List.of("seller_name", "invoice_date", "total_amount")),
            Map.entry("CONTRACT", List.of("party_a", "party_b", "effective_date", "total_amount")),
            Map.entry("contract_labor", List.of("party_a", "party_b", "effective_date", "salary")),
            Map.entry("SERVICE_PAYMENT_VOUCHER", List.of("seller_name", "invoice_date", "total_amount")),
            Map.entry("ELECTRICITY_BILL", List.of("customer_name", "total_amount", "invoice_date")),
            Map.entry("invoice_electricity", List.of("customer_name", "total_amount", "invoice_date")),
            Map.entry("WATER_BILL", List.of("customer_name", "total_amount", "invoice_date")),
            Map.entry("invoice_water", List.of("customer_name", "total_amount", "invoice_date")),
            Map.entry("ID_CARD", List.of("id_number", "full_name", "dob", "address")),
            Map.entry("BANK_STATEMENT", List.of("account_number", "bank_name", "total_amount", "transaction_date"))
    );

    /**
     * Công thức Completeness Score:
     * Completeness Score = (0.5 * MandatoryFieldsRatio) + (0.3 * ContourAreaRatio) + (0.2 * AvgOcrConfidence)
     */
    public CompletenessScoreResult compute(
            String documentType,
            String extractedFieldsJson,
            Double contourAreaRatio,
            Double avgOcrConfidence) {

        double actualContourRatio = contourAreaRatio != null ? contourAreaRatio : 0.6;
        double actualOcrConfidence = avgOcrConfidence != null ? avgOcrConfidence : 0.5;

        // Chuẩn hóa giới hạn [0.0, 1.0] cho các hệ số đầu vào
        actualContourRatio = Math.max(0.0, Math.min(1.0, actualContourRatio));
        actualOcrConfidence = Math.max(0.0, Math.min(1.0, actualOcrConfidence));

        int filledCount = 0;
        int totalMandatory = 0;
        double mandatoryRatio = 0.0;

        List<String> targetMandatoryFields = MANDATORY_FIELDS_MAP.get(documentType != null ? documentType.trim() : "");

        try {
            if (extractedFieldsJson != null && !extractedFieldsJson.isBlank()) {
                JsonNode root = objectMapper.readTree(extractedFieldsJson);

                if (targetMandatoryFields != null && !targetMandatoryFields.isEmpty()) {
                    totalMandatory = targetMandatoryFields.size();
                    for (String fieldKey : targetMandatoryFields) {
                        if (hasValue(root, fieldKey)) {
                            filledCount++;
                        }
                    }
                    mandatoryRatio = totalMandatory > 0 ? (double) filledCount / totalMandatory : 0.0;
                } else {
                    // Mẫu chưa định nghĩa cụ thể: đếm số trường khác null/rỗng
                    Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
                    int nonMetaCount = 0;
                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> entry = fields.next();
                        if (!entry.getKey().startsWith("_") && !entry.getKey().equals("review_status") && isNodeFilled(entry.getValue())) {
                            nonMetaCount++;
                        }
                    }
                    totalMandatory = Math.max(4, nonMetaCount);
                    filledCount = nonMetaCount;
                    mandatoryRatio = totalMandatory > 0 ? Math.min(1.0, (double) filledCount / totalMandatory) : 0.0;
                }
            }
        } catch (Exception e) {
            log.warn("[CompletenessScore] Không thể parse extractedFieldsJson: {}", e.getMessage());
        }

        // Tính toán Completeness Score theo trọng số (0.5 - 0.3 - 0.2)
        double rawScore = (0.5 * mandatoryRatio) + (0.3 * actualContourRatio) + (0.2 * actualOcrConfidence);
        double finalScore = round(Math.max(0.0, Math.min(1.0, rawScore)), 2);

        String grade;
        String gradeDescription;
        if (finalScore >= 0.85) {
            grade = "A";
            gradeDescription = "Xuất sắc";
        } else if (finalScore >= 0.70) {
            grade = "B";
            gradeDescription = "Tốt";
        } else if (finalScore >= 0.50) {
            grade = "C";
            gradeDescription = "Trung bình";
        } else {
            grade = "D";
            gradeDescription = "Cần xem lại";
        }

        long percentage = Math.round(finalScore * 100);
        String label = percentage + "% - " + gradeDescription + " (" + grade + ")";

        return new CompletenessScoreResult(
                finalScore,
                grade,
                label,
                round(mandatoryRatio, 2),
                round(actualContourRatio, 2),
                round(actualOcrConfidence, 2),
                filledCount,
                totalMandatory
        );
    }

    public String buildConfirmPrompt(CompletenessScoreResult result) {
        long pct = Math.round(result.score() * 100);
        return switch (result.grade()) {
            case "A" -> "Độ chính xác và độ đầy đủ đạt " + pct + "% (A - Xuất sắc). Dữ liệu rõ ràng, vui lòng kiểm tra và xác nhận.";
            case "B" -> "Độ chính xác và độ đầy đủ đạt " + pct + "% (B - Tốt). Kiểm tra nhanh thông tin trước khi duyệt.";
            case "C" -> "Độ chính xác đạt " + pct + "% (C - Trung bình). Một số trường có thể còn thiếu, hãy bổ sung trước khi xác nhận.";
            default -> "Độ chính xác chỉ đạt " + pct + "% (D - Cần xem lại). Hình ảnh có thể bị mờ hoặc thiếu trường, vui lòng kiểm tra kỹ hoặc nhập tay.";
        };
    }

    private boolean hasValue(JsonNode root, String fieldKey) {
        if (!root.has(fieldKey)) {
            // Hỗ trợ alias thông dụng
            if ("contact_name".equals(fieldKey) && (root.has("name") || root.has("full_name"))) {
                return isNodeFilled(root.get("name")) || isNodeFilled(root.get("full_name"));
            }
            if ("company_name".equals(fieldKey) && (root.has("company") || root.has("seller_name"))) {
                return isNodeFilled(root.get("company")) || isNodeFilled(root.get("seller_name"));
            }
            if ("phone".equals(fieldKey) && (root.has("mobile") || root.has("company_phone"))) {
                return isNodeFilled(root.get("mobile")) || isNodeFilled(root.get("company_phone"));
            }
            return false;
        }
        return isNodeFilled(root.get(fieldKey));
    }

    private boolean isNodeFilled(JsonNode node) {
        if (node == null || node.isNull()) return false;
        if (node.isTextual()) {
            return !node.asText().trim().isBlank();
        }
        if (node.isNumber()) {
            return true;
        }
        if (node.isArray()) {
            return !node.isEmpty();
        }
        if (node.isObject()) {
            return !node.isEmpty();
        }
        return false;
    }

    private double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();
        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }
}
