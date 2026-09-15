package com.company.bizocr.extract;

import com.company.bizocr.validator.BankAccountValidator;
import com.company.bizocr.validator.TaxCodeValidator;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CandidateFilterService {

    /**
     * Sàng lọc danh sách chuỗi bắt được từ Regex.
     * Candidate nào fail checksum MST sẽ bị loại ngay lập tức khỏi MST field.
     */
    public Optional<String> resolveTaxCode(List<String> rawCandidates) {
        if (rawCandidates == null) return Optional.empty();
        return rawCandidates.stream()
                .filter(TaxCodeValidator::isValid)
                .findFirst();
    }

    /**
     * Phân định Số tài khoản ngân hàng (tương thích ngược):
     */
    public Optional<String> resolveBankAccount(List<String> rawCandidates, String bankCode) {
        return resolveBankAccount(rawCandidates, bankCode, null);
    }

    /**
     * Phân định Số tài khoản ngân hàng:
     * - Bỏ qua các candidate đã thỏa mãn Checksum MST để tránh nhận nhầm MST làm STK.
     * - Kiểm tra ngữ cảnh rawText: Nếu candidate nằm trong ngữ cảnh "MST"/"Mã số thuế"
     *   (ví dụ: MST 13 số bị lỗi OCR 1 chữ số nên fail checksum), loại bỏ để không bị
     *   nhận nhầm thành STK Agribank/VBA (13 số).
     */
    public Optional<String> resolveBankAccount(List<String> rawCandidates, String bankCode, String rawText) {
        if (rawCandidates == null) return Optional.empty();
        return rawCandidates.stream()
                .filter(candidate -> !TaxCodeValidator.isValid(candidate))
                .filter(candidate -> !isTaxCodeContext(rawText, candidate))
                .filter(candidate -> BankAccountValidator.isValid(candidate, bankCode))
                .findFirst();
    }

    /**
     * Kiểm tra xem candidate có xuất hiện trong ngữ cảnh nhãn MST thay vì STK hay không.
     */
    private boolean isTaxCodeContext(String rawText, String candidate) {
        if (rawText == null || candidate == null || candidate.isBlank()) {
            return false;
        }
        String unaccented = stripAccents(rawText).toLowerCase();
        String cleanCandidate = candidate.trim();
        if (cleanCandidate.isBlank()) return false;

        String regex;
        if (cleanCandidate.matches("^[0-9]{13}$")) {
            regex = "(?<![0-9])(" + Pattern.quote(cleanCandidate) + "|" + Pattern.quote(cleanCandidate.substring(0, 10)) + "[- ]?" + Pattern.quote(cleanCandidate.substring(10)) + ")(?![0-9])";
        } else {
            regex = "(?<![0-9])" + Pattern.quote(cleanCandidate) + "(?![0-9])";
        }

        Pattern candidatePattern = Pattern.compile(regex);
        Matcher matcher = candidatePattern.matcher(unaccented);

        int candidateOccurrences = 0;
        int mstContextMatches = 0;

        while (matcher.find()) {
            candidateOccurrences++;
            int start = matcher.start();
            int windowStart = Math.max(0, start - 40);
            String contextBefore = unaccented.substring(windowStart, start);

            boolean hasTaxKeywords = contextBefore.contains("mst")
                    || contextBefore.contains("ma so thue")
                    || contextBefore.contains("tax code")
                    || contextBefore.contains("m.s.t");

            boolean hasBankKeywords = contextBefore.contains("stk")
                    || contextBefore.contains("so tai khoan")
                    || contextBefore.contains("tai khoan")
                    || contextBefore.contains("account")
                    || contextBefore.contains("so tk");

            if (hasTaxKeywords && !hasBankKeywords) {
                mstContextMatches++;
            }
        }

        return candidateOccurrences > 0 && candidateOccurrences == mstContextMatches;
    }

    private String stripAccents(String input) {
        if (input == null) return "";
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        String noMarks = normalized.replaceAll("\\p{Mn}+", "");
        return noMarks.replace('đ', 'd').replace('Đ', 'D');
    }
}