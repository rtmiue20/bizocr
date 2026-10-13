package com.company.bizocr.score;

public record CompletenessScoreResult(
        double score,
        String grade,
        String label,
        double mandatoryFieldsRatio,
        double contourAreaRatio,
        double avgOcrConfidence,
        int mandatoryFilled,
        int mandatoryTotal
) {}
