package com.company.bizocr.validator;

import com.company.bizocr.company.CompanyNameNormalizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Validator nghiệp vụ chuyên sâu cho tài liệu OCR Việt Nam:
 * 1. Check-digit Mã Số Thuế (MST) theo thuật toán Tổng cục Thuế Việt Nam (Modulo 11).
 * 2. Ngày tháng (format, khoảng thời gian thực tế không quá tương lai/quá khứ).
 * 3. Tổng tiền (dương, không vượt ngưỡng bất thường, quan hệ vat_amount <= total_amount).
 * 4. Grounding: Đối chiếu giá trị bóc tách xem có xuất hiện trong raw OCR text không.
 */
@Slf4j
@Component
public class BusinessRuleValidator {

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy")
    );

    private final CompanyNameNormalizer nameNormalizer = new CompanyNameNormalizer();

    /**
     * Kiểm tra Check-digit MST Việt Nam.
     */
    public boolean validateTaxCode(String taxCode) {
        return TaxCodeValidator.isValid(taxCode);
    }

    /**
     * Kiểm tra tính hợp lệ của ngày tháng:
     * - Đúng định dạng thông dụng.
     * - Năm trong khoảng 1990 đến năm hiện tại + 1.
     */
    public boolean validateDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return false;
        String clean = dateStr.trim();

        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                LocalDate date = LocalDate.parse(clean, formatter);
                int year = date.getYear();
                int currentYear = LocalDate.now().getYear();
                if (year >= 1990 && year <= currentYear + 1) {
                    return true;
                }
            } catch (DateTimeParseException ignored) {
            }
        }
        return false;
    }

    /**
     * Kiểm tra tổng tiền và tiền thuế:
     * - totalAmount > 0 và <= 100 tỷ VNĐ (giới hạn thực tế).
     * - vatAmount <= totalAmount nếu cả 2 cùng tồn tại.
     */
    public boolean validateAmounts(Double totalAmount, Double vatAmount) {
        if (totalAmount != null) {
            if (totalAmount <= 0 || totalAmount > 100_000_000_000.0) {
                return false;
            }
            if (vatAmount != null && vatAmount > totalAmount) {
                return false;
            }
        }
        if (vatAmount != null && vatAmount < 0) {
            return false;
        }
        return true;
    }

    /**
     * Grounding: Kiểm tra giá trị bóc tách có thực sự xuất hiện trong văn bản OCR gốc hay không.
     * Hỗ trợ chuẩn hóa dấu tiếng Việt, khoảng trắng và số.
     */
    public boolean isGrounded(String fieldValue, String rawOcrText) {
        if (fieldValue == null || fieldValue.isBlank()) return true;
        if (rawOcrText == null || rawOcrText.isBlank()) return false;

        // 1. Kiểm tra đối sánh chuỗi thô trực tiếp
        if (rawOcrText.contains(fieldValue.trim())) {
            return true;
        }

        // 2. Đối sánh sau chuẩn hóa (bỏ dấu tiếng Việt, viết hoa, gom khoảng trắng)
        String normField = nameNormalizer.normalize(fieldValue);
        String normOcr = nameNormalizer.normalize(rawOcrText);
        if (!normField.isBlank() && normOcr.contains(normField)) {
            return true;
        }

        // 2b. Đối sánh sau khi xóa toàn bộ khoảng trắng (xử lý "0912 345 678" vs "0912345678").
        //     Tránh false positive cho trường văn bản bằng cách chỉ áp dụng khi chuỗi chứa nhiều chữ số.
        String compactField = normField.replace(" ", "");
        String compactOcr   = normOcr.replace(" ", "");
        if (!compactField.isBlank() && compactOcr.contains(compactField)) {
            // Yêu cầu ít nhất 40% ký tự là chữ số để tránh false positive với tên công ty
            long digitCount = compactField.chars().filter(Character::isDigit).count();
            if (digitCount * 100 / compactField.length() >= 40) {
                return true;
            }
        }

        // 3. Nếu là chuỗi số (MST, SĐT, tiền 500,000 → 500000):
        //    Xây dựng regex cho phép ký tự phân cách (dấu chấm, gạch ngang, khoảng trắng) giữa các chữ số.
        //    Tránh ghép toàn bộ chữ số OCR thành một chuỗi (gây false positive).
        String digitsOnlyField = fieldValue.replaceAll("[^0-9]", "");
        if (digitsOnlyField.length() >= 4) {
            // Pattern: mỗi chữ số có thể cách nhau bởi ký tự phân cách (dấu chấm, gạch, khoảng trắng)
            StringBuilder sb = new StringBuilder("(?<![0-9])");
            for (char c : digitsOnlyField.toCharArray()) {
                sb.append(c).append("[\\s.,.-]*");
            }
            // Xóa đuôi "[\s.,.-]*" thừa và thêm lookahead
            String flexPattern = sb.substring(0, sb.lastIndexOf("[")) + "(?![0-9])";
            try {
                if (Pattern.compile(flexPattern).matcher(rawOcrText).find()) {
                    return true;
                }
            } catch (PatternSyntaxException e) {
                // Fallback: so sánh chuỗi số ghép
                String digitsOnlyOcr = rawOcrText.replaceAll("[^0-9]", "");
                if (digitsOnlyOcr.contains(digitsOnlyField)) {
                    return true;
                }
            }
        }

        return false;
    }
}
