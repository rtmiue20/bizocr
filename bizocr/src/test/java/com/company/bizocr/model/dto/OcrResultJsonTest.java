package com.company.bizocr.model.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OcrResultJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("Python thêm field mới không làm gãy deserialize (ignoreUnknown)")
    void unknownPropertiesDoNotBreakDeserialize() throws Exception {
        String json = """
                {
                  "rawText": "MST 0100109106",
                  "rawOcrText": "MST 0100109106",
                  "confidence": 0.91,
                  "engine": "paddleocr",
                  "normalized": true,
                  "futurePythonOnlyField": {"nested": true}
                }
                """;

        OcrResult result = mapper.readValue(json, OcrResult.class);
        assertEquals("MST 0100109106", result.getRawText());
        assertEquals(0.91, result.getConfidence());
        assertNull(result.getContourAreaRatio());
    }

    @Test
    @DisplayName("Metadata crop/deskew + quality gate map đúng tên camelCase Python")
    void qualityAndCropMetadataRoundTrip() throws Exception {
        String json = """
                {
                  "rawText": "hoa don",
                  "confidence": 0.8,
                  "width": 900,
                  "height": 640,
                  "aspectRatio": 1.4062,
                  "aspectRatioAfterCrop": 1.4062,
                  "originalWidth": 1280,
                  "originalHeight": 720,
                  "contourAreaRatio": 0.62,
                  "laplacianVariance": 180.5,
                  "deskewAngle": -1.2,
                  "isCropped": true,
                  "qualityPassed": false,
                  "qualityReasons": ["IMAGE_BLURRY"],
                  "qualityMode": "SHADOW",
                  "candidateDocumentType": "PAGE_DOCUMENT",
                  "candidateConfidence": 0.75
                }
                """;

        OcrResult result = mapper.readValue(json, OcrResult.class);
        assertEquals(900, result.getWidth());
        assertEquals(1.4062, result.getAspectRatioAfterCrop());
        assertEquals(0.62, result.getContourAreaRatio());
        assertTrue(result.getIsCropped());
        assertFalse(result.getQualityPassed());
        assertEquals("SHADOW", result.getQualityMode());
        assertEquals("IMAGE_BLURRY", result.getQualityReasons().get(0));
        assertEquals("PAGE_DOCUMENT", result.getCandidateDocumentType());
    }
}
