package com.company.bizocr.classify;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Bộ so khớp tiêu đề mờ (Fuzzy Title Matcher) sử dụng thuật toán Jaro-Winkler Similarity.
 * Giúp nhận diện chính xác loại chứng từ ngay cả khi OCR bị sai lệch ký tự nhẹ ở phần tiêu đề.
 */
@Slf4j
@Component
public class FuzzyTitleMatcher {

    private final JaroWinklerSimilarity similarityEngine = new JaroWinklerSimilarity();

    // Ngưỡng tương đồng tối thiểu (85%) để tính điểm thưởng Fuzzy
    private static final double SIMILARITY_THRESHOLD = 0.85;

    /**
     * So khớp N dòng đầu tiên của rawText với danh sách tiêu đề mẫu.
     * @return Score bonus từ 0.0 đến 1.0 dựa trên độ tương đồng cao nhất.
     */
    public double calculateTitleBonus(String rawText, List<String> sampleTitles) {
        if (StringUtils.isBlank(rawText) || sampleTitles == null || sampleTitles.isEmpty()) {
            return 0.0;
        }

        // Lấy 5 dòng đầu tiên và chuẩn hóa (bỏ dấu, in hoa, xóa ký tự đặc biệt)
        String normalizedHeader = extractAndNormalizeHeader(rawText, 5);
        if (normalizedHeader.isBlank()) {
            return 0.0;
        }

        double maxSimilarity = 0.0;

        for (String targetTitle : sampleTitles) {
            String cleanTarget = normalizeText(targetTitle);
            if (cleanTarget.isBlank()) continue;

            int windowSize = cleanTarget.length();

            if (normalizedHeader.length() <= windowSize) {
                double score = similarityEngine.apply(normalizedHeader, cleanTarget);
                maxSimilarity = Math.max(maxSimilarity, score);
            } else {
                // Cửa sổ trượt qua chuỗi header để tìm vị trí khớp nhất
                for (int i = 0; i <= normalizedHeader.length() - windowSize; i++) {
                    String subHeader = normalizedHeader.substring(i, Math.min(i + windowSize + 2, normalizedHeader.length()));
                    double score = similarityEngine.apply(subHeader, cleanTarget);
                    maxSimilarity = Math.max(maxSimilarity, score);

                    if (maxSimilarity >= 0.95) break; // Khớp gần như hoàn hảo -> Ngắt sớm
                }
            }
        }

        return maxSimilarity >= SIMILARITY_THRESHOLD ? maxSimilarity : 0.0;
    }

    private String extractAndNormalizeHeader(String text, int maxLines) {
        String[] lines = text.split("\\r?\\n");
        StringBuilder headerBuilder = new StringBuilder();

        int lineCount = Math.min(lines.length, maxLines);
        for (int i = 0; i < lineCount; i++) {
            headerBuilder.append(lines[i]).append(" ");
        }

        return normalizeText(headerBuilder.toString());
    }

    public static String normalizeText(String input) {
        if (input == null) return "";
        String unaccented = TemplateMatcher.removeVietnameseAccents(input);
        return unaccented.toUpperCase()
                .replaceAll("[^A-Z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}