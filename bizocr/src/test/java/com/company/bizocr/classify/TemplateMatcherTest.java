package com.company.bizocr.classify;

import com.company.bizocr.model.dto.ClassifyResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class TemplateMatcherTest {

    private TemplateMatcher templateMatcher;

    @BeforeEach
    void setUp() {
        templateMatcher = new TemplateMatcher();
    }

    @Test
    @DisplayName("Khớp chính xác Hóa đơn GTGT (VAT_INVOICE) có dấu")
    void testMatchVatInvoiceWithDiacritics() {
        String rawText = """
                CÔNG TY CỔ PHẦN CÔNG NGHỆ BIZOCR VIỆT NAM
                Mã số thuế: 0166412776
                HÓA ĐƠN GIÁ TRỊ GIA TĂNG
                Cộng tiền hàng: 15.000.000 VNĐ
                Tiền thuế GTGT: 1.500.000 VNĐ
                Tổng tiền thanh toán: 16.500.000 VNĐ
                """;

        Optional<ClassifyResult> resultOpt = templateMatcher.match(rawText);

        assertTrue(resultOpt.isPresent(), "Phải khớp được mẫu");
        ClassifyResult result = resultOpt.get();
        assertEquals("VAT_INVOICE", result.getDocumentType());
        assertEquals("TEMPLATE_MATCHING", result.getMatchedBy());
        assertTrue(result.getConfidenceScore() >= 0.85);
        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("Khớp chính xác Hóa đơn GTGT không dấu do lỗi OCR")
    void testMatchVatInvoiceUnaccented() {
        String rawText = """
                TAP DOAN DAU TU VA PHAT TRIEN NGUYEN HOANG
                Ma so thue: 0433186279
                HOA DON GIA TRI GIA TANG
                Cong tien hang: 19.000.000 VND
                Tien thue GTGT: 1.900.000 VND
                Tong tien thanh toan: 20.900.000 VND
                """;

        Optional<ClassifyResult> resultOpt = templateMatcher.match(rawText);

        assertTrue(resultOpt.isPresent());
        ClassifyResult result = resultOpt.get();
        assertEquals("VAT_INVOICE", result.getDocumentType());
        assertTrue(result.getConfidenceScore() >= 0.85);
    }

    @Test
    @DisplayName("Khớp chính xác Phiếu thanh toán dịch vụ (SERVICE_PAYMENT_VOUCHER) - Loại mới xuất hiện nhiều từ log")
    void testMatchServicePaymentVoucher() {
        // Mẫu Taxi Mai Linh
        String taxiText = """
                TAXI MAI LINH
                Phiếu thanh toán dịch vụ
                Tổng chi phí: 741.000 VNĐ
                """;

        Optional<ClassifyResult> taxiResult = templateMatcher.match(taxiText);
        assertTrue(taxiResult.isPresent());
        assertEquals("SERVICE_PAYMENT_VOUCHER", taxiResult.get().getDocumentType());
        assertTrue(taxiResult.get().getConfidenceScore() >= 0.85);

        // Mẫu Nhà hàng Phố Mới
        String restaurantText = """
                NHÀ HÀNG PHỐ MỚI
                Phiếu thanh toán dịch vụ
                Tổng chi phí: 161.000 VNĐ
                """;

        Optional<ClassifyResult> restResult = templateMatcher.match(restaurantText);
        assertTrue(restResult.isPresent());
        assertEquals("SERVICE_PAYMENT_VOUCHER", restResult.get().getDocumentType());

        // Mẫu Cửa hàng văn phòng phẩm ABC
        String storeText = """
                CỬA HÀNG VĂN PHÒNG PHẨM ABC
                Phiếu thanh toán dịch vụ
                Tổng chi phí: 1.160.000 VNĐ
                """;

        Optional<ClassifyResult> storeResult = templateMatcher.match(storeText);
        assertTrue(storeResult.isPresent());
        assertEquals("SERVICE_PAYMENT_VOUCHER", storeResult.get().getDocumentType());
    }

    @Test
    @DisplayName("Khớp chính xác Hóa đơn tiền điện EVN (ELECTRICITY_BILL) - Loại mới xuất hiện nhiều từ log")
    void testMatchElectricityBill() {
        // Mẫu Điện lực Hà Nội
        String hnEvn = """
                TỔNG CÔNG TY ĐIỆN LỰC HÀ NỘI
                Thẻ khách hàng: PA863207
                Tổng tiền điện thanh toán: 1.477.500 VNĐ
                """;

        Optional<ClassifyResult> hnResult = templateMatcher.match(hnEvn);
        assertTrue(hnResult.isPresent());
        assertEquals("ELECTRICITY_BILL", hnResult.get().getDocumentType());
        assertTrue(hnResult.get().getConfidenceScore() >= 0.85);

        // Mẫu Điện lực TPHCM
        String hcmEvn = """
                CÔNG TY ĐIỆN LỰC TPHCM
                Thẻ khách hàng: PA394435
                Tổng tiền điện thanh toán: 907.500 VNĐ
                """;

        Optional<ClassifyResult> hcmResult = templateMatcher.match(hcmEvn);
        assertTrue(hcmResult.isPresent());
        assertEquals("ELECTRICITY_BILL", hcmResult.get().getDocumentType());
        assertTrue(hcmResult.get().getConfidenceScore() >= 0.85);
    }

    @Test
    @DisplayName("Khớp chính xác Biên bản quyết toán hợp đồng (CONTRACT) thực tế từ OCR scan")
    void testMatchSettlementContract() {
        String contractText = """
                CENTRAL
                0027
                Cong hoa Xa hoi Chu nghia Viet nam
                GREENSYS
                Doc lap - Tu do - Hanh phuc
                BIEN BAN QUYET TOAN HOP DONG
                DU AN TICH HOP HOP DONG DIEN TU VAO HE THONG
                GREENSYSERP
                I. CAN CU
                Hop dong, Phu luc hop dong, va cac van ban dieu chinh lien quan da ky giua hai ben:
                Ben A: CONG TY CO PHAN XAY DUNG CENTRAL
                Ben B: CONG TY CO PHAN GIAI PHAP QUAN TRI GREENSYS
                Gia tri hop dong va cac phu luc da ky: 313,000,000
                Gia tri quyet toan: 313,000,000
                Tong cong
                """;

        Optional<ClassifyResult> resultOpt = templateMatcher.match(contractText);
        assertTrue(resultOpt.isPresent());
        assertEquals("CONTRACT", resultOpt.get().getDocumentType());
        assertTrue(resultOpt.get().getConfidenceScore() >= 0.85);
    }

    @Test
    @DisplayName("Khớp chính xác Hóa đơn nước (WATER_BILL)")
    void testMatchWaterBill() {
        String waterText = """
                CÔNG TY CỔ PHẦN CẤP NƯỚC THỦ ĐỨC
                HÓA ĐƠN TIỀN NƯỚC
                Mã danh bộ: 12345678
                Tiêu thụ: 25 m3
                Tiền nước: 250.000 VNĐ
                """;

        Optional<ClassifyResult> result = templateMatcher.match(waterText);
        assertTrue(result.isPresent());
        assertEquals("WATER_BILL", result.get().getDocumentType());
    }

    @Test
    @DisplayName("Khớp chính xác Ủy nhiệm chi ngân hàng (BANK_STATEMENT)")
    void testMatchBankStatement() {
        String bankText = """
                NGÂN HÀNG THƯƠNG MẠI CỔ PHẦN NGOẠI THƯƠNG VIỆT NAM (VIETCOMBANK)
                ỦY NHIỆM CHI / PAYMENT ORDER
                Đơn vị trả tiền: CÔNG TY ABC
                Số tài khoản: 0011001234567
                Đơn vị thụ hưởng: CÔNG TY XYZ
                Số tiền bằng chữ: Một trăm triệu đồng chẵn
                """;

        Optional<ClassifyResult> result = templateMatcher.match(bankText);
        assertTrue(result.isPresent());
        assertEquals("BANK_STATEMENT", result.get().getDocumentType());
    }

    @Test
    @DisplayName("Khớp chính xác Căn cước công dân (ID_CARD)")
    void testMatchIdCard() {
        String idText = """
                CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM
                CĂN CƯỚC CÔNG DÂN
                Số / No: 079099012345
                Họ và tên: NGUYỄN VĂN A
                Ngày sinh: 01/01/1990
                Quê quán: Hà Nội
                """;

        Optional<ClassifyResult> result = templateMatcher.match(idText);
        assertTrue(result.isPresent());
        assertEquals("ID_CARD", result.get().getDocumentType());
    }

    @Test
    @DisplayName("Khớp chính xác Danh thiếp (NAMECARD) từ văn bản OCR thực tế có đầy đủ chức danh, email, tel, mobile, website, tax code")
    void testMatchNameCardRealisticOcr() {
        String realisticOcr = """
                OTCIT
                Nguyễn Việt Hưng
                International Terminal Co, Ltd
                Tan Cang-Cni Mep
                Giám Đốc Tài Chính
                CÔNG TY TNHH CẢNG QUỐC TẾ TÂN CẢNG - CÁI MẾP
                Tầng 11, P.1104, Sunwah Tower
                115 Nguyễn Huệ, Q.1, Tp.HCM
                Tel: 84838278 591 (ext: 109)-Fax: 84838278 592
                Mobile: 84-988 202 177 Tax code: 3501473524
                E-mail: hung.nguyen@Atcit.com vn
                Website: www.tcit.c@m.vn
                """;

        Optional<ClassifyResult> resultOpt = templateMatcher.match(realisticOcr);

        assertTrue(resultOpt.isPresent(), "Phải khớp được NAMECARD từ OCR thực tế");
        ClassifyResult result = resultOpt.get();
        assertEquals("NAMECARD", result.getDocumentType());
        assertTrue(result.getConfidenceScore() >= 0.70);
    }

    @Test
    @DisplayName("Khớp chính xác Danh thiếp (NAMECARD) khi số điện thoại/domain KHÔNG có nhãn - kịch bản OCR thực tế")
    void testMatchNameCardWithoutLabels() {
        // Namecard thật: không có nhãn "phone:"/"tel:"/"website:" đi kèm, chỉ in trần số và domain.
        // Đây chính là kịch bản trước đây khiến NAMECARD chỉ đạt 0.60 điểm (dưới ngưỡng 0.70)
        // vì thiếu regex bắt số điện thoại/domain không nhãn.
        String rawText = """
                NGUYỄN VĂN A
                Giám đốc
                CÔNG TY TNHH BIZOCR VIỆT NAM
                0912345678
                bizocr.com.vn
                """;

        Optional<ClassifyResult> resultOpt = templateMatcher.match(rawText);

        assertTrue(resultOpt.isPresent(), "Phải khớp được NAMECARD dù không có nhãn phone/website");
        ClassifyResult result = resultOpt.get();
        assertEquals("NAMECARD", result.getDocumentType());
        assertTrue(result.getConfidenceScore() >= 0.70);
    }

    @Test
    @DisplayName("Kiểm tra mở rộng động (Dynamic Extension) đăng ký thêm mẫu chứng từ mới")
    void testDynamicTemplateRegistration() {
        // Đăng ký loại chứng từ mới: PHIẾU XUẤT KHO (DELIVERY_ORDER)
        DocumentTemplate deliveryOrderTemplate = DocumentTemplate.builder()
                .documentType("DELIVERY_ORDER")
                .displayName("Phiếu xuất kho kiêm vận chuyển nội bộ")
                .category("INVENTORY")
                .priority(5)
                .confidenceThreshold(0.70)
                .primaryKeywords(List.of("phiếu xuất kho", "phieu xuat kho", "lệnh xuất kho"))
                .secondaryKeywords(List.of("thủ kho", "người vận chuyển", "số lượng xuất", "mã hàng hóa"))
                .regexPatterns(List.of(Pattern.compile("phiếu xuất kho|lệnh xuất kho", Pattern.CASE_INSENSITIVE)))
                .build();

        templateMatcher.registerTemplate(deliveryOrderTemplate);

        String sampleText = """
                CÔNG TY TNHH LOGISTICS VIỆT NAM
                PHIẾU XUẤT KHO KIÊM VẬN CHUYỂN NỘI BỘ
                Người vận chuyển: Trần Văn B
                Mã hàng hóa: SP-001
                Số lượng xuất: 50
                Thủ kho ký nhận
                """;

        Optional<ClassifyResult> result = templateMatcher.match(sampleText);
        assertTrue(result.isPresent());
        assertEquals("DELIVERY_ORDER", result.get().getDocumentType());
    }

    @Test
    @DisplayName("Xử lý chuỗi rỗng, null hoặc nội dung không hợp lệ")
    void testInvalidInputs() {
        assertTrue(templateMatcher.match(null).isEmpty());
        assertTrue(templateMatcher.match("").isEmpty());
        assertTrue(templateMatcher.match("   ").isEmpty());
        assertTrue(templateMatcher.match("Hôm nay trời đẹp quá đi dạo phố.").isEmpty());
    }
}