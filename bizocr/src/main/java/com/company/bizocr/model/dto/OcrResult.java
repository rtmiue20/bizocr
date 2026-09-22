package com.company.bizocr.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Response OCR từ Python FastAPI ({@code OcrResponseDto}).
 * Trường mới (contour, aspect sau crop, quality gate) để nullable
 * để client/Java cũ không bắt buộc gửi.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
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

    private Integer width;
    private Integer height;
    private Double aspectRatio;
    private Double aspectRatioAfterCrop;
    private Integer originalWidth;
    private Integer originalHeight;
    private Double contourAreaRatio;
    private Double laplacianVariance;
    private Double deskewAngle;
    private Boolean isCropped;
    private Boolean qualityPassed;
    @Builder.Default
    private List<String> qualityReasons = new ArrayList<>();
    private String qualityMode;
    private String candidateDocumentType;
    private Double candidateConfidence;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BoundingBox {
        private String text;
        private List<Integer> points;
    }
}
