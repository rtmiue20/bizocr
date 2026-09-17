package com.company.bizocr.classify;

import org.springframework.stereotype.Component;

@Component
public class CompletenessScoreCalculator {

    // Trọng số - để trong application.yml sau khi tune với dữ liệu thật, tạm hardcode
    private static final double WEIGHT_FIELD_COMPLETENESS = 0.4;
    private static final double WEIGHT_CONTOUR_AREA = 0.3;
    private static final double WEIGHT_OCR_CONFIDENCE = 0.3;

    public double calculate(int nonEmptyFieldCount, int expectedFieldCount,
                            double contourAreaRatio, double ocrConfidence) {

        double fieldCompleteness = expectedFieldCount == 0
                ? 0.0
                : (double) nonEmptyFieldCount / expectedFieldCount;

        double normalizedContour = clamp(contourAreaRatio, 0.0, 1.0);
        double normalizedConfidence = clamp(ocrConfidence, 0.0, 1.0);

        return WEIGHT_FIELD_COMPLETENESS * fieldCompleteness
                + WEIGHT_CONTOUR_AREA * normalizedContour
                + WEIGHT_OCR_CONFIDENCE * normalizedConfidence;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
