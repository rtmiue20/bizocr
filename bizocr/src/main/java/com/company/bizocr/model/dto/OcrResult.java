package com.company.bizocr.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OcrResult {
    private String rawText;
    private String rawOcrText;
    private Double confidence;
    private List<BoundingBox> boxes;
    private Boolean normalized;

    /**
     * Tên engine OCR đã xử lý document này (vd: "paddleocr").
     * Dùng cho audit/debug khi so sánh chất lượng giữa các engine trong quá trình chuyển đổi,
     * và để truy vết nhanh nếu một document cụ thể cho kết quả bất thường.
     */
    private String engine;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BoundingBox {
        private String text;
        private List<Integer> points;
    }
}