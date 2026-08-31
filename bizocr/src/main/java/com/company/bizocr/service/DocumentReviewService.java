package com.company.bizocr.service;

import com.company.bizocr.client.PythonMlClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.company.bizocr.model.entity.Document;
import com.company.bizocr.repository.DocumentRepository;
import org.springframework.beans.factory.annotation.Value;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentReviewService {

    private final DocumentRepository documentRepository;
    private final PythonMlClient pythonMlClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${bizocr.classification.ai-confidence-threshold}")
    private double confidenceThreshold; // Spring set giá trị này qua field injection, không cần đưa vào constructor

    /**
     * Phê duyệt chứng từ & kích hoạt đồng bộ dữ liệu (Collect + Extract + Anomaly)
     */
    public Mono<Void> approveDocument(Long documentId, String approvedType, String reviewedBy) {
        return documentRepository.findById(documentId)
                .flatMap(doc -> {
                    doc.setDocumentType(approvedType);
                    doc.setStatus("APPROVED");
                    doc.setReviewedAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                    doc.setReviewedBy(reviewedBy); // Gán người dùng thực tế từ Security Context

                    return documentRepository.save(doc)
                            .flatMap(savedDoc -> {
                                log.info("[Review] Đã duyệt chứng từ #{}, kích hoạt thu thập & bóc tách...", documentId);

                                // 1. Gửi dữ liệu về Python để gom label (Collect data)
                                Mono<Void> collectMono = pythonMlClient.collectApprovedData(savedDoc.getRawOcrText(), approvedType)
                                        .onErrorResume(e -> {
                                            log.error("[Collect] Bỏ qua lỗi gửi data sang Python: {}", e.getMessage());
                                            return Mono.empty();
                                        });

                                // 2. Kích hoạt bóc tách & phân tích bất thường tự động
                                Mono<Document> extractMono = extractAndSaveFields(savedDoc.getId());

                                // Chạy song song cả 2 tác vụ
                                return Mono.when(collectMono, extractMono);
                            });
                });
    }

    /**
     * Bóc tách thông tin và gọi AI phân tích bất thường
     */
    public Mono<Document> extractAndSaveFields(Long documentId) {
        return documentRepository.findById(documentId)
                .flatMap(doc -> {
                    if (doc.getRawOcrText() == null || doc.getRawOcrText().isBlank()) {
                        return Mono.just(doc);
                    }

                    // Gọi Python bóc tách Regex/NLP
                    return pythonMlClient.extractFields(doc.getRawOcrText(), doc.getDocumentType())
                            .flatMap(extractedJsonStr -> processExtractedJson(doc, extractedJsonStr))
                            .onErrorResume(e -> {
                                log.error("[Extract] Bóc tách lỗi ở chứng từ #{}, giữ nguyên field cũ. Lỗi: {}", doc.getId(), e.getMessage());
                                // Fallback: Truyền null để hàm processExtractedJson tự lấy data cũ trong DB
                                return processExtractedJson(doc, null);
                            });
                });
    }

    /**
     * Hàm phụ trợ: Xử lý JSON bóc tách được và kiểm tra Anomaly
     */
    private Mono<Document> processExtractedJson(Document doc, String extractedJsonStr) {
        try {
            ObjectNode extractedJson;

            if (extractedJsonStr != null && !extractedJsonStr.trim().isEmpty() && !extractedJsonStr.equals("{}")) {
                extractedJson = (ObjectNode) objectMapper.readTree(extractedJsonStr);
            } else {
                String existingFields = doc.getExtractedFields();
                extractedJson = (existingFields != null && !existingFields.trim().isEmpty())
                        ? (ObjectNode) objectMapper.readTree(existingFields)
                        : objectMapper.createObjectNode();
            }

            // --- TÍNH NĂNG MỚI: Kiểm tra Field Confidence ---
            if (extractedJson.has("field_confidences")) {
                JsonNode confidences = extractedJson.get("field_confidences");
                boolean hasLowConfidence = false;

                Iterator<String> fieldNames = confidences.fieldNames();
                while (fieldNames.hasNext()) {
                    String fieldName = fieldNames.next();
                    double confidence = confidences.get(fieldName).asDouble();
                    if (confidence < confidenceThreshold) {
                        hasLowConfidence = true;
                        log.info("[Confidence] Field '{}' có độ tin cậy thấp ({}). Cần người duyệt cho chứng từ #{}",
                                fieldName, confidence, doc.getId());
                        break;
                    }
                }

                // Nếu điểm thấp, gán cờ NEED_REVIEW (trừ khi nó đã bị dính cảnh báo ANOMALY nặng hơn)
                if (hasLowConfidence && !"WARNING_ANOMALY".equals(doc.getStatus())) {
                    doc.setStatus("NEED_REVIEW");
                }
            }
            // ------------------------------------------------

            // Luồng Anomaly Detection hiện tại (Giữ nguyên)
            if (extractedJson.has("total_amount")) {
                double currentAmount = extractedJson.get("total_amount").asDouble();
                String taxCode = extractedJson.has("tax_code") ? extractedJson.get("tax_code").asText() : "";

                return documentRepository.findHistoricalAmountsAndCompanyId(doc.getDocumentType(), taxCode, doc.getCompanyId())
                        .collectList()
                        .flatMap(historicalAmounts -> {
                            List<Double> history = historicalAmounts.isEmpty() ? List.of(currentAmount) : historicalAmounts;
                            return pythonMlClient.analyzeAnomaly(currentAmount, history);
                        })
                        .flatMap(anomalyResult -> {
                            if (anomalyResult != null && !anomalyResult.isEmpty()) {
                                extractedJson.set("anomaly_analysis", anomalyResult);
                                if (anomalyResult.has("is_anomaly") && anomalyResult.get("is_anomaly").asBoolean()) {
                                    doc.setStatus("WARNING_ANOMALY"); // Anomaly ghi đè NEED_REVIEW vì tính nghiêm trọng cao hơn
                                }
                            }
                            doc.setExtractedFields(extractedJson.toString());
                            return documentRepository.save(doc);
                        });
            } else {
                doc.setExtractedFields(extractedJson.toString());
                return documentRepository.save(doc);
            }
        } catch (Exception e) {
            log.error("[Extract] Lỗi parse JSON bóc tách cho chứng từ #{}: {}", doc.getId(), e.getMessage());
            return documentRepository.save(doc);
        }
    }
}