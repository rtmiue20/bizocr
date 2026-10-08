package com.company.bizocr.extract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bộ lọc và chuẩn hóa dữ liệu chuyên sâu cho danh thiếp (NAMECARD).
 * Khắc phục triệt để các nhược điểm của mô hình AI nhỏ hoặc OCR:
 * 1. Phân biệt chính xác SĐT cá nhân (di động) vs SĐT công ty (máy bàn) vs Số Fax.
 * 2. Ngăn chặn việc gộp nhiều số điện thoại bằng dấu phẩy.
 * 3. Ngăn chặn việc lấy tên miền website (.vn, .com, Q...) làm tên công ty thay vì tên thương hiệu.
 * 4. Loại bỏ nick chat (Skype, Yahoo ID, Zalo) bị nhận nhầm thành email cá nhân.
 * 5. Khắc phục lỗi OCR dính ký tự lạ sau dấu @ (vd: cskh@Qatoztravel.vn -> cskh@atoztravel.vn).
 */
@Slf4j
public final class NamecardSanitizer {

    // Bắt số điện thoại: di động, máy bàn 02x, tổng đài miễn phí 1800/1900
    private static final Pattern PHONE_CANDIDATE_PATTERN =
            Pattern.compile("(?i)(?:1[89]00[\\s.-]?\\d{4,6}|(?:\\+?84|0)[0-9 \\t.()-]{8,18})");

    // Đầu số di động Việt Nam: 09x, 03x, 07x, 05x, 08[1-9]x (trừ 0838, 0839 là mã bàn HCM cũ)
    private static final Pattern MOBILE_PATTERN =
            Pattern.compile("^(?:\\+84|0)(?:[3579]\\d{8}|8[12456789]\\d{7})$");

    private static final Pattern EMAIL_SYNTAX_PATTERN =
            Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");

    private NamecardSanitizer() {}

    public static ObjectNode sanitize(ObjectNode node, String rawText, ObjectMapper mapper) {
        if (node == null) return null;
        if (rawText == null || rawText.isBlank()) return node;

        String[] lines = rawText.split("\\R");

        // Danh sách domain email miễn phí / cá nhân
        final Set<String> FREE_EMAIL_DOMAINS = Set.of(
                "gmail.com", "yahoo.com", "yahoo.com.vn", "hotmail.com",
                "outlook.com", "icloud.com", "live.com"
        );

        // 1. Phân tách và làm sạch số điện thoại & Fax
        // Bóc tách có nhãn từ rawText
        Pattern mobileLabelPattern = Pattern.compile(
                "(?i)(?:\\b(?:dd|đd|dld|dtdd|đtdđ|mobile|cell|m|hp|handphone|di dong)\\b)[\\s.:/-]*([+0-9\\s.()-]{8,20})"
        );
        Pattern landlineLabelPattern = Pattern.compile(
                "(?i)(?:\\b(?:tel|telephone|phone|office|dt|đt|t|hotline|tong dai|1800|1900)\\b)[\\s.:/-]*([+0-9\\s.()-]{6,20})"
        );
        Pattern faxLabelPattern = Pattern.compile(
                "(?i)(?:\\b(?:fax|f)\\b)[\\s.:/-]*([+0-9\\s.()-]{8,20})"
        );

        String labeledMobile = null;
        String labeledLandline = null;
        String labeledFax = null;

        for (String line : lines) {
            String tl = line.trim();
            Matcher mobM = mobileLabelPattern.matcher(tl);
            if (mobM.find() && labeledMobile == null) labeledMobile = mobM.group(1).trim();

            Matcher landM = landlineLabelPattern.matcher(tl);
            if (landM.find() && labeledLandline == null) labeledLandline = landM.group(1).trim();

            Matcher faxM = faxLabelPattern.matcher(tl);
            if (faxM.find() && labeledFax == null) labeledFax = faxM.group(1).trim();
        }

        List<PhoneCandidate> allPhones = extractAllPhones(lines);
        List<PhoneCandidate> mobiles = new ArrayList<>();
        List<PhoneCandidate> landlines = new ArrayList<>();

        for (PhoneCandidate c : allPhones) {
            if (isMobile(c.clean)) {
                mobiles.add(c);
            } else {
                landlines.add(c);
            }
        }

        String curPhone = textOf(node.get("phone"));
        String curCompPhone = textOf(node.get("company_phone"));
        String curFax = textOf(node.get("fax"));

        // Định tuyến phone (cá nhân): ưu tiên labeledMobile -> mobiles -> curPhone nếu là mobile
        if (labeledMobile != null && !labeledMobile.isBlank()) {
            curPhone = labeledMobile;
        } else if (!mobiles.isEmpty()) {
            curPhone = mobiles.get(0).display;
        } else if (isMobile(curPhone.replaceAll("[^0-9+]", ""))) {
            // Giữ nguyên curPhone
        } else {
            // Không có số di động rõ ràng
            curPhone = "";
        }

        // Định tuyến company_phone: ưu tiên labeledLandline -> landlines -> fallback
        if (labeledLandline != null && !labeledLandline.isBlank()) {
            curCompPhone = labeledLandline;
        } else if (!landlines.isEmpty()) {
            curCompPhone = landlines.get(0).display;
        } else if (!mobiles.isEmpty() && mobiles.size() > 1 && curPhone.equals(mobiles.get(0).display)) {
            // Có số di động thứ hai và chưa có company_phone
            curCompPhone = mobiles.get(1).display;
        }

        // Định tuyến fax
        if (labeledFax != null && !labeledFax.isBlank()) {
            curFax = labeledFax;
        } else if (landlines.size() >= 2) {
            curFax = landlines.get(1).display;
        }

        // Ghi lại số điện thoại vào node
        if (!curPhone.isBlank()) node.put("phone", curPhone);
        if (!curCompPhone.isBlank()) node.put("company_phone", curCompPhone);
        if (!curFax.isBlank()) node.put("fax", curFax);

        // Làm sạch Tax Code (tránh số điện thoại/fax bị gán nhầm thành MST trên namecard)
        String curTax = textOf(node.get("tax_code"));
        if (!curTax.isBlank()) {
            String cleanTax = curTax.replaceAll("[^0-9]", "");
            boolean isPhoneDuplicate = cleanTax.equals(curPhone.replaceAll("[^0-9]", ""))
                    || cleanTax.equals(curCompPhone.replaceAll("[^0-9]", ""))
                    || cleanTax.equals(curFax.replaceAll("[^0-9]", ""));
            boolean hasTaxLabel = rawText.matches("(?is).*(?:MST|Mã số thuế|Tax code|M\\.S\\.T).*");
            if (isPhoneDuplicate || !hasTaxLabel) {
                node.put("tax_code", "");
            }
        }

        // 2. Làm sạch Tên công ty (company_name)
        String curCompany = textOf(node.get("company_name"));
        boolean isDomainLike = curCompany.endsWith(".vn")
                || curCompany.endsWith(".com")
                || curCompany.startsWith("Q")
                || curCompany.startsWith("q")
                || curCompany.startsWith("www.")
                || curCompany.contains("@");

        if (isDomainLike || curCompany.isBlank()) {
            String bestBrand = findBrandName(lines);
            if (bestBrand != null && !bestBrand.isBlank()) {
                node.put("company_name", bestBrand);
            } else if (isDomainLike && !curCompany.isBlank()) {
                String cleanedComp = curCompany.replaceAll("(?i)^[qQ]?(?:www\\.)?", "")
                        .replaceAll("(?i)\\.(?:vn|com|com\\.vn|net|org)$", "")
                        .toUpperCase();
                if (!cleanedComp.isBlank()) {
                    node.put("company_name", cleanedComp);
                }
            }
        }

        // 3. THU THẬP & ĐỊNH TUYẾN EMAIL THỐNG NHẤT (Quy tắc FREE domain vs Domain công ty)
        // Thu thập tất cả email từ node hiện tại và rawText
        List<String> rawEmailList = new ArrayList<>();
        String nEmail = textOf(node.get("email"));
        String nCompEmail = textOf(node.get("company_email"));
        if (!nEmail.isBlank()) rawEmailList.add(nEmail);
        if (!nCompEmail.isBlank() && !rawEmailList.contains(nCompEmail)) rawEmailList.add(nCompEmail);

        // Tìm thêm email từ rawText
        Pattern emailExtractPattern = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
        Matcher emMatcher = emailExtractPattern.matcher(rawText);
        while (emMatcher.find()) {
            String found = emMatcher.group().trim();
            if (!rawEmailList.contains(found)) rawEmailList.add(found);
        }

        // Tìm fuzzy email thiếu @ (vd: user.nomega.com.vn)
        Pattern fuzzyEmailPattern = Pattern.compile(
                "(?i)\\b([a-zA-Z0-9._%+-]+)(?:\\.n|\\.at|\\s*@\\s*|\\s+at\\s+)([a-zA-Z0-9-]+\\.(?:com\\.vn|com|vn|net|org))\\b"
        );
        Matcher feM = fuzzyEmailPattern.matcher(rawText);
        while (feM.find()) {
            String restored = feM.group(1).trim() + "@" + feM.group(2).trim();
            if (EMAIL_SYNTAX_PATTERN.matcher(restored).matches() && !rawEmailList.contains(restored)) {
                rawEmailList.add(restored);
            }
        }

        // Làm sạch và lọc nick chat / rác
        List<String> validEmails = new ArrayList<>();
        for (String rawEm : rawEmailList) {
            String em = rawEm.trim().replaceAll("(?i)@[qQ]", "@");
            if (em.contains("/Skype") || em.contains("/Zalo") || em.contains("/Yahoo")) continue;
            if (!EMAIL_SYNTAX_PATTERN.matcher(em).matches()) continue;
            if (!validEmails.contains(em)) validEmails.add(em);
        }

        // Định tuyến: FREE domain -> person email; Domain riêng -> company_email
        List<String> personEmails = new ArrayList<>();
        List<String> companyEmails = new ArrayList<>();

        for (String em : validEmails) {
            String domain = em.toLowerCase().substring(em.lastIndexOf('@') + 1);
            if (FREE_EMAIL_DOMAINS.contains(domain)) {
                personEmails.add(em);
            } else {
                companyEmails.add(em);
            }
        }

        String finalPersonEmail = personEmails.isEmpty() ? "" : personEmails.get(0);
        String finalCompanyEmail = companyEmails.isEmpty() ? "" : companyEmails.get(0);

        // Khi 2 email cùng loại (vd: 2 email công ty: tammy@dimensions.edu.vn và student@dimensions.edu.vn)
        // Ưu tiên email có tên người làm chính, email chung/thứ 2 cho vào note
        List<String> extraNotes = new ArrayList<>();
        if (personEmails.size() > 1) {
            for (int i = 1; i < personEmails.size(); i++) {
                extraNotes.add("Email cá nhân khác: " + personEmails.get(i));
            }
        }
        if (companyEmails.size() > 1) {
            // Sắp xếp: email gắn tên người (không phải info/contact/student/cskh/admin/support) lên đầu
            String contactName = textOf(node.get("contact_name")).toLowerCase();
            int bestIdx = 0;
            for (int i = 0; i < companyEmails.size(); i++) {
                String ce = companyEmails.get(i).toLowerCase();
                boolean isGeneric = ce.startsWith("info@") || ce.startsWith("contact@")
                        || ce.startsWith("student@") || ce.startsWith("cskh@")
                        || ce.startsWith("sales@") || ce.startsWith("admin@");
                if (!isGeneric) {
                    bestIdx = i;
                    break;
                }
            }
            finalCompanyEmail = companyEmails.get(bestIdx);
            for (int i = 0; i < companyEmails.size(); i++) {
                if (i != bestIdx) {
                    extraNotes.add("Email công ty khác: " + companyEmails.get(i));
                }
            }
        }

        node.put("email", finalPersonEmail);
        node.put("company_email", finalCompanyEmail);

        if (!extraNotes.isEmpty()) {
            String curNote = textOf(node.get("note"));
            String noteAppend = String.join("; ", extraNotes);
            if (curNote.isBlank()) {
                node.put("note", noteAppend);
            } else if (!curNote.contains(noteAppend)) {
                node.put("note", curNote + " (" + noteAppend + ")");
            }
        }

        // 5. Làm sạch Website
        String curWeb = textOf(node.get("website"));
        if (!curWeb.isBlank()) {
            curWeb = curWeb.replaceAll("(?i)www\\.[a-z]*w{1,3}", "www.")
                           .replaceAll("(?i)www\\.+", "www.");
            node.put("website", curWeb);
        } else {
            Pattern webP = Pattern.compile("(?i)\\b(www\\.[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}|https?://[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})\\b");
            Matcher webM = webP.matcher(rawText);
            if (webM.find()) {
                String foundWeb = webM.group(1).replaceAll("(?i)www\\.[a-z]*w{1,3}", "www.")
                                               .replaceAll("(?i)www\\.+", "www.");
                node.put("website", foundWeb);
            }
        }

        return node;
    }

    private static String findBrandName(String[] lines) {
        for (int i = 0; i < Math.min(lines.length, 3); i++) {
            String line = lines[i].trim();
            String upper = line.toUpperCase();
            if (upper.length() >= 2 && upper.length() <= 35) {
                // Bỏ qua dòng chức vụ, tên người hoặc website/email/địa chỉ
                if (upper.contains("GIÁM ĐỐC")
                        || upper.contains("GIAM DOC")
                        || upper.contains("MANAGER")
                        || upper.contains("DIRECTOR")
                        || upper.contains("NGUYỄN")
                        || upper.contains("TRẦN")
                        || upper.contains("LÊ")
                        || upper.contains("PHẠM")
                        || upper.contains("@")
                        || upper.contains(".VN")
                        || upper.contains(".COM")
                        || upper.contains("QUẬN")
                        || upper.contains("TP.")
                        || upper.contains("HỒ CHÍ MINH")
                        || upper.contains("HO CHI MINH")
                        || upper.contains("ĐỒNG HÀNH")) {
                    continue;
                }
                return line;
            }
        }
        return null;
    }

    private static boolean isMobile(String cleanDigits) {
        if (cleanDigits == null) return false;
        String s = cleanDigits;
        if (s.startsWith("+84") && s.length() >= 11) {
            s = "0" + s.substring(3);
        } else if (s.startsWith("84") && s.length() >= 11) {
            s = "0" + s.substring(2);
        }
        // Loại trừ mã vùng cố định TP.HCM cũ (0838..., 0839...)
        if (s.startsWith("0838") || s.startsWith("0839")) {
            return false;
        }
        return MOBILE_PATTERN.matcher(s).matches();
    }

    private record PhoneCandidate(String display, String clean) {}

    private static List<PhoneCandidate> extractAllPhones(String[] lines) {
        List<PhoneCandidate> list = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (String line : lines) {
            Matcher m = PHONE_CANDIDATE_PATTERN.matcher(line);
            while (m.find()) {
                String raw = m.group().trim();
                String clean = raw.replaceAll("[^0-9+]", "");
                if (clean.length() >= 9 && clean.length() <= 12) {
                    if (seen.add(clean)) {
                        list.add(new PhoneCandidate(raw, clean));
                    }
                }
            }
        }
        return list;
    }

    private static String textOf(JsonNode n) {
        return (n == null || n.isNull()) ? "" : n.asText().trim();
    }
}
