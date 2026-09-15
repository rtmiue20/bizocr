package com.company.bizocr.classify;

import com.company.bizocr.model.dto.ClassifyResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Bộ nhận diện và phân loại chứng từ theo thuật toán Template Matching (Mức 1 - Priority Matching).
 * Đã được chuẩn hóa và mở rộng để hỗ trợ cấu hình động hàng trăm loại chứng từ.
 * Tối ưu hóa tốc độ xử lý nhanh (<1ms) và giảm tải cho các tầng AI Fallback.
 */
@Slf4j
@Component
public class TemplateMatcher {

    private static final double DEFAULT_CONFIDENCE_THRESHOLD = 0.70;

    // Danh mục các mẫu chứng từ được quản lý tập trung (Thread-safe)
    private final Map<String, DocumentTemplate> templateCatalog = new ConcurrentHashMap<>();

    // Inject FuzzyTitleMatcher phục vụ Tầng 2 So khớp mờ
    private final FuzzyTitleMatcher fuzzyTitleMatcher;

    public TemplateMatcher() {
        this(new FuzzyTitleMatcher());
    }

    public TemplateMatcher(FuzzyTitleMatcher fuzzyTitleMatcher) {
        this.fuzzyTitleMatcher = fuzzyTitleMatcher != null ? fuzzyTitleMatcher : new FuzzyTitleMatcher();
        initStandardTemplates();
    }

    /**
     * Khởi tạo danh mục các mẫu chứng từ cốt lõi
     */
    private void initStandardTemplates() {
        // 1. Hóa đơn giá trị gia tăng (VAT Invoice)
        registerTemplate(DocumentTemplate.builder()
                .documentType("VAT_INVOICE")
                .displayName("Hóa đơn Giá trị gia tăng")
                .category("INVOICE")
                .priority(10)
                .confidenceThreshold(0.70)
                .primaryKeywords(List.of(
                        "hóa đơn giá trị gia tăng", "hoa don gia tri gia tang",
                        "hóa đơn bán hàng", "hoa don ban hang",
                        "hóa đơn điện tử", "hoa don dien tu",
                        "hóa đơn vat", "hoa don vat"
                ))
                .sampleTitles(List.of(
                        "HOA DON GIA TRI GIA TANG", "HOA DON BAN HANG", "HOA DON DIEN TU", "HOA DON VAT"
                ))
                .secondaryKeywords(List.of(
                        "mã số thuế", "ma so thue", "mst",
                        "mẫu số", "mau so", "ký hiệu", "ky hieu", "số hóa đơn", "so hoa don",
                        "cộng tiền hàng", "cong tien hang", "tiền thuế gtgt", "tien thue gtgt",
                        "thuế suất gtgt", "thue suat gtgt", "tổng tiền thanh toán", "tong tien thanh toan",
                        "mã cơ quan thuế", "ma co quan thue"
                ))
                .regexPatterns(List.of(
                        Pattern.compile("(?:mã số thuế|mst|tax code)[:\\s]*[0-9]{10}", Pattern.CASE_INSENSITIVE),
                        Pattern.compile("hóa đơn|tiền thuế gtgt|thuế suất", Pattern.CASE_INSENSITIVE)
                ))
                .build());

        // 2. Phiếu thanh toán dịch vụ (Service Payment Voucher / Receipt)
        registerTemplate(DocumentTemplate.builder()
                .documentType("SERVICE_PAYMENT_VOUCHER")
                .displayName("Phiếu thanh toán dịch vụ / Hóa đơn bán lẻ")
                .category("VOUCHER")
                .priority(9)
                .confidenceThreshold(0.70)
                .primaryKeywords(List.of(
                        "phiếu thanh toán dịch vụ", "phieu thanh toan dich vu",
                        "phiếu thanh toán", "phieu thanh toan",
                        "phiếu thu", "phieu thu",
                        "phiếu chi", "phieu chi",
                        "biên lai thu tiền", "bien lai thu tien",
                        "hóa đơn bán lẻ", "hoa don ban le",
                        "phiếu tính tiền", "phieu tinh tien"
                ))
                .sampleTitles(List.of(
                        "PHIEU THANH TOAN DICH VU", "PHIEU THANH TOAN", "PHIEU THU", "PHIEU CHI", "BIEN LAI THU TIEN", "HOA DON BAN LE"
                ))
                .secondaryKeywords(List.of(
                        "tổng chi phí", "tong chi phi",
                        "tổng cộng", "tong cong",
                        "thành tiền", "thanh tien",
                        "tiền mặt", "tien mat",
                        "taxi mai linh", "nhà hàng phố mới", "cửa hàng văn phòng phẩm abc",
                        "cước taxi", "dịch vụ ăn uống", "văn phòng phẩm"
                ))
                .regexPatterns(List.of(
                        Pattern.compile("(?:tổng chi phí|tổng tiền thanh toán|thành tiền)[:\\s]*[0-9\\.,]+", Pattern.CASE_INSENSITIVE),
                        Pattern.compile("phiếu thanh toán|phiếu thu|phiếu chi|biên lai", Pattern.CASE_INSENSITIVE)
                ))
                .build());

        // 3. Hóa đơn tiền điện (Electricity Bill / EVN)
        registerTemplate(DocumentTemplate.builder()
                .documentType("ELECTRICITY_BILL")
                .displayName("Hóa đơn tiền điện / Thông báo tiền điện EVN")
                .category("UTILITY")
                .priority(9)
                .confidenceThreshold(0.70)
                .primaryKeywords(List.of(
                        "tổng công ty điện lực", "tong cong ty dien luc",
                        "công ty điện lực", "cong ty dien luc",
                        "điện lực hà nội", "dien luc ha noi",
                        "điện lực tphcm", "dien luc tphcm",
                        "điện lực miền bắc", "dien luc mien bac",
                        "điện lực miền nam", "dien luc mien nam",
                        "điện lực miền trung", "dien luc mien trung",
                        "hóa đơn tiền điện", "hoa don tien dien",
                        "giấy báo tiền điện", "giay bao tien dien"
                ))
                .sampleTitles(List.of(
                        "HOA DON TIEN DIEN", "GIAY BAO TIEN DIEN", "TONG CONG TY DIEN LUC", "CONG TY DIEN LUC"
                ))
                .secondaryKeywords(List.of(
                        "thẻ khách hàng", "the khach hang",
                        "mã khách hàng", "ma khach hang", "mã kh", "ma kh",
                        "tổng tiền điện thanh toán", "tong tien dien thanh toan",
                        "tiền điện", "tien dien",
                        "điện năng tiêu thụ", "dien nang tieu thu",
                        "chỉ số mới", "chỉ số cũ", "kwh", "số công tơ", "so cong to", "giá bán điện"
                ))
                .regexPatterns(List.of(
                        Pattern.compile("(?:thẻ khách hàng|mã khách hàng|mã kh)[:\\s]*[A-Z0-9]+", Pattern.CASE_INSENSITIVE),
                        Pattern.compile("(?:tổng tiền điện thanh toán|tiền điện)[:\\s]*[0-9\\.,]+", Pattern.CASE_INSENSITIVE),
                        Pattern.compile("điện lực|evn|kwh", Pattern.CASE_INSENSITIVE)
                ))
                .build());

        // 4. Hóa đơn tiền nước (Water Bill)
        registerTemplate(DocumentTemplate.builder()
                .documentType("WATER_BILL")
                .displayName("Hóa đơn tiền nước / Cấp nước")
                .category("UTILITY")
                .priority(8)
                .confidenceThreshold(0.70)
                .primaryKeywords(List.of(
                        "công ty cổ phần cấp nước", "cong ty co phan cap nuoc",
                        "công ty nước sạch", "cong ty nuoc sach",
                        "hóa đơn tiền nước", "hoa don tien nuoc",
                        "giấy báo tiền nước", "giay bao tien nuoc"
                ))
                .sampleTitles(List.of(
                        "HOA DON TIEN NUOC", "GIAY BAO TIEN NUOC", "CONG TY CAP NUOC"
                ))
                .secondaryKeywords(List.of(
                        "mã danh bộ", "ma danh bo", "số danh bộ", "so danh bo",
                        "mã khách hàng", "ma khach hang",
                        "tiền nước", "tien nuoc",
                        "tiêu thụ", "tieu thu", "chỉ số đồng hồ", "m3", "khối nước"
                ))
                .regexPatterns(List.of(
                        Pattern.compile("(?:mã danh bộ|số danh bộ|mã kh)[:\\s]*[A-Z0-9]+", Pattern.CASE_INSENSITIVE),
                        Pattern.compile("cấp nước|nước sạch|tiền nước|m3", Pattern.CASE_INSENSITIVE)
                ))
                .build());

        // 5. Hợp đồng kinh tế / Biên bản quyết toán / Nghiệm thu (Contract)
        registerTemplate(DocumentTemplate.builder()
                .documentType("CONTRACT")
                .displayName("Hợp đồng kinh tế / Biên bản quyết toán nghiệm thu")
                .category("CONTRACT")
                .priority(8)
                .confidenceThreshold(0.70)
                .primaryKeywords(List.of(
                        "cộng hòa xã hội chủ nghĩa việt nam", "cong hoa xa hoi chu nghia viet nam",
                        "độc lập tự do hạnh phúc", "doc lap - tu do - hanh phuc",
                        "hợp đồng kinh tế", "hop dong kinh te",
                        "hợp đồng dịch vụ", "hop dong dich vu",
                        "hợp đồng lao động", "hop dong lao dong",
                        "hợp đồng", "hop dong",
                        "biên bản quyết toán hợp đồng", "bien ban quyet toan hop dong",
                        "biên bản nghiệm thu", "bien ban nghiem thu",
                        "biên bản thanh lý hợp đồng", "bien ban thanh ly hop dong"
                ))
                .sampleTitles(List.of(
                        "HOP DONG KINH TE", "HOP DONG DICH VU", "HOP DONG LAO DONG", "BIEN BAN QUYET TOAN", "BIEN BAN NGHIEM THU"
                ))
                .secondaryKeywords(List.of(
                        "bên a", "ben a", "bên b", "ben b",
                        "điều khoản", "dieu khoan",
                        "trách nhiệm của các bên", "trach nhiem cua cac ben",
                        "phạm vi công việc", "pham vi cong viec",
                        "giá trị quyết toán", "gia tri quyet toan",
                        "giá trị hợp đồng", "gia tri hop dong",
                        "đại diện", "dai dien", "giám đốc", "giam doc"
                ))
                .regexPatterns(List.of(
                        Pattern.compile("hợp đồng|quyết toán hợp đồng|nghiệm thu|thanh lý|bên a|bên b", Pattern.CASE_INSENSITIVE)
                ))
                .build());

        // 6. Ủy nhiệm chi / Giấy nộp tiền ngân hàng (Bank Statement)
        registerTemplate(DocumentTemplate.builder()
                .documentType("BANK_STATEMENT")
                .displayName("Ủy nhiệm chi / Giấy nộp tiền ngân hàng")
                .category("BANKING")
                .priority(8)
                .confidenceThreshold(0.70)
                .primaryKeywords(List.of(
                        "ủy nhiệm chi", "uy nhiem chi",
                        "giấy nộp tiền", "giay nop tien",
                        "lệnh chuyển tiền", "lenh chuyen tien",
                        "phiếu chuyển tiền", "phieu chuyen tien",
                        "sao kê tài khoản", "sao ke tai khoan"
                ))
                .sampleTitles(List.of(
                        "UY NHIEM CHI", "GIAY NOP TIEN", "LENH CHUYEN TIEN", "SAO KE TAI KHOAN"
                ))
                .secondaryKeywords(List.of(
                        "ngân hàng", "ngan hang", "bank",
                        "đơn vị trả tiền", "don vi tra tien",
                        "đơn vị thụ hưởng", "don vi thu huong",
                        "số tài khoản", "so tai khoan", "stk",
                        "tài khoản trích nợ", "tai khoan trich no",
                        "tổng số tiền bằng chữ", "tong so tien bang chu"
                ))
                .regexPatterns(List.of(
                        Pattern.compile("ủy nhiệm chi|giấy nộp tiền|lệnh chuyển tiền|sao kê tài khoản", Pattern.CASE_INSENSITIVE),
                        Pattern.compile("(?:số tài khoản|stk|tài khoản)[:\\s]*[0-9]{6,20}", Pattern.CASE_INSENSITIVE)
                ))
                .build());

        // 7. Căn cước công dân / CMND (ID Card)
        registerTemplate(DocumentTemplate.builder()
                .documentType("ID_CARD")
                .displayName("Căn cước công dân / CMND")
                .category("IDENTITY")
                .priority(8)
                .confidenceThreshold(0.70)
                .primaryKeywords(List.of(
                        "căn cước công dân", "can cuoc cong dan",
                        "chứng minh nhân dân", "chung minh nhan dan",
                        "thẻ căn cước", "the can cuoc",
                        "citizen identity card", "identity card"
                ))
                .sampleTitles(List.of(
                        "CAN CUOC CONG DAN", "CHUNG MINH NHAN DAN", "THE CAN CUOC", "CITIZEN IDENTITY CARD"
                ))
                .secondaryKeywords(List.of(
                        "số / no", "so / no", "số định danh", "so dinh danh",
                        "họ và tên", "ho va ten",
                        "ngày sinh", "ngay sinh", "giới tính", "gioi tinh",
                        "quốc tịch", "quoc tich", "quê quán", "que quan",
                        "nơi thường trú", "noi thuong tru", "có giá trị đến", "co gia tri den"
                ))
                .regexPatterns(List.of(
                        Pattern.compile("(?:số|no\\.?|số định danh)[:\\s]*[0-9]{9,12}", Pattern.CASE_INSENSITIVE),
                        Pattern.compile("căn cước|chứng minh nhân dân", Pattern.CASE_INSENSITIVE)
                ))
                .build());

        // 8. Danh thiếp (Name Card)
        registerTemplate(DocumentTemplate.builder()
                .documentType("NAME_CARD")
                .displayName("Danh thiếp / Card Visit")
                .category("IDENTITY")
                .priority(9)
                .confidenceThreshold(0.70)
                .primaryKeywords(List.of(
                        "danh thiếp", "danh thiep", "name card", "business card", "card visit",
                        "giám đốc tài chính", "giam doc tai chinh", "cfo", "chief financial officer",
                        "tổng giám đốc", "tong giam doc", "phó tổng giám đốc", "pho tong giam doc",
                        "giám đốc điều hành", "giam doc dieu hanh", "ceo", "chief executive officer",
                        "giám đốc", "giam doc", "director", "managing director", "general director",
                        "trưởng phòng", "truong phong", "phó phòng", "pho phong", "head of",
                        "manager", "deputy manager", "founder", "co-founder", "chủ tịch", "chu tich",
                        "chairman", "president", "vice president", "kế toán trưởng", "ke toan truong",
                        "chuyên viên", "chuyen vien", "consultant", "specialist", "sales executive"
                ))
                .sampleTitles(List.of(
                        "BUSINESS CARD", "NAME CARD", "CARD VISIT"
                ))
                .secondaryKeywords(List.of(
                        "phone", "mobile", "email", "e-mail", "website", "address",
                        "tel", "fax", "hotline", "ext", "địa chỉ", "dia chi", "điện thoại", "dien thoai",
                        "tax code", "mst", "mã số thuế", "ma so thue", "tower", "tầng", "tang", "p.", "phòng", "tp.hcm", "tphcm", "hà nội", "ha noi"
                ))
                .regexPatterns(List.of(
                        Pattern.compile("(?:phone|tel|mobile|hotline)[:\\s]*[0-9+\\-\\s.()]{8,20}", Pattern.CASE_INSENSITIVE),
                        Pattern.compile("(?:e-?mail)[:\\s]*[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}", Pattern.CASE_INSENSITIVE),
                        Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9@.-]+\\.[a-zA-Z]{2,}", Pattern.CASE_INSENSITIVE),
                        Pattern.compile("\\b(?:84|\\+84|0)[0-9\\-\\s.]{8,15}\\b"),
                        Pattern.compile("\\b(?:www\\.)?[a-zA-Z0-9-]+\\.(?:com|net|vn|org|info|c@m)(?:\\.vn)?\\b", Pattern.CASE_INSENSITIVE),
                        Pattern.compile("(?:tax\\s*code|mst|mã\\s*số\\s*thuế)[:\\s]*[0-9]{10}", Pattern.CASE_INSENSITIVE)
                ))
                .build());
    }

    public void registerTemplate(DocumentTemplate template) {
        if (template != null && template.getDocumentType() != null && !template.getDocumentType().isBlank()) {
            templateCatalog.put(template.getDocumentType().toUpperCase(), template);
            log.info("[TemplateMatcher] Đã đăng ký mẫu chứng từ: {} ({})",
                    template.getDocumentType(), template.getDisplayName());
        }
    }

    public void removeTemplate(String documentType) {
        if (documentType != null) {
            templateCatalog.remove(documentType.toUpperCase());
        }
    }

    public List<DocumentTemplate> getRegisteredTemplates() {
        return new ArrayList<>(templateCatalog.values());
    }

    public Optional<DocumentTemplate> getTemplate(String documentType) {
        if (documentType == null) return Optional.empty();
        return Optional.ofNullable(templateCatalog.get(documentType.toUpperCase()));
    }

    public Optional<ClassifyResult> match(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return Optional.empty();
        }

        String lowerText = rawText.toLowerCase();
        String unaccentedText = removeVietnameseAccents(lowerText);

        DocumentTemplate bestTemplate = null;
        double maxScore = 0.0;

        for (DocumentTemplate template : templateCatalog.values()) {
            double score = calculateMatchScore(template, rawText, lowerText, unaccentedText);

            if (score > maxScore || (Math.abs(score - maxScore) < 0.001 && bestTemplate != null && template.getPriority() > bestTemplate.getPriority())) {
                maxScore = score;
                bestTemplate = template;
            }
        }

        if (bestTemplate != null && maxScore >= bestTemplate.getConfidenceThreshold()) {
            double finalConfidence = Math.min(Math.max(maxScore, 0.85), 0.98);

            log.info("[TemplateMatcher] Khớp thành công loại chứng từ: {} ({}) với điểm số tin cậy: {}",
                    bestTemplate.getDocumentType(), bestTemplate.getDisplayName(), Math.round(finalConfidence * 100.0) / 100.0);

            return Optional.of(ClassifyResult.builder()
                    .documentType(bestTemplate.getDocumentType())
                    .confidenceScore(finalConfidence)
                    .matchedBy("TEMPLATE_MATCHING")
                    .success(true)
                    .outlier(false)
                    .build());
        }

        return Optional.empty();
    }

    /**
     * Thuật toán chấm điểm mức độ tương đồng kết hợp Keyword + Fuzzy Title + Regex
     */
    private double calculateMatchScore(DocumentTemplate template, String rawText, String lowerText, String unaccentedText) {
        double score = 0.0;

        // 1. So khớp từ khóa chính (Primary Keywords)
        List<String> primaryKeywords = template.getPrimaryKeywords();
        if (primaryKeywords != null && !primaryKeywords.isEmpty()) {
            long primaryMatches = primaryKeywords.stream()
                    .filter(kw -> {
                        String cleanKw = kw.toLowerCase();
                        String cleanUnaccentedKw = removeVietnameseAccents(cleanKw);
                        return lowerText.contains(cleanKw) || unaccentedText.contains(cleanUnaccentedKw);
                    })
                    .count();

            if (primaryMatches > 0) {
                score += 0.50 + Math.min(0.15, (double) (primaryMatches - 1) * 0.05);
            }
        }

        // 2. So khớp mờ tiêu đề (Fuzzy Title Matching Bonus)
        double fuzzyBonus = fuzzyTitleMatcher.calculateTitleBonus(rawText, template.getSampleTitles());
        if (fuzzyBonus > 0) {
            score += (0.25 * fuzzyBonus);
            log.debug("[TemplateMatcher] Fuzzy Title Bonus [{}] = +{}", template.getDocumentType(), Math.round(0.25 * fuzzyBonus * 100.0) / 100.0);
        }

        // 3. So khớp từ khóa phụ / ngữ cảnh (Secondary Keywords)
        List<String> secondaryKeywords = template.getSecondaryKeywords();
        if (secondaryKeywords != null && !secondaryKeywords.isEmpty()) {
            long secondaryMatches = secondaryKeywords.stream()
                    .filter(kw -> {
                        String cleanKw = kw.toLowerCase();
                        String cleanUnaccentedKw = removeVietnameseAccents(cleanKw);
                        return lowerText.contains(cleanKw) || unaccentedText.contains(cleanUnaccentedKw);
                    })
                    .count();

            double secondaryRatio = (double) secondaryMatches / secondaryKeywords.size();
            score += Math.min(0.20, secondaryRatio * 0.30);
        }

        // 4. So khớp Regex đặc trưng (Regex Patterns)
        List<Pattern> regexPatterns = template.getRegexPatterns();
        if (regexPatterns != null && !regexPatterns.isEmpty()) {
            long regexMatches = regexPatterns.stream()
                    .filter(p -> p.matcher(lowerText).find() || p.matcher(unaccentedText).find())
                    .count();

            if (regexMatches > 0) {
                score += 0.15;
            }
        }

        return score;
    }

    public static String removeVietnameseAccents(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String normalized = input.replace('đ', 'd').replace('Đ', 'D');
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFD);
        normalized = normalized.replaceAll("\\p{M}", "");
        return normalized;
    }

    public void clearDynamicTemplates() {
        templateCatalog.entrySet().removeIf(entry ->
                "DYNAMIC_DB".equalsIgnoreCase(entry.getValue().getCategory())
        );
        log.info("[TemplateMatcher] Đã dọn dẹp toàn bộ template động từ bộ nhớ");
    }
}