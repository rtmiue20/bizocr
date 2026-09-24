package com.company.bizocr.extract;

import com.company.bizocr.validator.ExtractionValidatorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CandidateFilterAndExtractorTest {

    private CandidateFilterService candidateFilterService;
    private RegexExtractorService regexExtractorService;
    private ExtractionValidatorService extractionValidatorService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        candidateFilterService = new CandidateFilterService();
        regexExtractorService = new RegexExtractorService(candidateFilterService);
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        objectMapper = new ObjectMapper();
        extractionValidatorService = new ExtractionValidatorService(validator, objectMapper);
        extractionValidatorService.setShadowMode(false);
    }

    @Test
    @DisplayName("CandidateFilterService - Tax code hợp lệ và không hợp lệ")
    void testTaxCodeFiltering() {
        // MST hợp lệ: 0100109106 (Viettel MST chuẩn checksum)
        Optional<String> validMst = candidateFilterService.resolveTaxCode(List.of("0100109106"));
        assertTrue(validMst.isPresent());
        assertEquals("0100109106", validMst.get());

        // MST sai checksum: 0100109107
        Optional<String> invalidMst = candidateFilterService.resolveTaxCode(List.of("0100109107"));
        assertTrue(invalidMst.isEmpty());
    }

    @Test
    @DisplayName("CandidateFilterService - MST 13 số hỏng không bị nhận nhầm thành STK Agribank/VBA 13 số do kiểm tra ngữ cảnh")
    void testCorrupted13DigitTaxCodeNotConfusedWithAgribank() {
        String rawText = """
                CÔNG TY TNHH KINH DOANH THƯƠNG MẠI
                Mã số thuế: 0101234567-001
                Địa chỉ: Hà Nội
                """;
        // Giả sử candidate là "0101234567001" (13 số), nhưng sai checksum MST
        List<String> candidates = List.of("0101234567001");

        // Khi có ngữ cảnh "Mã số thuế", không được nhận nhầm là STK VBA dù VBA có độ dài 13 số
        Optional<String> bankAccount = candidateFilterService.resolveBankAccount(candidates, "VBA", rawText);
        assertTrue(bankAccount.isEmpty(), "MST hỏng trong ngữ cảnh MST không được coi là STK ngân hàng");

        // Nhưng nếu trong ngữ cảnh STK:
        String bankText = """
                CÔNG TY TNHH KINH DOANH THƯƠNG MẠI
                Số tài khoản: 0101234567001
                Ngân hàng Nông nghiệp Agribank
                """;
        Optional<String> validBankAcc = candidateFilterService.resolveBankAccount(candidates, "VBA", bankText);
        assertTrue(validBankAcc.isPresent(), "Cùng chuỗi 13 số nhưng trong ngữ cảnh STK sẽ được nhận là STK");
    }

    @Test
    @DisplayName("RegexExtractorService - Chỉ gán tax_code khi checksum hợp lệ")
    void testRegexExtractorWithChecksum() {
        // Text với MST hợp lệ: 0100109106
        String validText = """
                HÓA ĐƠN GIÁ TRỊ GIA TĂNG
                Mã số thuế: 0100109106
                Tổng tiền: 1000000
                """;
        Map<String, Object> validFields = regexExtractorService.extractFromHardcodedRegex("VAT_INVOICE", validText);
        assertEquals("0100109106", validFields.get("tax_code"));
        assertEquals(1000000.0, validFields.get("total_amount"));

        // Text với MST sai checksum: 0100109107
        String invalidText = """
                HÓA ĐƠN GIÁ TRỊ GIA TĂNG
                Mã số thuế: 0100109107
                Tổng tiền: 1000000
                """;
        Map<String, Object> invalidFields = regexExtractorService.extractFromHardcodedRegex("VAT_INVOICE", invalidText);
        assertNull(invalidFields.get("tax_code"), "MST fail checksum không được đưa vào trường tax_code");
    }

    @Test
    @DisplayName("ExtractionValidatorService - Hỗ trợ và validate tất cả các loại document type và bỏ qua unknown properties")
    void testExtractionValidatorAllDocTypes() {
        // 1. VAT_INVOICE với unknown properties và MST 13 số hợp lệ check-digit
        String vatJson = "{\"tax_code\":\"0100109106001\",\"total_amount\":500000.0,\"email\":\"test@bizocr.vn\",\"website\":\"www.bizocr.vn\"}";
        String validatedVat = extractionValidatorService.validateAndSanitize("VAT_INVOICE", vatJson);
        assertTrue(validatedVat.contains("AUTO_APPROVED"));

        // 2. CONTRACT với total_amount
        String contractJson = "{\"party_a\":\"Cong Ty A\",\"party_b\":\"Cong Ty B\",\"total_amount\":100000.0,\"extra_prop\":\"ignored\"}";
        String validatedContract = extractionValidatorService.validateAndSanitize("CONTRACT", contractJson);
        assertTrue(validatedContract.contains("AUTO_APPROVED"));

        // 2b. CONTRACT với total_amount âm (phải bị bắt lỗi PENDING_REVIEW)
        String invalidContractJson = "{\"party_a\":\"Cong Ty A\",\"party_b\":\"Cong Ty B\",\"total_amount\":-500.0}";
        String validatedInvalidContract = extractionValidatorService.validateAndSanitize("CONTRACT", invalidContractJson);
        assertTrue(validatedInvalidContract.contains("PENDING_REVIEW"));

        // 3. NAMECARD (với alias contact_name / company_name)
        String namecardJson = "{\"contact_name\":\"Nguyen Van A\",\"phone\":\"0912345678\",\"company_name\":\"BizOCR\",\"random\":\"123\"}";
        String validatedNameCard = extractionValidatorService.validateAndSanitize("NAMECARD", namecardJson);
        assertTrue(validatedNameCard.contains("AUTO_APPROVED"));

        // 4. ELECTRICITY_BILL & WATER_BILL (với customer_id và total_amount)
        String elecJson = "{\"customer_id\":\"PA123456\",\"total_amount\":350000.0}";
        String validatedElec = extractionValidatorService.validateAndSanitize("ELECTRICITY_BILL", elecJson);
        assertTrue(validatedElec.contains("AUTO_APPROVED"));

        String waterJson = "{\"customer_id\":\"DB987654\",\"total_amount\":120000.0}";
        String validatedWater = extractionValidatorService.validateAndSanitize("WATER_BILL", waterJson);
        assertTrue(validatedWater.contains("AUTO_APPROVED"));

        // 5. SERVICE_PAYMENT_VOUCHER (với service_name và total_amount)
        String serviceJson = "{\"receipt_no\":\"REC001\",\"service_name\":\"Taxi Mai Linh\",\"total_amount\":75000.0}";
        String validatedService = extractionValidatorService.validateAndSanitize("SERVICE_PAYMENT_VOUCHER", serviceJson);
        assertTrue(validatedService.contains("AUTO_APPROVED"));
    }

    @Test
    @DisplayName("RegexExtractorService - Pattern 1 (labeled MST) ưu tiên tuyệt đối, không lấy nhầm số 10 chữ số khác khi MST nhãn bị lỗi OCR")
    void testTaxCodeCandidatesLabeledPriorityOverRandom10Digits() {
        // Text có MST nhãn bị sai checksum (0100109107) và 1 số 10 chữ số khác ngẫu nhiên pass checksum (0100109106 - ví dụ mã bưu chính/khách hàng)
        String rawText = """
                CÔNG TY TNHH ABC
                Mã số thuế: 0100109107
                Mã tham chiếu: 0100109106
                Tổng tiền: 500000
                """;

        List<String> candidates = regexExtractorService.extractTaxCodeCandidates(rawText);
        // Vì có MST có nhãn rõ ràng, chỉ lấy candidate của MST nhãn
        assertEquals(List.of("0100109107"), candidates);

        // Do candidate có nhãn sai checksum, resolveTaxCode sẽ trả về empty chứ không nhận nhầm số 0100109106
        Optional<String> resolved = candidateFilterService.resolveTaxCode(candidates);
        assertTrue(resolved.isEmpty(), "Không được chọn nhầm số 10 chữ số khác khi MST có nhãn bị lỗi");
    }

    @Test
    @DisplayName("RegexExtractorService - Pattern 2 loại bỏ số 10 chữ số trong ngữ cảnh Điện thoại, STK, Số HĐ khi không có nhãn MST")
    void testTaxCodeCandidatesContextFilteringWhenNoLabel() {
        String rawText = """
                CÔNG TY TNHH ABC
                Điện thoại: 0100109106
                Số HĐ: 0100109106
                STK: 0100109106
                """;

        List<String> candidates = regexExtractorService.extractTaxCodeCandidates(rawText);
        assertTrue(candidates.isEmpty(), "Các số trong ngữ cảnh ĐT, STK, Số HĐ phải bị loại khỏi ứng viên MST");
    }

    @Test
    @DisplayName("RegexExtractorService - extractFromDynamicConfig an toàn không ném NPE khi regex group trả về null")
    void testDynamicConfigNullGroupSafety() {
        com.company.bizocr.model.entity.TemplateFieldConfig config = new com.company.bizocr.model.entity.TemplateFieldConfig();
        config.setFieldKey("bank_account");
        config.setRegexPattern("(?:STK:)?([0-9]+)?"); // Regex có optional group 1
        config.setDataType("STRING");
        config.setDocTypeCode("TEST_DOC");

        // rawText không match group 1 (group 1 là null)
        String rawText = "STK:";

        assertDoesNotThrow(() -> {
            Map<String, Object> result = regexExtractorService.extractFromDynamicConfig(List.of(config), rawText);
            assertNotNull(result);
            assertFalse(result.containsKey("bank_account"));
        });
    }

    @Test
    @DisplayName("RegexExtractorService - Trích xuất chính xác các trường của Danh thiếp (NAMECARD) từ văn bản OCR thực tế")
    void testExtractNameCardRealisticOcr() {
        String realisticOcr = """
                OTCIT
                Nguyễn Việt Hưng
                International Terminal Co, Ltd
                Tan Cang-Cai Mep
                Giám Đốc Tài Chính
                CÔNG TY TNHH CẢNG QUỐC TẾ TÂN CẢNG - CÁI MÉP
                Tầng 11, P.1104, Sunwah Tower
                115 Nguyễn Huệ, Q.1, Tp.HCM
                Tel: 84838278 591 (ext: 109)-Fax: 84838278 592
                Mobile: 84-988 202 177 Tax code: 3501473524
                E-mail: hung.nguyen@tcit.com vn
                Website: www.tcit.com.vn
                """;

        Map<String, Object> fields = regexExtractorService.extractFromHardcodedRegex("NAMECARD", realisticOcr);

        assertNotNull(fields);
        assertEquals("Nguyễn Việt Hưng", fields.get("contact_name"));
        assertEquals("Giám Đốc Tài Chính", fields.get("contact_title"));
        assertEquals("CÔNG TY TNHH CẢNG QUỐC TẾ TÂN CẢNG - CÁI MẾP", fields.get("company_name"));
        assertEquals("hung.nguyen@tcit.com.vn", fields.get("email"), "Email phải được sanitize sửa lỗi @Atcit và khoảng trắng");
        assertEquals("www.tcit.com.vn", fields.get("website"), "Website phải được sanitize sửa lỗi c@m");
        assertTrue(fields.containsKey("phone"));
        assertEquals("3501473524", fields.get("tax_code"));
    }
}
