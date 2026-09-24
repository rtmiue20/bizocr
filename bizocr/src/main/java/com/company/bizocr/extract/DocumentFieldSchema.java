package com.company.bizocr.extract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Bộ key CỐ ĐỊNH cho từng loại chứng từ - nguồn sự thật duy nhất ở backend.
 *
 * Các engine trích xuất (regex, Ollama...) có thể trả key khác nhau (name / contact_name / personInCharge...).
 * normalize() gom tất cả về đúng bộ key chuẩn, đúng thứ tự; key thiếu = "" để người dùng nhập và xác nhận ở Bước 5.
 *
 * snake_case để khớp cột generated của bảng documents ($.tax_code, $.issue_date, $.total_amount).
 * Ba key tax_code / issue_date / total_amount được dùng thống nhất cho mọi loại chứng từ có ý nghĩa đó.
 *
 * File này được SINH từ một bảng dữ liệu chung với docSchemas.ts (frontend). Sửa form thì sửa ở cả hai nơi.
 * Loại chứng từ không có trong SCHEMAS (loại tùy biến tạo từ template_config) thì KHÔNG bị chuẩn hóa/bỏ key.
 */
@Slf4j
public final class DocumentFieldSchema {

    private DocumentFieldSchema() {
    }

    /** key = key chuẩn; label = tiêu đề hiển thị; hint = mô tả đưa vào prompt; match = các tên key (đã chuẩn hóa) coi là cùng trường. */
    public record Field(String key, String label, String hint, List<String> match) {
    }

    private static Field f(String key, String label, String hint, String... aliases) {
        List<String> m = new ArrayList<>();
        m.add(norm(key));
        for (String a : aliases) {
            m.add(norm(a));
        }
        return new Field(key, label, hint == null ? label : hint, m);
    }

    private static final Map<String, List<Field>> SCHEMAS = new LinkedHashMap<>();
    private static final Map<String, String> TYPE_ALIASES = new HashMap<>();

    private static void aliases(String canonical, String... codes) {
        for (String c : codes) {
            TYPE_ALIASES.put(c, canonical);
        }
    }

    static {
        SCHEMAS.put("NAMECARD", List.of(
                f("contact_name", "Họ và tên", "Họ và tên người trên danh thiếp", "name", "fullName", "personInCharge"),
                f("contact_title", "Chức danh", "Chức danh / Chức vụ (ví dụ: Director, Manager...)", "title", "position", "jobTitle"),
                f("company_name", "Tên công ty", "Tên công ty / Tổ chức", "company", "issuerName", "organization"),
                f("phone", "Số điện thoại", null, "phoneNumber", "tel", "mobile", "sdt"),
                f("email", "Email", "Địa chỉ Email", "mail"),
                f("tax_code", "Mã số thuế", null, "mst", "maSoThue"),
                f("address", "Địa chỉ", "Địa chỉ cơ quan / Văn phòng"),
                f("website", "Website", "Trang web", "web", "url")
        ));

        SCHEMAS.put("VAT_INVOICE", List.of(
                f("invoice_symbol", "Ký hiệu hóa đơn", "Ký hiệu hóa đơn / Mẫu số ký hiệu", "symbol", "serial", "invoiceSerial", "kyHieu"),
                f("invoice_number", "Số hóa đơn", null, "documentNumber", "number", "soHoaDon"),
                f("issue_date", "Ngày lập", "Ngày lập hóa đơn", "date", "invoiceDate", "ngayLap"),
                f("seller_name", "Đơn vị bán", "Đơn vị bán / Phía cung cấp", "issuerName", "seller", "supplier"),
                f("tax_code", "MST bên bán", "Mã số thuế bên bán", "sellerTaxCode", "mst", "maSoThue"),
                f("seller_address", "Địa chỉ bên bán", null, "address"),
                f("buyer_name", "Đơn vị mua", "Đơn vị mua / Khách hàng", "customerName", "buyer", "customer"),
                f("buyer_tax_code", "MST bên mua", "Mã số thuế bên mua", "customerTaxCode"),
                f("subtotal", "Tiền hàng (chưa thuế)", "Cộng tiền hàng chưa thuế", "amountBeforeTax", "totalBeforeTax", "tienHang"),
                f("vat_rate", "Thuế suất", "Thuế suất GTGT (%)", "taxRate"),
                f("vat_amount", "Tiền thuế GTGT", null, "vat", "taxAmount", "tienThue"),
                f("total_amount", "Tổng thanh toán", "Tổng tiền thanh toán", "total", "grandTotal", "tongTien")
        ));

        SCHEMAS.put("SERVICE_PAYMENT_VOUCHER", List.of(
                f("document_number", "Số phiếu / hóa đơn", "Số phiếu / Số hóa đơn", "invoiceNumber", "receiptNumber", "number"),
                f("issue_date", "Ngày lập", null, "date", "invoiceDate"),
                f("seller_name", "Đơn vị cung cấp", "Đơn vị cung cấp dịch vụ / Cửa hàng", "issuerName", "merchant", "storeName", "seller"),
                f("tax_code", "Mã số thuế", null, "sellerTaxCode", "mst", "maSoThue"),
                f("address", "Địa chỉ", null, "sellerAddress"),
                f("customer_name", "Khách hàng", null, "buyerName", "buyer"),
                f("payment_method", "Hình thức thanh toán", null),
                f("total_amount", "Tổng thanh toán", "Tổng tiền thanh toán", "total", "grandTotal", "amount")
        ));

        SCHEMAS.put("ELECTRICITY_BILL", List.of(
                f("invoice_number", "Số hóa đơn", null, "documentNumber", "number"),
                f("issue_date", "Ngày lập", null, "date", "invoiceDate"),
                f("seller_name", "Đơn vị điện lực", null, "issuerName", "seller", "supplier"),
                f("tax_code", "Mã số thuế", "Mã số thuế đơn vị điện lực", "sellerTaxCode", "mst"),
                f("customer_name", "Khách hàng", "Tên khách hàng", "buyerName", "customer"),
                f("customer_code", "Mã khách hàng", null, "maKhachHang", "customerId"),
                f("address", "Địa chỉ sử dụng điện", null, "customerAddress"),
                f("billing_period", "Kỳ hóa đơn", "Kỳ hóa đơn / Tháng sử dụng", "period"),
                f("consumption", "Điện tiêu thụ (kWh)", null, "consumptionKwh", "kwh", "usage"),
                f("total_amount", "Tổng thanh toán", null, "total", "grandTotal", "amount")
        ));

        SCHEMAS.put("WATER_BILL", List.of(
                f("invoice_number", "Số hóa đơn", null, "documentNumber", "number"),
                f("issue_date", "Ngày lập", null, "date", "invoiceDate"),
                f("seller_name", "Đơn vị cấp nước", null, "issuerName", "seller", "supplier"),
                f("tax_code", "Mã số thuế", "Mã số thuế đơn vị cấp nước", "sellerTaxCode", "mst"),
                f("customer_name", "Khách hàng", "Tên khách hàng", "buyerName", "customer"),
                f("customer_code", "Mã khách hàng", null, "maKhachHang", "customerId", "danhBo"),
                f("address", "Địa chỉ sử dụng nước", null, "customerAddress"),
                f("billing_period", "Kỳ hóa đơn", "Kỳ hóa đơn / Tháng sử dụng", "period"),
                f("consumption", "Nước tiêu thụ (m3)", null, "consumptionM3", "m3", "usage"),
                f("total_amount", "Tổng thanh toán", null, "total", "grandTotal", "amount")
        ));

        SCHEMAS.put("CONTRACT", List.of(
                f("contract_number", "Số hợp đồng", null, "documentNumber", "number"),
                f("contract_title", "Tên hợp đồng", "Tên / Tiêu đề hợp đồng", "documentTitle", "title"),
                f("issue_date", "Ngày ký", "Ngày ký hợp đồng", "signDate", "date"),
                f("party_a", "Bên A", "Tên đơn vị / cá nhân Bên A", "partyAName"),
                f("party_a_tax_code", "MST bên A", "Mã số thuế Bên A", "taxCode"),
                f("party_b", "Bên B", "Tên đơn vị / cá nhân Bên B", "partyBName"),
                f("party_b_tax_code", "MST bên B", "Mã số thuế Bên B"),
                f("total_amount", "Giá trị hợp đồng", null, "contractValue", "value", "amount", "total"),
                f("effective_date", "Ngày hiệu lực", null, "startDate"),
                f("expiry_date", "Ngày hết hạn", null, "endDate")
        ));

        SCHEMAS.put("BANK_STATEMENT", List.of(
                f("document_number", "Số chứng từ", null, "number", "transactionId"),
                f("issue_date", "Ngày chứng từ", null, "date", "transactionDate"),
                f("bank_name", "Ngân hàng", null, "bank"),
                f("payer_name", "Người chuyển", "Người chuyển / Đơn vị trả tiền", "payer", "senderName", "remitter"),
                f("payer_account", "Số TK người chuyển", null, "senderAccount", "accountNumber"),
                f("beneficiary_name", "Người thụ hưởng", null, "beneficiary", "receiverName", "payee"),
                f("beneficiary_account", "Số TK thụ hưởng", null, "receiverAccount"),
                f("beneficiary_bank", "Ngân hàng thụ hưởng", null, "receiverBank"),
                f("total_amount", "Số tiền", null, "amount", "total"),
                f("content", "Nội dung", "Nội dung chuyển khoản / nộp tiền", "note", "description", "memo")
        ));

        SCHEMAS.put("ID_CARD", List.of(
                f("id_number", "Số CCCD/CMND", "Số định danh cá nhân / Số CCCD / CMND", "cccd", "cmnd", "documentNumber", "soCanCuoc"),
                f("full_name", "Họ và tên", null, "name", "fullName"),
                f("date_of_birth", "Ngày sinh", null, "dob", "birthDate"),
                f("gender", "Giới tính", null, "sex"),
                f("nationality", "Quốc tịch", null),
                f("place_of_origin", "Quê quán", null, "hometown"),
                f("place_of_residence", "Nơi thường trú", null, "residence", "address"),
                f("issue_date", "Ngày cấp", null, "date"),
                f("expiry_date", "Có giá trị đến", null, "validUntil")
        ));

        SCHEMAS.put("OTHER", List.of(
                f("document_title", "Tên loại chứng từ", "Tên loại chứng từ / Tiêu đề giấy tờ"),
                f("document_number", "Số hiệu chứng từ", "Số hiệu / Mã số chứng từ", "number"),
                f("issue_date", "Ngày tháng năm", null, "date"),
                f("issuer_name", "Đơn vị phát hành", "Tên đơn vị / Cá nhân phát hành", "company", "companyName"),
                f("tax_code", "Mã số thuế", "Mã số thuế (nếu có)", "mst"),
                f("contact_name", "Người liên quan", "Họ tên người liên quan / Đại diện", "personInCharge", "name", "fullName"),
                f("contact_title", "Chức danh", "Chức danh / Vị trí công tác (nếu có)", "title", "position"),
                f("phone", "Số điện thoại", "Số điện thoại liên hệ", "phoneNumber", "tel"),
                f("email", "Email", "Email liên hệ"),
                f("address", "Địa chỉ", "Địa chỉ ghi trên chứng từ"),
                f("total_amount", "Số tiền", "Số tiền / Giá trị (nếu có)", "total", "amount"),
                f("note", "Ghi chú", "Ghi chú / Nội dung chính", "content", "description")
        ));

        aliases("NAMECARD", "NAME_CARD", "DANH_THIEP", "BUSINESS_CARD");
        aliases("VAT_INVOICE", "INVOICE", "HOA_DON", "HOA_DON_GTGT");
        aliases("SERVICE_PAYMENT_VOUCHER", "RECEIPT", "INVOICE_SUPERMARKET", "RETAIL_INVOICE");
        aliases("ELECTRICITY_BILL", "INVOICE_ELECTRICITY");
        aliases("WATER_BILL", "INVOICE_WATER");
        aliases("CONTRACT", "CONTRACT_LABOR");
        aliases("ID_CARD", "IDCARD", "CCCD", "CMND");
        aliases("OTHER", "UNKNOWN", "CHUA_PHAN_LOAI");
    }

    // ------------------------------------------------------------------
    // Tra cứu
    // ------------------------------------------------------------------

    /** Chuẩn hóa mã loại: name_card / DANH_THIEP -> NAMECARD; rỗng / UNKNOWN -> OTHER; loại tùy biến giữ nguyên (viết hoa). */
    public static String canonicalType(String raw) {
        if (raw == null || raw.isBlank()) return "OTHER";
        String up = raw.trim().toUpperCase().replaceAll("[\\s\\-]+", "_");
        if (SCHEMAS.containsKey(up)) return up;
        String mapped = TYPE_ALIASES.get(up);
        return mapped != null ? mapped : up;
    }

    public static boolean hasSchema(String type) {
        return SCHEMAS.containsKey(canonicalType(type));
    }

    /** Danh sách trường chuẩn của loại chứng từ; null nếu loại đó không có schema. */
    public static List<Field> fieldsOf(String type) {
        return SCHEMAS.get(canonicalType(type));
    }

    public static Map<String, List<Field>> allSchemas() {
        return Collections.unmodifiableMap(SCHEMAS);
    }

    /** Khung JSON mẫu đưa vào prompt Ollama, sinh từ schema (luôn hợp lệ, luôn đúng key chuẩn). Null nếu không có schema. */
    public static String jsonTemplate(String type) {
        List<Field> fields = fieldsOf(type);
        if (fields == null) return null;
        StringBuilder sb = new StringBuilder("{\n");
        for (int i = 0; i < fields.size(); i++) {
            Field field = fields.get(i);
            sb.append("  \"").append(field.key()).append("\": \"")
                    .append(field.hint().replace("\\", "\\\\").replace("\"", "\\\""))
                    .append("\"");
            sb.append(i < fields.size() - 1 ? ",\n" : "\n");
        }
        return sb.append("}").toString();
    }

    // ------------------------------------------------------------------
    // Chuẩn hóa dữ liệu
    // ------------------------------------------------------------------

    /**
     * Đưa JSON về đúng bộ key chuẩn của loại chứng từ.
     * Trả nguyên bản nếu: loại không có schema, JSON rỗng / không phải object, hoặc JSON báo lỗi ({"error":...}).
     */
    public static String normalize(String type, String json, ObjectMapper om) {
        if (json == null || json.isBlank() || !hasSchema(type)) return json;
        try {
            ObjectNode out = normalizeToNode(type, om.readTree(json), om);
            return out == null ? json : om.writeValueAsString(out);
        } catch (Exception e) {
            return json;
        }
    }

    /**
     * Như normalize() nhưng làm việc trên JsonNode. Trả null nếu không áp dụng được (không có schema,
     * không phải object, hoặc chứa "error"). Key lạ bị loại bỏ và được ghi WARN để bạn bổ sung alias nếu cần.
     */
    public static ObjectNode normalizeToNode(String type, JsonNode src, ObjectMapper om) {
        List<Field> fields = fieldsOf(type);
        if (fields == null || src == null || !src.isObject() || src.has("error")) return null;

        Map<String, String> originalName = new LinkedHashMap<>();
        Map<String, JsonNode> byNorm = new LinkedHashMap<>();
        src.fieldNames().forEachRemaining(name -> {
            String k = norm(name);
            originalName.putIfAbsent(k, name);
            JsonNode old = byNorm.get(k);
            if (old == null || textOf(old).isEmpty()) {
                byNorm.put(k, src.get(name));
            }
        });

        Set<String> consumed = new HashSet<>();
        ObjectNode out = om.createObjectNode();
        for (Field field : fields) {
            String value = "";
            for (String m : field.match()) {
                if (byNorm.containsKey(m)) {
                    consumed.add(m);
                    if (value.isEmpty()) {
                        value = textOf(byNorm.get(m));
                    }
                }
            }
            out.put(field.key(), value);
        }

        List<String> dropped = new ArrayList<>();
        for (Map.Entry<String, String> e : originalName.entrySet()) {
            if (!consumed.contains(e.getKey()) && !isInternalKey(e.getValue())
                    && !textOf(byNorm.get(e.getKey())).isEmpty()) {
                dropped.add(e.getValue());
            }
        }
        if (!dropped.isEmpty()) {
            log.warn("[FieldSchema] {}: bỏ qua key không thuộc form cố định: {}", canonicalType(type), dropped);
        }
        return out;
    }

    /** Key nội bộ của pipeline, không phải dữ liệu chứng từ. */
    public static boolean isInternalKey(String name) {
        String n = name.toLowerCase();
        return n.startsWith("_") || n.startsWith("validator_") || norm(name).equals("reviewstatus");
    }

    private static String textOf(JsonNode v) {
        if (v == null || v.isNull() || v.isMissingNode()) return "";
        String s = v.isContainerNode() ? v.toString() : v.asText().trim();
        return "null".equalsIgnoreCase(s) ? "" : s;
    }

    /** phoneNumber = phone_number = PhoneNumber. */
    private static String norm(String s) {
        return s == null ? "" : s.toLowerCase().replaceAll("[^a-z0-9]", "");
    }
}