package com.company.bizocr.extract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NamecardSanitizerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("Email phân luồng: Gmail vào email cá nhân, domain riêng vào company_email")
    void testEmailRouting_personalAndCompanyEmail() {
        ObjectNode input = mapper.createObjectNode();
        input.put("contact_name", "Tô Ngọc Phát");
        input.put("email", "phatmarcom@gmail.com");
        input.put("company_email", "ceo@forlybrand.com");

        String rawText = "FORLY BRAND\nTô Ngọc Phát\nCEO\nEmail: phatmarcom@gmail.com\nEmail: ceo@forlybrand.com\nĐT: 0903123456";

        ObjectNode result = NamecardSanitizer.sanitize(input, rawText, mapper);

        assertThat(result.get("email").asText()).isEqualTo("phatmarcom@gmail.com");
        assertThat(result.get("company_email").asText()).isEqualTo("ceo@forlybrand.com");
        assertThat(result.get("phone").asText()).isEqualTo("0903123456");
    }

    @Test
    @DisplayName("Thẻ chỉ có email domain công ty -> email cá nhân để trống, gán vào company_email")
    void testEmailRouting_companyDomainOnly() {
        ObjectNode input = mapper.createObjectNode();
        input.put("contact_name", "Nguyễn Văn A");
        // Giả sử extractor ban đầu gán nhầm vào field 'email'
        input.put("email", "info@khaihoanland.vn");

        String rawText = "KHAI HOAN LAND\nNguyễn Văn A\ninfo@khaihoanland.vn\nTel: 028.38221234";

        ObjectNode result = NamecardSanitizer.sanitize(input, rawText, mapper);

        assertThat(result.get("email").asText()).isEmpty();
        assertThat(result.get("company_email").asText()).isEqualTo("info@khaihoanland.vn");
        assertThat(result.get("company_phone").asText()).isEqualTo("028.38221234");
    }

    @Test
    @DisplayName("Hai email cùng domain công ty -> ưu tiên email có tên người, email generic vào note")
    void testEmailRouting_twoCompanyEmails_genericToNote() {
        ObjectNode input = mapper.createObjectNode();
        input.put("contact_name", "Tammy Tran");

        String rawText = "DIMENSIONS EDUCATION\nTammy Tran\nDirector\ntammy@dimensions.edu.vn\nstudent@dimensions.edu.vn";

        ObjectNode result = NamecardSanitizer.sanitize(input, rawText, mapper);

        assertThat(result.get("company_email").asText()).isEqualTo("tammy@dimensions.edu.vn");
        assertThat(result.get("email").asText()).isEmpty();
        assertThat(result.get("note").asText()).contains("student@dimensions.edu.vn");
    }

    @Test
    @DisplayName("Phân loại SĐT: Mobile vs Bàn vs Fax theo nhãn")
    void testPhoneRouting_labeled() {
        ObjectNode input = mapper.createObjectNode();
        input.put("contact_name", "Trần Văn B");

        String rawText = "ABC CORP\nTrần Văn B\nDĐ: 0912.345.678\nTel: 024.3789.9999\nFax: 024.3789.8888";

        ObjectNode result = NamecardSanitizer.sanitize(input, rawText, mapper);

        assertThat(result.get("phone").asText()).contains("0912.345.678");
        assertThat(result.get("company_phone").asText()).contains("024.3789.9999");
        assertThat(result.get("fax").asText()).contains("024.3789.8888");
    }
}
