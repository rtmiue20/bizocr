package com.company.bizocr.extract;

import com.company.bizocr.model.entity.TemplateFieldConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegexExtractorService {

    private final CandidateFilterService candidateFilterService;

    public Map<String, Object> extractFromDynamicConfig(
            List<TemplateFieldConfig> configs,
            String rawText
    ) {
        Map<String, Object> fields = new HashMap<>();

        if (configs == null || rawText == null) {
            return fields;
        }

        for (TemplateFieldConfig cfg : configs) {
            if (cfg == null || cfg.getRegexPattern() == null) {
                continue;
            }

            try {
                Pattern pattern = Pattern.compile(
                        cfg.getRegexPattern(),
                        Pattern.CASE_INSENSITIVE
                );

                Matcher matcher = pattern.matcher(rawText);

                if (matcher.find()) {
                    String rawValue = matcher.groupCount() >= 1
                            ? matcher.group(1)
                            : matcher.group(0);

                    if (rawValue == null || rawValue.isBlank()) {
                        continue;
                    }

                    String fieldKey = cfg.getFieldKey();

                    if ("tax_code".equalsIgnoreCase(fieldKey)
                            || "mst".equalsIgnoreCase(fieldKey)
                            || "ma_so_thue".equalsIgnoreCase(fieldKey)) {

                        List<String> candidates =
                                extractTaxCodeCandidates(rawText);

                        if (!candidates.contains(rawValue.trim())) {
                            candidates = new ArrayList<>(candidates);
                            candidates.add(0, rawValue.trim());
                        }

                        candidateFilterService
                                .resolveTaxCode(candidates)
                                .ifPresent(validMst ->
                                        fields.put(fieldKey, validMst)
                                );

                    } else if ("bank_account".equalsIgnoreCase(fieldKey)
                            || "stk".equalsIgnoreCase(fieldKey)
                            || "so_tai_khoan".equalsIgnoreCase(fieldKey)) {

                        List<String> candidates =
                                List.of(rawValue.trim());

                        candidateFilterService
                                .resolveBankAccount(
                                        candidates,
                                        null,
                                        rawText
                                )
                                .ifPresent(validAccount ->
                                        fields.put(fieldKey, validAccount)
                                );

                    } else {
                        fields.put(
                                fieldKey,
                                castDynamicValue(
                                        rawValue,
                                        cfg.getDataType()
                                )
                        );
                    }
                }

            } catch (Exception error) {
                log.warn(
                        "Regex cấu hình DB lỗi cho field '{}' "
                                + "(doc_type={}): {}",
                        cfg.getFieldKey(),
                        cfg.getDocTypeCode(),
                        error.getMessage()
                );
            }
        }

        return fields;
    }

    public Map<String, Object> extractFromHardcodedRegex(
            String documentType,
            String rawText
    ) {
        Map<String, Object> regexFields = new HashMap<>();

        if (rawText == null || rawText.isBlank()) {
            return regexFields;
        }

        if ("VAT_INVOICE".equalsIgnoreCase(documentType)
                || "HOA_DON_VAT".equalsIgnoreCase(documentType)) {

            regexFields = extractInvoiceVatFields(rawText);

        } else if ("ELECTRICITY_BILL".equalsIgnoreCase(documentType)
                || "HOA_DON_DIEN".equalsIgnoreCase(documentType)) {

            regexFields = extractElectricityFields(rawText);

        } else if ("WATER_BILL".equalsIgnoreCase(documentType)
                || "HOA_DON_NUOC".equalsIgnoreCase(documentType)) {

            regexFields = extractWaterFields(rawText);

        } else if ("SERVICE_PAYMENT_VOUCHER".equalsIgnoreCase(documentType)
                || "RECEIPT".equalsIgnoreCase(documentType)
                || "SERVICE_BILL".equalsIgnoreCase(documentType)) {

            regexFields = extractServicePaymentFields(rawText);

        } else if ("CONTRACT".equalsIgnoreCase(documentType)
                || "HOP_DONG".equalsIgnoreCase(documentType)) {

            regexFields = extractContractFields(rawText);

        } else if ("NAME_CARD".equalsIgnoreCase(documentType)
                || "NAMECARD".equalsIgnoreCase(documentType)
                || "DANH_THIEP".equalsIgnoreCase(documentType)) {

            regexFields = extractNameCardFields(rawText);
        }

        return regexFields;
    }

    private Map<String, Object> extractInvoiceVatFields(String text) {
        Map<String, Object> fields = new HashMap<>();

        List<String> candidates = extractTaxCodeCandidates(text);

        candidateFilterService
                .resolveTaxCode(candidates)
                .ifPresent(mst -> fields.put("tax_code", mst));

        Pattern amountPattern = Pattern.compile(
                "(?:Tong cong tien thanh toan"
                        + "|Tong tien thanh toan"
                        + "|Tong tien"
                        + "|Cong tien hang"
                        + "|Thanh tien"
                        + "|Tong cong)"
                        + "[:\\s]*([0-9\\.,]+)",
                Pattern.CASE_INSENSITIVE
        );

        String amountRaw =
                captureGroupFromOriginal(amountPattern, text);

        if (amountRaw != null) {
            fields.put("total_amount", parseAmount(amountRaw));
        }

        return fields;
    }

    private Map<String, Object> extractElectricityFields(String text) {
        Map<String, Object> fields = new HashMap<>();

        Pattern customerPattern = Pattern.compile(
                "(?:The khach hang"
                        + "|Ma KH"
                        + "|Ma khach hang)"
                        + "[:\\s]*([A-Z0-9]+)",
                Pattern.CASE_INSENSITIVE
        );

        String customerId =
                captureGroupFromOriginal(customerPattern, text);

        if (customerId != null) {
            fields.put("customer_id", customerId);
        }

        Pattern amountPattern = Pattern.compile(
                "(?:Tong cong tien thanh toan"
                        + "|Tong tien dien thanh toan"
                        + "|Tong tien thanh toan"
                        + "|Tien dien"
                        + "|Tong tien"
                        + "|Thanh tien)"
                        + "[:\\s]*([0-9\\.,]+)",
                Pattern.CASE_INSENSITIVE
        );

        String amountRaw =
                captureGroupFromOriginal(amountPattern, text);

        if (amountRaw != null) {
            fields.put("total_amount", parseAmount(amountRaw));
        }

        return fields;
    }

    private Map<String, Object> extractWaterFields(String text) {
        Map<String, Object> fields = new HashMap<>();

        Pattern customerPattern = Pattern.compile(
                "(?:Ma danh bo"
                        + "|So danh bo"
                        + "|Ma KH"
                        + "|Ma khach hang)"
                        + "[:\\s]*([A-Z0-9]+)",
                Pattern.CASE_INSENSITIVE
        );

        String customerId =
                captureGroupFromOriginal(customerPattern, text);

        if (customerId != null) {
            fields.put("customer_id", customerId);
        }

        Pattern amountPattern = Pattern.compile(
                "(?:Tong tien thanh toan"
                        + "|Cong tien hang"
                        + "|Tien nuoc"
                        + "|Tong tien"
                        + "|Thanh tien)"
                        + "[:\\s]*([0-9\\.,]+)",
                Pattern.CASE_INSENSITIVE
        );

        String amountRaw =
                captureGroupFromOriginal(amountPattern, text);

        if (amountRaw != null) {
            fields.put("total_amount", parseAmount(amountRaw));
        }

        return fields;
    }

    private Map<String, Object> extractServicePaymentFields(
            String text
    ) {
        Map<String, Object> fields = new HashMap<>();

        String[] lines = text.split("\\r?\\n");

        for (String line : lines) {
            String trimmed = line.trim();
            String normalized = stripAccents(trimmed).toLowerCase();

            if (!trimmed.isEmpty()
                    && !normalized.contains("phieu thanh toan")
                    && !normalized.contains("tong chi phi")
                    && !normalized.contains("thanh tien")) {

                fields.put("service_name", trimmed);
                break;
            }
        }

        Pattern amountPattern = Pattern.compile(
                "(?:Tong chi phi"
                        + "|Tong cong tien thanh toan"
                        + "|Tong tien thanh toan"
                        + "|Tong tien"
                        + "|Thanh tien"
                        + "|Tong cong)"
                        + "[:\\s]*([0-9\\.,]+)",
                Pattern.CASE_INSENSITIVE
        );

        String amountRaw =
                captureGroupFromOriginal(amountPattern, text);

        if (amountRaw != null) {
            fields.put("total_amount", parseAmount(amountRaw));
        }

        return fields;
    }

    private Map<String, Object> extractContractFields(String text) {
        Map<String, Object> fields = new HashMap<>();

        Pattern partyAPattern = Pattern.compile(
                "(?:Ben A)[:\\s]*([^\\n]+)",
                Pattern.CASE_INSENSITIVE
        );

        String partyA =
                captureGroupFromOriginal(partyAPattern, text);

        if (partyA != null) {
            fields.put("party_a", partyA.trim());
        }

        Pattern partyBPattern = Pattern.compile(
                "(?:Ben B)[:\\s]*([^\\n]+)",
                Pattern.CASE_INSENSITIVE
        );

        String partyB =
                captureGroupFromOriginal(partyBPattern, text);

        if (partyB != null) {
            fields.put("party_b", partyB.trim());
        }

        Pattern amountPattern = Pattern.compile(
                "(?:Gia tri quyet toan"
                        + "|Gia tri hop dong"
                        + "|Tong cong"
                        + "|Tong tien)"
                        + "[:\\s]*([0-9\\.,]+)",
                Pattern.CASE_INSENSITIVE
        );

        String amountRaw =
                captureGroupFromOriginal(amountPattern, text);

        if (amountRaw != null) {
            fields.put("total_amount", parseAmount(amountRaw));
        }

        List<String> candidates =
                extractTaxCodeCandidates(text);

        candidateFilterService
                .resolveTaxCode(candidates)
                .ifPresent(mst -> fields.put("tax_code", mst));

        return fields;
    }

    private Map<String, Object> extractNameCardFields(String text) {
        Map<String, Object> fields = new HashMap<>();

        if (text == null || text.isBlank()) {
            return fields;
        }

        // 1. Mã số thuế
        List<String> taxCandidates =
                extractTaxCodeCandidates(text);

        candidateFilterService
                .resolveTaxCode(taxCandidates)
                .ifPresent(mst -> fields.put("tax_code", mst));

        // 2. Chức danh
        Pattern titlePattern = Pattern.compile(
                "(?i)\\b("
                        + "giam doc tai chinh"
                        + "|tong giam doc"
                        + "|pho giam doc"
                        + "|giam doc"
                        + "|cfo"
                        + "|ceo"
                        + "|cto"
                        + "|coo"
                        + "|truong phong"
                        + "|quan ly"
                        + "|manager"
                        + "|director"
                        + "|ke toan truong"
                        + "|chu tich"
                        + "|chuyen vien"
                        + "|consultant"
                        + "|specialist"
                        + ")\\b"
        );

        String[] lines = text.split("\\R");
        int titleLineIndex = -1;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            String normalizedLine =
                    stripAccents(line).toLowerCase();

            if (titlePattern.matcher(normalizedLine).find()) {
                fields.put("contact_title", line);
                titleLineIndex = i;
                break;
            }
        }

        // 3. Tên người liên hệ
        Pattern namePattern = Pattern.compile(
                "^[\\p{L}\\s.]{2,50}$"
        );

        for (int i = 0; i < Math.min(lines.length, 6); i++) {
            if (i == titleLineIndex) {
                continue;
            }

            String candidate = lines[i].trim();

            if (candidate.isBlank()) {
                continue;
            }

            String upper = candidate.toUpperCase();
            String[] words = candidate.split("\\s+");

            // Bỏ logo hoặc từ viết tắt một từ như OTCIT, TCIT.
            if (words.length < 2) {
                continue;
            }

            // Bỏ dòng tên công ty, địa chỉ và thông tin liên hệ.
            if (upper.contains("CÔNG TY")
                    || upper.contains("CONG TY")
                    || upper.contains("CTY")
                    || upper.contains("TERMINAL")
                    || upper.contains("LIMITED")
                    || upper.contains("CO.,")
                    || upper.contains("CO,")
                    || upper.contains("CORP")
                    || upper.contains("TAN CANG")
                    || upper.contains("TÂN CẢNG")
                    || upper.contains("GROUP")
                    || upper.contains("LOGISTICS")
                    || upper.contains("SERVICES")
                    || upper.contains("SOLUTIONS")
                    || upper.contains("TẦNG")
                    || upper.contains("TANG ")
                    || upper.contains("TEL")
                    || upper.contains("FAX")
                    || upper.contains("MOBILE")) {
                continue;
            }

            if (namePattern.matcher(candidate).matches()) {
                fields.put("contact_name", candidate);
                break;
            }
        }

        // 4. Tên công ty
        for (String line : lines) {
            String trimmed = line.trim();
            String upper = trimmed.toUpperCase();

            if (upper.startsWith("CÔNG TY")
                    || upper.startsWith("CONG TY")
                    || upper.startsWith("CTY")) {

                fields.put("company_name", trimmed);
                break;
            }
        }

        if (!fields.containsKey("company_name")) {
            for (String line : lines) {
                String trimmed = line.trim();
                String upper = trimmed.toUpperCase();

                if (upper.contains("INTERNATIONAL TERMINAL")
                        || upper.contains("CO., LTD")
                        || upper.contains("CO.,LTD")
                        || upper.contains("CORPORATION")
                        || upper.contains("LIMITED")) {

                    fields.put("company_name", trimmed);
                    break;
                }
            }
        }

        // 5. Số điện thoại
        Pattern mobilePattern = Pattern.compile(
                "(?i)(?:Mobile|Cell|Hotline|DT|Tel|Phone)"
                        + "[:\\s]*([+0-9\\-\\s.()]{8,20})"
        );

        Matcher mobileMatcher = mobilePattern.matcher(text);

        if (mobileMatcher.find()) {
            String cleanPhone = mobileMatcher
                    .group(1)
                    .trim()
                    .replaceAll("[^0-9+]", "");

            fields.put("phone", cleanPhone);

        } else {
            Pattern rawPhonePattern = Pattern.compile(
                    "\\b("
                            + "0[0-9]{9,10}"
                            + "|84-[0-9\\s]{9,12}"
                            + "|\\+84[0-9\\s]{9,12}"
                            + ")\\b"
            );

            Matcher rawPhoneMatcher =
                    rawPhonePattern.matcher(text);

            if (rawPhoneMatcher.find()) {
                String cleanPhone = rawPhoneMatcher
                        .group(1)
                        .trim()
                        .replaceAll("[^0-9+]", "");

                fields.put("phone", cleanPhone);
            }
        }

        /*
         * 6. Email
         *
         * Quan trọng:
         * Không dùng \\s trong group email vì \\s bao gồm cả newline.
         * Regex cũ có thể capture:
         *
         * hung.nguyen@Atcit.com vn
         * Website
         *
         * thành một chuỗi duy nhất.
         *
         * Regex mới bắt buộc bắt đầu từ dòng E-mail và dừng trước
         * newline kế tiếp.
         */
        Pattern emailPattern = Pattern.compile(
                "(?im)^[\\t ]*"
                        + "(?:E-?mail)"
                        + "[\\t ]*[:：]?[\\t ]*"
                        + "([^\\r\\n]+)"
        );

        Matcher emailMatcher = emailPattern.matcher(text);

        if (emailMatcher.find()) {
            String rawEmail = emailMatcher.group(1).trim();
            String sanitizedEmail = sanitizeEmail(rawEmail);

            if (sanitizedEmail != null
                    && !sanitizedEmail.isBlank()) {
                fields.put("email", sanitizedEmail);
            }
        }

        /*
         * 7. Website
         *
         * Website cũng chỉ được capture trong cùng một dòng.
         */
        Pattern websitePattern = Pattern.compile(
                "(?im)^[\\t ]*"
                        + "Website"
                        + "[\\t ]*[:：]?[\\t ]*"
                        + "([^\\r\\n]+)"
        );

        Matcher websiteMatcher =
                websitePattern.matcher(text);

        if (websiteMatcher.find()) {
            String rawWebsite =
                    websiteMatcher.group(1).trim();

            String sanitizedWebsite =
                    sanitizeWebsite(rawWebsite);

            if (sanitizedWebsite != null
                    && !sanitizedWebsite.isBlank()) {
                fields.put("website", sanitizedWebsite);
            }
        }

        // 8. Địa chỉ
        for (String line : lines) {
            String trimmed = line.trim();
            String normalized =
                    stripAccents(trimmed).toLowerCase();

            if (normalized.startsWith("tang ")
                    || normalized.startsWith("so ")
                    || normalized.contains("nguyen hue")
                    || normalized.contains("quan ")
                    || normalized.contains("tp.hcm")
                    || normalized.contains("ha noi")
                    || normalized.startsWith("dia chi:")
                    || normalized.startsWith("address:")) {

                fields.put("address", trimmed);
                break;
            }
        }

        return fields;
    }

    private String sanitizeEmail(String rawEmail) {
        if (rawEmail == null) {
            return null;
        }

        // Bỏ khoảng trắng trong email OCR.
        String cleaned = rawEmail.replaceAll("\\s+", "");

        // @Atcit -> @tcit
        cleaned = cleaned.replaceAll(
                "(?i)@a([a-z0-9])",
                "@$1"
        );

        // c@m -> com
        cleaned = cleaned.replaceAll(
                "(?i)c@m",
                "com"
        );

        // comvn hoặc com.vn bị dính lại.
        cleaned = cleaned.replaceAll(
                "(?i)com\\.?vn",
                "com.vn"
        );

        // Xóa dấu chấm lặp.
        cleaned = cleaned.replaceAll(
                "\\.+",
                "."
        );

        // Bỏ dấu chấm cuối chuỗi.
        cleaned = cleaned.replaceAll(
                "\\.+$",
                ""
        );

        return cleaned;
    }

    private String sanitizeWebsite(String rawWebsite) {
        if (rawWebsite == null) {
            return null;
        }

        String cleaned = rawWebsite.replaceAll(
                "\\s+",
                ""
        );

        // c@m.vn -> com.vn
        cleaned = cleaned.replaceAll(
                "(?i)c@m",
                "com"
        );

        // comvn hoặc com.vn bị dính lại.
        cleaned = cleaned.replaceAll(
                "(?i)com\\.?vn",
                "com.vn"
        );

        // Xóa dấu chấm lặp.
        cleaned = cleaned.replaceAll(
                "\\.+",
                "."
        );

        // Bỏ dấu chấm cuối chuỗi.
        cleaned = cleaned.replaceAll(
                "\\.+$",
                ""
        );

        return cleaned;
    }

    public List<String> extractTaxCodeCandidates(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<String> candidates = new ArrayList<>();
        String stripped = stripAccents(text);

        /*
         * Nếu có label MST, chỉ lấy các số nằm sau label.
         * Không lấy thêm số 10 chữ số khác trong văn bản.
         */
        Pattern labeledPattern = Pattern.compile(
                "(?:Ma so thue"
                        + "|MST"
                        + "|Tax code"
                        + "|M\\.S\\.T)"
                        + "[:\\s]*"
                        + "([0-9]{10}(?:-[0-9]{3})?)",
                Pattern.CASE_INSENSITIVE
        );

        Matcher labeledMatcher =
                labeledPattern.matcher(stripped);

        while (labeledMatcher.find()) {
            String candidate =
                    labeledMatcher.group(1).trim();

            if (!candidates.contains(candidate)) {
                candidates.add(candidate);
            }
        }

        if (!candidates.isEmpty()) {
            return candidates;
        }

        /*
         * Không có label MST thì tìm candidate tổng quát,
         * sau đó loại các số nằm trong ngữ cảnh điện thoại,
         * số tài khoản, số hóa đơn, CCCD...
         */
        Pattern generalPattern = Pattern.compile(
                "\\b("
                        + "[0-9]{10}(?:-[0-9]{3})?"
                        + "|[0-9]{13}"
                        + ")\\b"
        );

        Matcher generalMatcher =
                generalPattern.matcher(stripped);

        while (generalMatcher.find()) {
            int start = generalMatcher.start();
            int windowStart = Math.max(0, start - 30);

            String contextBefore = stripped
                    .substring(windowStart, start)
                    .toLowerCase();

            boolean isNonTaxContext =
                    contextBefore.contains("tel")
                            || contextBefore.contains("dien thoai")
                            || contextBefore.contains("dt:")
                            || contextBefore.contains("d/t")
                            || contextBefore.contains("phone")
                            || contextBefore.contains("hotline")
                            || contextBefore.contains("fax")
                            || contextBefore.contains("stk")
                            || contextBefore.contains("so tai khoan")
                            || contextBefore.contains("tai khoan")
                            || contextBefore.contains("so hd")
                            || contextBefore.contains("so hoa don")
                            || contextBefore.contains("hd so")
                            || contextBefore.contains("invoice no")
                            || contextBefore.contains("zip")
                            || contextBefore.contains("cccd")
                            || contextBefore.contains("cmnd");

            if (!isNonTaxContext) {
                String candidate =
                        generalMatcher.group(1).trim();

                if (!candidates.contains(candidate)) {
                    candidates.add(candidate);
                }
            }
        }

        return candidates;
    }

    private String stripAccents(String input) {
        if (input == null) {
            return "";
        }

        String normalized = Normalizer.normalize(
                input,
                Normalizer.Form.NFD
        );

        String withoutMarks =
                normalized.replaceAll("\\p{Mn}+", "");

        return withoutMarks
                .replace('đ', 'd')
                .replace('Đ', 'D');
    }

    private String captureGroupFromOriginal(
            Pattern unaccentedPattern,
            String originalText
    ) {
        if (originalText == null) {
            return null;
        }

        String stripped = stripAccents(originalText);
        Matcher matcher = unaccentedPattern.matcher(stripped);

        if (!matcher.find()) {
            return null;
        }

        if (matcher.groupCount() < 1) {
            return matcher.group();
        }

        int start = matcher.start(1);
        int end = matcher.end(1);

        if (start < 0
                || end > originalText.length()
                || start > end) {
            return matcher.group(1);
        }

        return originalText.substring(start, end);
    }

    private Object castDynamicValue(
            String rawValue,
            String dataType
    ) {
        if (rawValue == null) {
            return null;
        }

        String trimmed = rawValue.trim();

        if ("NUMBER".equalsIgnoreCase(dataType)) {
            return parseAmount(trimmed);
        }

        return trimmed;
    }

    private Double parseAmount(String rawAmount) {
        try {
            String clean = rawAmount.replaceAll(
                    "[^0-9]",
                    ""
            );

            return Double.parseDouble(clean);
        } catch (Exception error) {
            return null;
        }
    }
}