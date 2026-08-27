package com.company.bizocr.company;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;

@Component
public class CompanyNameNormalizer {

    /**
     * Chuẩn hóa tên công ty để tìm kiếm:
     *
     * Công ty TNHH ABC Việt Nam
     * CTY TNHH ABC VN
     * CÔNG-TY ABC VIỆT NAM
     *
     * sẽ được đưa về chuỗi thống nhất hơn.
     */
    public String normalize(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }

        String value = input
                .trim()
                .replace('Đ', 'D')
                .replace('đ', 'd')
                .toUpperCase(Locale.ROOT);

        // Bỏ dấu tiếng Việt
        value = Normalizer.normalize(
                value,
                Normalizer.Form.NFD
        );

        value = value.replaceAll("\\p{M}", "");

        /*
         * Bỏ các từ pháp lý phổ biến khi tạo khóa tìm kiếm.
         * Tên gốc vẫn được giữ nguyên trong Company.legalName.
         */
        value = value.replaceAll(
                "\\b(CONG TY|CTY|COMPANY|CORPORATION|CORP|"
                        + "LIMITED|LTD|JOINT STOCK COMPANY|"
                        + "TRACH NHIEM HUU HAN|TNHH|"
                        + "CO PHAN|CP|JSC)\\b",
                " "
        );

        return value
                .replaceAll("[^A-Z0-9]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Chuẩn hóa mã số thuế.
     *
     * Ví dụ:
     * 031 234 5678   -> 0312345678
     * 0312345678     -> 0312345678
     */
    public String normalizeTaxCode(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }

        return input
                .replaceAll("\\s+", "")
                .trim();
    }

    /**
     * Chuẩn hóa mã công ty.
     */
    public String normalizeCompanyCode(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }

        return input
                .trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9_\\-/]", "");
    }
}