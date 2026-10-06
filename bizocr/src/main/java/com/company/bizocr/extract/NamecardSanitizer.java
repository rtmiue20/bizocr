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

    private static final Pattern PHONE_CANDIDATE_PATTERN =
            Pattern.compile("(?i)(?:\\+?84|0)[0-9 \\t.()-]{8,18}");

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

        // 1. Phân tách và làm sạch số điện thoại & Fax
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

        // Nếu phone hiện tại bị gộp dấu phẩy hoặc chứa nhiều số (vd: "08 3847 6888, 08 3847 9080")
        if (curPhone.contains(",") || curPhone.contains(";") || curPhone.split("\\s{2,}").length > 1) {
            curPhone = "";
        }

        // Nếu phone hiện tại là số máy bàn trong khi ta có số di động thật
        String curPhoneClean = curPhone.replaceAll("[^0-9+]", "");
        if (!isMobile(curPhoneClean) && !mobiles.isEmpty()) {
            curPhone = mobiles.get(0).display;
        } else if (curPhone.isBlank() && !mobiles.isEmpty()) {
            curPhone = mobiles.get(0).display;
        }

        // Xử lý company_phone
        if (curCompPhone.isBlank() && !landlines.isEmpty()) {
            curCompPhone = landlines.get(0).display;
        } else if (!landlines.isEmpty()) {
            String compClean = curCompPhone.replaceAll("[^0-9+]", "");
            String phoneClean = curPhone.replaceAll("[^0-9+]", "");
            if (compClean.equals(phoneClean)) {
                curCompPhone = landlines.get(0).display;
            }
        }

        // Xử lý fax
        if (curFax.isBlank() && landlines.size() >= 2) {
            // Số máy bàn thứ 2 trên danh thiếp là số Fax
            curFax = landlines.get(1).display;
        }

        // Ghi lại vào node
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

        // 3. Làm sạch Email cá nhân
        String curEmail = textOf(node.get("email"));
        if (!curEmail.isBlank()) {
            if (curEmail.contains("/Skype")
                    || curEmail.contains("/Zalo")
                    || curEmail.contains("/Yahoo")
                    || curEmail.toLowerCase().startsWith("cskh@")
                    || curEmail.toLowerCase().startsWith("info@")
                    || curEmail.toLowerCase().startsWith("contact@")
                    || !EMAIL_SYNTAX_PATTERN.matcher(curEmail).matches()) {
                // Nếu email cá nhân bị nhầm thành email cty chung (cskh@...), chuyển sang company_email nếu company_email rỗng
                if ((curEmail.toLowerCase().startsWith("cskh@") || curEmail.toLowerCase().startsWith("info@"))
                        && textOf(node.get("company_email")).isBlank()) {
                    node.put("company_email", curEmail);
                }
                node.put("email", "");
            }
        }

        // 4. Làm sạch Email công ty
        String curCompEmail = textOf(node.get("company_email"));
        if (!curCompEmail.isBlank()) {
            // Sửa lỗi OCR dính chữ sau dấu @ (vd: cskh@Qatoztravel.vn -> cskh@atoztravel.vn)
            if (curCompEmail.matches("(?i).*@[qQ][a-z0-9.-]+\\.[a-z]{2,}")) {
                curCompEmail = curCompEmail.replaceAll("(?i)@[qQ]", "@");
                node.put("company_email", curCompEmail);
            }
        }

        // Phục hồi email bị lỗi OCR thiếu dấu @ (vd: vannhan.nomega.com.vn -> vannhan@omega.com.vn)
        if (textOf(node.get("email")).isBlank() && textOf(node.get("company_email")).isBlank()) {
            Pattern fuzzyEmailPattern = Pattern.compile(
                    "(?i)\\b([a-zA-Z0-9._%+-]+)(?:\\.n|\\.o|\\.at|\\s*@\\s*|\\s+at\\s+)([a-zA-Z0-9-]+\\.(?:com\\.vn|com|vn|net|org))\\b"
            );
            Matcher feM = fuzzyEmailPattern.matcher(rawText);
            if (feM.find()) {
                String restored = feM.group(1).trim() + "@" + feM.group(2).trim();
                node.put("email", restored);
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
