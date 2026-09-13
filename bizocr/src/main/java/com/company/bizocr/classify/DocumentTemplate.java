package com.company.bizocr.classify;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Định nghĩa chuẩn hóa một mẫu chứng từ (Document Template) cho thuật toán Template Matching.
 * Cho phép mở rộng dễ dàng tới hàng trăm loại chứng từ khác nhau mà không làm thay đổi cấu trúc mã nguồn.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentTemplate {

    /**
     * Mã định danh loại chứng từ (Ví dụ: VAT_INVOICE, ELECTRICITY_BILL, SERVICE_PAYMENT_VOUCHER...)
     */
    private String documentType;

    /**
     * Tên hiển thị tiếng Việt (Ví dụ: "Hóa đơn GTGT", "Hóa đơn tiền điện", "Phiếu thanh toán dịch vụ"...)
     */
    private String displayName;

    /**
     * Danh mục / nhóm chứng từ (INVOICE, BILL, VOUCHER, CONTRACT, IDENTITY, BANKING...)
     */
    private String category;

    /**
     * Trọng số ưu tiên (số càng cao càng được ưu tiên khi tính điểm cạnh tranh)
     */
    @Builder.Default
    private int priority = 1;

    /**
     * Ngưỡng tin cậy tối thiểu để chấp nhận template này (Mặc định 0.70)
     */
    @Builder.Default
    private double confidenceThreshold = 0.70;

    /**
     * Danh sách từ khóa chính / tiêu đề (Primary / Title keywords).
     * Khi văn bản chứa từ khóa này, điểm tin cậy sẽ tăng vượt trội.
     */
    @Builder.Default
    private List<String> primaryKeywords = new ArrayList<>();

    /**
     * Danh sách từ khóa phụ / ngữ cảnh (Secondary / Context keywords).
     */
    @Builder.Default
    private List<String> secondaryKeywords = new ArrayList<>();

    /**
     * Danh sách biểu thức chính quy đặc trưng (Regex patterns).
     */
    @Builder.Default
    private List<Pattern> regexPatterns = new ArrayList<>();
}
