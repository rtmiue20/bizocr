package com.company.bizocr.classify;

import com.company.bizocr.model.dto.ClassifyResult;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

@Component
public class TemplateMatcher {

    // Ngưỡng độ tin cậy tối thiểu để chấp nhận kết quả Template Matching
    private static final double CONFIDENCE_THRESHOLD = 0.70;

    // Danh sách quy tắc từ khóa cho từng loại chứng từ
    private final Map<String, List<String>> keywordRules = new HashMap<>();

    // Danh sách Regex đặc trưng
    private final Map<String, Pattern> regexRules = new HashMap<>();

    public TemplateMatcher() {
        initRules();
    }

    /**
     * Khởi tạo các bộ mẫu từ khóa cho các chứng từ ưu tiên cao
     */
    private void initRules() {
        // 1. Hóa đơn giá trị gia tăng (VAT Invoice)
        keywordRules.put("VAT_INVOICE", List.of(
                "hóa đơn giá trị gia tăng", "hóa đơn bán hàng", "mẫu số",
                "ký hiệu", "số hóa đơn", "mã cơ quan thuế", "tổng tiền thanh toán"
        ));
        regexRules.put("VAT_INVOICE", Pattern.compile("mã số thuế|mst|hóa đơn", Pattern.CASE_INSENSITIVE));

        // 2. Hợp đồng kinh tế / Lao động (Contract)
        keywordRules.put("CONTRACT", List.of(
                "cộng hòa xã hội chủ nghĩa việt nam",
                "độc lập tự do hạnh phúc",
                "hợp đồng",
                "bên a",
                "bên b",
                "điều khoản",
                "trách nhiệm của các bên",
                "scan"
        ));

        // 3. Danh thiếp (Name Card)
        keywordRules.put("NAME_CARD", List.of(
                "phone", "mobile", "email", "website", "address",
                "tel", "fax", "director", "manager"
        ));

        // 4. Căn cước công dân / CMND (ID Card)
        keywordRules.put("ID_CARD", List.of(
                "căn cước công dân", "chứng minh nhân dân", "số / no",
                "họ và tên", "ngày sinh", "quốc tịch", "quê quán"
        ));

    }

    /**
     * Thực hiện khớp mẫu chuỗi Text lấy từ Python OCR
     */
    public Optional<ClassifyResult> match(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return Optional.empty();
        }

        String normalizedText = rawText.toLowerCase();
        String bestMatchedType = null;
        double maxScore = 0.0;

        for (Map.Entry<String, List<String>> entry : keywordRules.entrySet()) {
            String docType = entry.getKey();
            List<String> keywords = entry.getValue();

            long matchCount = keywords.stream()
                    .filter(normalizedText::contains)
                    .count();

            // Tính điểm tỉ lệ % từ khóa khớp được
            double score = (double) matchCount / keywords.size();

            // Thưởng điểm nếu khớp thêm Regex đặc trưng
            if (regexRules.containsKey(docType) && regexRules.get(docType).matcher(normalizedText).find()) {
                score += 0.15;
            }

            if (score > maxScore) {
                maxScore = score;
                bestMatchedType = docType;
            }
        }

        // Nếu điểm vượt qua ngưỡng tin cậy thì trả về kết quả
        if (maxScore >= CONFIDENCE_THRESHOLD && bestMatchedType != null) {
            return Optional.of(ClassifyResult.builder()
                    .documentType(bestMatchedType)
                    .confidenceScore(Math.min(maxScore, 1.0)) // Max là 1.0
                    .matchedBy("TEMPLATE_MATCHING")
                    .success(true)
                    .outlier(false)
                    .build());
        }

        // Không khớp mẫu nào -> Trả về Empty để kích hoạt AI Fallback
        return Optional.empty();
    }
}