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

        } else if ("NAMECARD".equalsIgnoreCase(documentType)
                || "NAME_CARD".equalsIgnoreCase(documentType)
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

        // 1. Mã số thuế (Chỉ lấy khi có nhãn rõ ràng như MST, Mã số thuế)
        List<String> taxCandidates =
                extractTaxCodeCandidates(text, true);

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
                        || upper.contains("LIMITED")
                        || upper.contains("TRAVEL")
                        || upper.contains("LOGISTICS")
                        || upper.contains("SOLUTIONS")
                        || upper.contains("TECHNOLOGY")) {

                    fields.put("company_name", trimmed);
                    break;
                }
            }
        }

        // Nếu vẫn chưa có tên công ty, tìm thương hiệu ở các dòng đầu (vd: "Omega", "ATOZ")
        if (!fields.containsKey("company_name")) {
            for (int i = 0; i < Math.min(lines.length, 3); i++) {
                String trimmed = lines[i].trim();
                String upper = trimmed.toUpperCase();
                if (upper.length() >= 2 && upper.length() <= 35) {
                    if (!upper.contains("GIÁM ĐỐC")
                            && !upper.contains("GIAM DOC")
                            && !upper.contains("MANAGER")
                            && !upper.contains("DIRECTOR")
                            && !upper.contains("NGUYỄN")
                            && !upper.contains("TRẦN")
                            && !upper.contains("LÊ")
                            && !upper.contains("PHẠM")
                            && !upper.contains("@")
                            && !upper.contains(".VN")
                            && !upper.contains(".COM")
                            && !upper.contains("QUẬN")
                            && !upper.contains("TP.")
                            && !upper.contains("HỒ CHÍ MINH")
                            && !upper.contains("HO CHI MINH")
                            && !upper.contains("ĐỒNG HÀNH")) {
                        fields.put("company_name", trimmed);
                        break;
                    }
                }
            }
        }

        // 5. Số điện thoại & Fax
        String mobileLabeled = null;
        String landlineLabeled = null;
        String faxLabeled = null;

        Pattern mobileLabelPattern = Pattern.compile("(?i)(?:\\b(?:dd|đd|dld|dtdd|đtdđ|mobile|cell|hp|handphone)\\b|\\bdd\\b|\\bđd\\b)[\\s.:/-]*([+0-9\\s.()-]{8,20})");
        Pattern landlineLabelPattern = Pattern.compile("(?i)(?:\\b(?:tel|phone|office|dt|đt)\\b)[\\s.:/-]*([+0-9\\s.()-]{8,20})");
        Pattern faxLabelPattern = Pattern.compile("(?i)(?:\\b(?:fax|f)\\b)[\\s.:/-]*([+0-9\\s.()-]{8,20})");

        Pattern anyPhonePattern = Pattern.compile("(?i)(?:\\+?84|0)[0-9 \\t.()-]{8,18}");
        List<String> mobileList = new ArrayList<>();
        List<String> landlineList = new ArrayList<>();

        for (String line : lines) {
            String trimmedLine = line.trim();

            Matcher mobM = mobileLabelPattern.matcher(trimmedLine);
            if (mobM.find() && mobileLabeled == null) {
                mobileLabeled = mobM.group(1).trim();
            }

            Matcher landM = landlineLabelPattern.matcher(trimmedLine);
            if (landM.find() && landlineLabeled == null) {
                landlineLabeled = landM.group(1).trim();
            }

            Matcher faxM = faxLabelPattern.matcher(trimmedLine);
            if (faxM.find() && faxLabeled == null) {
                faxLabeled = faxM.group(1).trim();
            }

            Matcher m = anyPhonePattern.matcher(trimmedLine);
            while (m.find()) {
                String raw = m.group().trim();
                String clean = raw.replaceAll("[^0-9+]", "");
                if (clean.length() >= 9 && clean.length() <= 12) {
                    String normClean = clean;
                    if (clean.startsWith("+84") && clean.length() >= 11) {
                        normClean = "0" + clean.substring(3);
                    } else if (clean.startsWith("84") && clean.length() >= 11) {
                        normClean = "0" + clean.substring(2);
                    }
                    boolean isMob = normClean.matches("^(?:0[3579]\\d{8}|08[12456789]\\d{7})$");
                    if (isMob) {
                        if (!mobileList.contains(raw)) mobileList.add(raw);
                    } else {
                        if (!landlineList.contains(raw)) landlineList.add(raw);
                    }
                }
            }
        }

        // 5b. Gán vào phone (SĐT cá nhân)
        String assignedMobile = null;
        if (mobileLabeled != null) {
            assignedMobile = mobileLabeled;
            fields.put("phone", mobileLabeled);
        } else if (!mobileList.isEmpty()) {
            assignedMobile = mobileList.get(0);
            fields.put("phone", assignedMobile);
        }

        // 5c. Gán vào company_phone (SĐT công ty)
        List<String> remainingLandlines = new ArrayList<>(landlineList);
        List<String> remainingMobiles = new ArrayList<>(mobileList);
        if (assignedMobile != null) {
            remainingMobiles.remove(assignedMobile);
            String cleanAssigned = assignedMobile.replaceAll("[^0-9]", "");
            remainingMobiles.removeIf(mStr -> mStr.replaceAll("[^0-9]", "").equals(cleanAssigned));
        }

        if (landlineLabeled != null) {
            fields.put("company_phone", landlineLabeled);
        } else if (!remainingLandlines.isEmpty()) {
            fields.put("company_phone", remainingLandlines.remove(0));
        } else if (!remainingMobiles.isEmpty()) {
            fields.put("company_phone", remainingMobiles.remove(0));
        }

        // 5d. Gán vào fax
        if (faxLabeled != null) {
            fields.put("fax", faxLabeled);
        } else if (!remainingLandlines.isEmpty()) {
            fields.put("fax", remainingLandlines.remove(0));
        } else if (!remainingMobiles.isEmpty()) {
            fields.put("fax", remainingMobiles.remove(0));
        }

        // 6. Email (Cá nhân & Công ty)
        Pattern emailExtractPattern = Pattern.compile(
                "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"
        );
        Matcher allEmailMatcher = emailExtractPattern.matcher(text);
        List<String> foundEmails = new ArrayList<>();
        while (allEmailMatcher.find()) {
            String em = sanitizeEmail(allEmailMatcher.group(0));
            if (em != null && !em.isBlank() && !foundEmails.contains(em)) {
                foundEmails.add(em);
            }
        }

        // Nhận diện email bị lỗi OCR thiếu dấu @ (vd: vannhan.nomega.com.vn -> vannhan@omega.com.vn)
        Pattern fuzzyEmailPattern = Pattern.compile(
                "(?i)\\b([a-zA-Z0-9._%+-]+)(?:\\.n|\\.o|\\.at|\\s*@\\s*|\\s+at\\s+)([a-zA-Z0-9-]+\\.(?:com\\.vn|com|vn|net|org))\\b"
        );
        Matcher fuzzyEmailMatcher = fuzzyEmailPattern.matcher(text);
        while (fuzzyEmailMatcher.find()) {
            String reconstructed = fuzzyEmailMatcher.group(1).trim() + "@" + fuzzyEmailMatcher.group(2).trim();
            String em = sanitizeEmail(reconstructed);
            if (em != null && !em.isBlank() && !foundEmails.contains(em)) {
                foundEmails.add(em);
            }
        }

        Pattern emailPrefixPattern = Pattern.compile(
                "(?im)^[\\t ]*(?:E-?mail)[\\t ]*[:：]?[\\t ]*([^\\r\\n]+)"
        );
        Matcher emailPrefixMatcher = emailPrefixPattern.matcher(text);
        if (emailPrefixMatcher.find()) {
            String rawEmail = emailPrefixMatcher.group(1).trim();
            String sanitized = sanitizeEmail(rawEmail);
            if (sanitized != null && sanitized.contains("@") && !foundEmails.contains(sanitized)) {
                foundEmails.add(0, sanitized);
            }
        }

        for (String em : foundEmails) {
            String lower = em.toLowerCase();
            boolean isCompanyGeneric = lower.startsWith("info@")
                    || lower.startsWith("contact@")
                    || lower.startsWith("support@")
                    || lower.startsWith("sales@")
                    || lower.startsWith("office@")
                    || lower.startsWith("admin@")
                    || lower.startsWith("service@")
                    || lower.startsWith("cskh@")
                    || lower.startsWith("cty@")
                    || lower.startsWith("company@");

            if (isCompanyGeneric) {
                if (!fields.containsKey("company_email")) {
                    fields.put("company_email", em);
                }
            } else {
                if (!fields.containsKey("email")) {
                    fields.put("email", em);
                } else if (!fields.containsKey("company_email")) {
                    fields.put("company_email", em);
                }
            }
        }

        // 7. Website
        Pattern websiteRegex = Pattern.compile(
                "(?i)\\b(www\\.[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}|https?://[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})\\b"
        );
        Matcher webMatcher = websiteRegex.matcher(text);
        if (webMatcher.find()) {
            String rawWeb = webMatcher.group(1).trim();
            String cleanedWeb = sanitizeWebsite(rawWeb);
            if (cleanedWeb != null && !cleanedWeb.isBlank()) {
                cleanedWeb = cleanedWeb.replaceAll("(?i)www\\.[a-z]*w{1,3}", "www.")
                                       .replaceAll("(?i)www\\.+", "www.");
                fields.put("website", cleanedWeb);
            }
        } else {
            Pattern websitePrefixPattern = Pattern.compile(
                    "(?im)^[\\t ]*Website[\\t ]*[:：]?[\\t ]*([^\\r\\n]+)"
            );
            Matcher websitePrefixMatcher = websitePrefixPattern.matcher(text);
            if (websitePrefixMatcher.find()) {
                String rawWebsite = websitePrefixMatcher.group(1).trim();
                String sanitizedWebsite = sanitizeWebsite(rawWebsite);
                if (sanitizedWebsite != null && !sanitizedWebsite.isBlank()) {
                    fields.put("website", sanitizedWebsite);
                }
            }
        }

        // 8. Địa chỉ
        String addrDetail = null;
        String addrGeneral = null;
        for (String line : lines) {
            String trimmed = line.trim();
            String normalized = stripAccents(trimmed).toLowerCase();

            if (normalized.contains("dia chi:") || normalized.contains("dia chre") || normalized.contains("address:")
                    || normalized.matches(".*\\d+[/-][\\d\\w/-]+.*")) {
                String cleanDetail = trimmed.replaceAll("(?i)^[\\(\\s]*(?:dia chi|address|dia chre)[\t :：]*", "")
                                           .replaceAll("[\\)\\s]+$", "")
                                           .trim();
                if (addrDetail == null || cleanDetail.length() > addrDetail.length()) {
                    addrDetail = cleanDetail;
                }
            } else if (normalized.contains("quan ") || normalized.contains("tp.hcm") || normalized.contains("ho chi minh")
                    || normalized.contains("ha noi") || normalized.startsWith("tang ") || normalized.startsWith("so ")) {
                if (addrGeneral == null) {
                    addrGeneral = trimmed;
                }
            }
        }

        if (addrDetail != null && addrGeneral != null && !addrDetail.contains(addrGeneral)) {
            fields.put("address", addrDetail + ", " + addrGeneral);
        } else if (addrDetail != null) {
            fields.put("address", addrDetail);
        } else if (addrGeneral != null) {
            fields.put("address", addrGeneral);
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
        return extractTaxCodeCandidates(text, false);
    }

    public List<String> extractTaxCodeCandidates(String text, boolean labeledOnly) {
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

        if (!candidates.isEmpty() || labeledOnly) {
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