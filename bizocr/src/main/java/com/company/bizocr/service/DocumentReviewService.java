package com.company.bizocr.service;

import com.company.bizocr.client.PythonMlClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.company.bizocr.model.entity.Document;
import com.company.bizocr.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentReviewService {

    private final DocumentRepository documentRepository;
    private final PythonMlClient pythonMlClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

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

            // KIỂM TRA FALLBACK: Nếu Python trả về rỗng/null, dùng lại data đang có trong DB
            if (extractedJsonStr != null && !extractedJsonStr.trim().isEmpty() && !extractedJsonStr.equals("{}")) {
                extractedJson = (ObjectNode) objectMapper.readTree(extractedJsonStr);
            } else {
                String existingFields = doc.getExtractedFields();
                if (existingFields != null && !existingFields.trim().isEmpty()) {
                    extractedJson = (ObjectNode) objectMapper.readTree(existingFields);
                } else {
                    extractedJson = objectMapper.createObjectNode();
                }
            }

            // Nếu có trường total_amount, ta gọi AI kiểm tra bất thường (Anomaly Detection)
            if (extractedJson.has("total_amount")) {
                double currentAmount = extractedJson.get("total_amount").asDouble();
                String taxCode = extractedJson.has("tax_code") ? extractedJson.get("tax_code").asText() : "";

                // 1. Truy vấn lịch sử số tiền các hóa đơn cũ cùng MST và Loại chứng từ từ MariaDB
                return documentRepository.findHistoricalAmounts(doc.getDocumentType(), taxCode)
                        .collectList()
                        .flatMap(historicalAmounts -> {
                            // Nếu doanh nghiệp mới chưa có lịch sử, dùng chính currentAmount để tránh lỗi mảng rỗng
                            List<Double> history = historicalAmounts.isEmpty() ? List.of(currentAmount) : historicalAmounts;

                            // 2. Gửi dữ liệu thực tế sang Python ML để tính toán Z-Score / Anomaly
                            return pythonMlClient.analyzeAnomaly(currentAmount, history);
                        })
                        .flatMap(anomalyResult -> {
                            // 3. Cập nhật kết quả phân tích vào JSON và chuyển status nếu có bất thường
                            if (anomalyResult != null && !anomalyResult.isEmpty()) {
                                extractedJson.set("anomaly_analysis", anomalyResult);

                                if (anomalyResult.has("is_anomaly") && anomalyResult.get("is_anomaly").asBoolean()) {
                                    doc.setStatus("WARNING_ANOMALY");
                                    log.warn("[Anomaly] CẢNH BÁO: Phát hiện số tiền bất thường ở chứng từ #{}", doc.getId());
                                }
                            }

                            doc.setExtractedFields(extractedJson.toString());
                            return documentRepository.save(doc);
                        })
                        .onErrorResume(e -> {
                            log.error("[Anomaly] Lỗi phân tích bất thường: {}", e.getMessage());
                            doc.setExtractedFields(extractedJson.toString());
                            return documentRepository.save(doc);
                        });
            } else {
                // Không có số tiền thì lưu bình thường, không chạy Anomaly
                doc.setExtractedFields(extractedJson.toString());
                return documentRepository.save(doc);
            }
        } catch (Exception e) {
            log.error("[Extract] Lỗi parse JSON bóc tách cho chứng từ #{}: {}", doc.getId(), e.getMessage());
            // Tránh văng exception làm crash Reactive stream
            return documentRepository.save(doc);
        }
    }
}