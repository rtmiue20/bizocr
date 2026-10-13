package com.company.bizocr.validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExtractionValidatorShadowModeTest {

    private ExtractionValidatorService validatorService;
    private BusinessRuleValidator businessRuleValidator;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        objectMapper = new ObjectMapper();
        businessRuleValidator = new BusinessRuleValidator();
        validatorService = new ExtractionValidatorService(validator, objectMapper, businessRuleValidator);
    }

    @Test
    @DisplayName("BusinessRuleValidator - Check-digit MST chuẩn Việt Nam (0100109106 Viettel đúng, 0100109107 sai)")
    void testTaxCodeCheckDigit() {
        assertTrue(businessRuleValidator.validateTaxCode("0100109106")); // Viettel hợp lệ
        assertTrue(businessRuleValidator.validateTaxCode("0100109106-001")); // Chi nhánh hợp lệ
        assertFalse(businessRuleValidator.validateTaxCode("0100109107")); // Sai check-digit
        assertFalse(businessRuleValidator.validateTaxCode("123456")); // Sai độ dài
    }

    @Test
    @DisplayName("BusinessRuleValidator - Kiểm tra ngày tháng")
    void testDateValidation() {
        assertTrue(businessRuleValidator.validateDate("25/12/2023"));
        assertTrue(businessRuleValidator.validateDate("2024-05-15"));
        assertFalse(businessRuleValidator.validateDate("32/01/2024")); // Ngày không tồn tại
        assertFalse(businessRuleValidator.validateDate("15/15/2024")); // Tháng sai
        assertFalse(businessRuleValidator.validateDate("01/01/1850")); // Quá xa trong quá khứ
    }

    @Test
    @DisplayName("BusinessRuleValidator - Kiểm tra tổng tiền & VAT")
    void testAmountsValidation() {
        assertTrue(businessRuleValidator.validateAmounts(1000000.0, 100000.0));
        assertFalse(businessRuleValidator.validateAmounts(-500.0, null)); // Tiền âm
        assertFalse(businessRuleValidator.validateAmounts(1000.0, 2000.0)); // VAT lớn hơn tổng tiền
    }

    @Test
    @DisplayName("BusinessRuleValidator - Grounding đối chiếu text OCR")
    void testGrounding() {
        String ocr = "HOA DON GIA TRI GIA TANG\nMST: 0100109106\nCONG TY VIETTEL\nTong tien: 500,000 VND";
        assertTrue(businessRuleValidator.isGrounded("0100109106", ocr));
        assertTrue(businessRuleValidator.isGrounded("Cong ty Viettel", ocr)); // Thử không dấu
        assertTrue(businessRuleValidator.isGrounded("500000", ocr));
        assertFalse(businessRuleValidator.isGrounded("CONG TY VINAMILK", ocr)); // Ảo giác, không có trong text
    }

    @Test
    @DisplayName("Grounding - So khớp chuẩn hóa: SĐT có/không có khoảng trắng phải khớp nhau")
    void testGroundingNormalizedPhoneFormatting() {
        // OCR viết có khoảng trắng, AI trả về không khoảng trắng
        assertTrue(businessRuleValidator.isGrounded("0912345678", "DT: 0912 345 678"));
        // OCR viết không khoảng trắng, AI trả về có khoảng trắng
        assertTrue(businessRuleValidator.isGrounded("0912 345 678", "Hotline: 0912345678"));
        // Số tiền: OCR có dấu chấm phân cách, AI trả về liên tục
        assertTrue(businessRuleValidator.isGrounded("1200000", "Tong tien: 1.200.000 VND"));
        // MST có gạch ngang (NNNNNNNNNN-NNN)
        assertTrue(businessRuleValidator.isGrounded("0100109106123", "MST: 0100109106-123"));
        // Không được grounding cho số không tồn tại trong OCR
        assertFalse(businessRuleValidator.isGrounded("0987654321", "DT: 0912 345 678"));
    }

    @Test
    @DisplayName("Shadow Mode = TRUE: Không chặn luồng khi có lỗi vi phạm, ghi nhận telemetry và validator_errors")
    void testShadowModeTrue_DoesNotBlock() throws Exception {
        validatorService.setShadowMode(true);

        // Giả lập hóa đơn có MST sai checksum và tên seller không có trong OCR
        String rawOcr = "HOA DON GTGT Tong tien: 500000";
        String extractedJson = """
                {
                    "tax_code": "0100109107",
                    "seller_name": "CONG TY AO GIAC",
                    "total_amount": 500000.0,
                    "invoice_date": "20/10/2023"
                }
                """;

        String resultJson = validatorService.validateAndSanitize("VAT_INVOICE", extractedJson, rawOcr);
        JsonNode node = objectMapper.readTree(resultJson);

        // Trong Shadow Mode: review_status vẫn giữ AUTO_APPROVED để không gián đoạn
        assertEquals("AUTO_APPROVED", node.get("review_status").asText());
        assertTrue(node.get("validator_shadow_mode").asBoolean());
        assertTrue(node.get("validator_has_violations").asBoolean());
        assertTrue(node.has("validator_errors"));
        assertTrue(node.get("validator_errors").size() > 0);

        // Telemetry ghi nhận vi phạm
        assertTrue(validatorService.getBusinessRuleViolationsCount() > 0);
        assertTrue(validatorService.getGroundingViolationsCount() > 0);
    }

    @Test
    @DisplayName("Shadow Mode = FALSE (Blocking Mode): Chặn ngay và gán review_status = PENDING_REVIEW")
    void testShadowModeFalse_BlocksAndSetsPendingReview() throws Exception {
        validatorService.setShadowMode(false);

        String rawOcr = "HOA DON GTGT";
        String extractedJson = """
                {
                    "tax_code": "0100109107",
                    "total_amount": 500000.0
                }
                """;

        String resultJson = validatorService.validateAndSanitize("VAT_INVOICE", extractedJson, rawOcr);
        JsonNode node = objectMapper.readTree(resultJson);

        // Khi chuyển sang Blocking mode: Bị đẩy vào PENDING_REVIEW để reviewer xử lý
        assertEquals("PENDING_REVIEW", node.get("review_status").asText());
        assertFalse(node.get("validator_shadow_mode").asBoolean());
        assertTrue(node.has("validator_errors"));
    }
}
