package com.company.bizocr.score;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CompletenessScoreServiceTest {

    private CompletenessScoreService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new CompletenessScoreService(objectMapper);
    }

    @Test
    @DisplayName("VAT_INVOICE đầy đủ 4 trường cốt lõi -> MandatoryFieldsRatio = 1.0, Score cao")
    void testVatInvoiceFullFields() {
        String json = """
                {
                    "tax_code": "0100109106",
                    "seller_name": "TẬP ĐOÀN VIỄN THÔNG QUÂN ĐỘI",
                    "invoice_date": "20/10/2023",
                    "total_amount": 500000.0
                }
                """;

        CompletenessScoreResult result = service.compute("VAT_INVOICE", json, 0.9, 0.95);

        assertNotNull(result);
        assertEquals(1.0, result.mandatoryFieldsRatio());
        assertEquals(4, result.mandatoryFilled());
        assertEquals(4, result.mandatoryTotal());
        assertEquals(0.9, result.contourAreaRatio());
        assertEquals(0.95, result.avgOcrConfidence());

        // Formula: 0.5 * 1.0 + 0.3 * 0.9 + 0.2 * 0.95 = 0.5 + 0.27 + 0.19 = 0.96
        assertEquals(0.96, result.score(), 0.01);
        assertEquals("A", result.grade());
        assertTrue(result.label().contains("Xuất sắc"));
        assertTrue(service.buildConfirmPrompt(result).contains("Xuất sắc"));
    }

    @Test
    @DisplayName("NAMECARD chỉ có 2 trường (name, company) -> MandatoryFieldsRatio = 0.5")
    void testNamecardPartialFields() {
        String json = """
                {
                    "contact_name": "Nguyễn Văn A",
                    "company_name": "Công ty TNHH Giải Pháp AI"
                }
                """;

        // contour = 0.8, ocr_conf = 0.7
        // Formula: 0.5 * 0.5 + 0.3 * 0.8 + 0.2 * 0.7 = 0.25 + 0.24 + 0.14 = 0.63
        CompletenessScoreResult result = service.compute("NAMECARD", json, 0.8, 0.7);

        assertNotNull(result);
        assertEquals(0.5, result.mandatoryFieldsRatio());
        assertEquals(2, result.mandatoryFilled());
        assertEquals(4, result.mandatoryTotal());
        assertEquals(0.63, result.score(), 0.01);
        assertEquals("C", result.grade());
        assertTrue(result.label().contains("Trung bình"));
        assertTrue(service.buildConfirmPrompt(result).contains("Trung bình"));
    }

    @Test
    @DisplayName("Xử lý an toàn khi contourAreaRatio và avgOcrConfidence bị null (dùng fallback)")
    void testNullRatiosFallback() {
        String json = """
                {
                    "tax_code": "0100109106",
                    "seller_name": "Công ty ABC",
                    "invoice_date": "15/01/2024",
                    "total_amount": 1000000
                }
                """;

        // null contour fallback = 0.6, null conf fallback = 0.5
        // Formula: 0.5 * 1.0 + 0.3 * 0.6 + 0.2 * 0.5 = 0.5 + 0.18 + 0.10 = 0.78
        CompletenessScoreResult result = service.compute("VAT_INVOICE", json, null, null);

        assertNotNull(result);
        assertEquals(0.6, result.contourAreaRatio());
        assertEquals(0.5, result.avgOcrConfidence());
        assertEquals(0.78, result.score(), 0.01);
        assertEquals("B", result.grade());
        assertTrue(result.label().contains("Tốt"));
    }

    @Test
    @DisplayName("JSON rỗng hoặc rác -> score thấp (Grade D)")
    void testEmptyJsonResultsInGradeD() {
        CompletenessScoreResult result = service.compute("VAT_INVOICE", "{}", 0.3, 0.4);

        assertNotNull(result);
        assertEquals(0.0, result.mandatoryFieldsRatio());
        assertEquals(0, result.mandatoryFilled());

        // Formula: 0.5 * 0.0 + 0.3 * 0.3 + 0.2 * 0.4 = 0.0 + 0.09 + 0.08 = 0.17
        assertEquals(0.17, result.score(), 0.01);
        assertEquals("D", result.grade());
        assertTrue(result.label().contains("Cần xem lại"));
        assertTrue(service.buildConfirmPrompt(result).contains("Cần xem lại"));
    }

    @Test
    @DisplayName("Điểm số được giới hạn chính xác trong khoảng [0.0, 1.0]")
    void testScoreBoundaryClamping() {
        // Test giá trị ngoại lai
        CompletenessScoreResult high = service.compute("VAT_INVOICE", "{\"tax_code\":\"1\",\"seller_name\":\"2\",\"invoice_date\":\"3\",\"total_amount\":4}", 1.5, 2.0);
        assertTrue(high.score() <= 1.0);

        CompletenessScoreResult low = service.compute("VAT_INVOICE", "", -0.5, -1.0);
        assertTrue(low.score() >= 0.0);
    }
}
