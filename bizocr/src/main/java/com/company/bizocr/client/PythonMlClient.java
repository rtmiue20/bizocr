package com.company.bizocr.client;

import tools.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class PythonMlClient {

    private final WebClient webClient;
    private final String adminToken;

    public PythonMlClient(
            WebClient.Builder webClientBuilder,
            @Value("${bizocr.python-service.url:${python.ml.url:http://localhost:8000}}") String mlUrl,
            @Value("${bizocr.security.internal-token:${INTERNAL_TOKEN:dev-internal-token}}") String internalToken,
            @Value("${bizocr.security.admin-token:${ADMIN_TOKEN:dev-admin-token}}") String adminToken) {

        this.adminToken = adminToken;
        // Tự động gắn X-Internal-Token vào tất cả các request qua defaultHeader
        this.webClient = webClientBuilder
                .baseUrl(mlUrl)
                .defaultHeader("X-Internal-Token", internalToken)
                .build();
    }

    /**
     * 1. Gọi sang Python để dự đoán loại chứng từ (Predict)
     */
    public Mono<JsonNode> predictDocumentType(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return Mono.empty();
        }

        Map<String, String> body = Map.of("rawText", rawText);

        return webClient.post()
                .uri("/api/v1/ml/predict")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .onErrorResume(Throwable.class, e -> {
                    log.error("[ML Client] Lỗi khi gọi API dự đoán (Predict): {}", e.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * 2. Kích hoạt Python bắt đầu huấn luyện lô {@code batchId}.
     * Yêu cầu cả X-Internal-Token (mặc định) và X-Admin-Token.
     */
    public Mono<Void> triggerLearnBatch(Long batchId) {
        if (batchId == null) {
            return Mono.error(new IllegalArgumentException("[ML Client] triggerLearnBatch: batchId không được null"));
        }

        Map<String, Long> body = Map.of("batch_id", batchId);

        return webClient.post()
                .uri("/api/v1/ml/learn-batch")
                .header("X-Admin-Token", adminToken)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(Duration.ofMinutes(5))
                .doOnSuccess(v -> log.info("[ML Client] Python đã nhận lệnh train lô batchId={}", batchId))
                .doOnError(e -> log.error("[ML Client] Lỗi khi kích hoạt train lô batchId={}: {}", batchId, e.getMessage()));
    }

    /**
     * 3. Kích hoạt nạp lại mô hình ML & từ điển chuẩn hóa (Reload API).
     * Yêu cầu cả X-Internal-Token (mặc định) và X-Admin-Token.
     */
    public Mono<JsonNode> triggerApiReload() {
        return webClient.post()
                .uri("/api/v1/ml/reload")
                .header("X-Admin-Token", adminToken)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .doOnSuccess(res -> log.info("[ML Client] Kích hoạt Reload API Python thành công: {}", res))
                .doOnError(e -> log.error("[ML Client] Lỗi khi kích hoạt Reload API Python: {}", e.getMessage()));
    }

    /**
     * 4. Truy vấn trạng thái tiến trình huấn luyện lô (Learning Status).
     * Yêu cầu cả X-Internal-Token (mặc định) và X-Admin-Token.
     */
    public Mono<JsonNode> getLearningStatus() {
        return webClient.get()
                .uri("/api/v1/ml/learning-status")
                .header("X-Admin-Token", adminToken)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .onErrorResume(Throwable.class, e -> {
                    log.error("[ML Client] Lỗi khi lấy trạng thái tiến trình huấn luyện: {}", e.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * 5. Gọi sang Python để bóc tách các trường thông tin (Extract Fields)
     */
    public Mono<String> extractFields(String rawText, String documentType) {
        if (rawText == null || rawText.isBlank()) {
            return Mono.just("{}");
        }

        Map<String, String> body = Map.of(
                "rawText", rawText,
                "documentType", documentType != null ? documentType : "UNKNOWN"
        );

        return webClient.post()
                .uri("/api/v1/ml/extract")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(jsonNode -> {
                    if (jsonNode != null && jsonNode.has("extractedFields")) {
                        return jsonNode.get("extractedFields").toString();
                    }
                    return "{}";
                })
                .onErrorResume(Throwable.class, e -> {
                    log.error("[ML Client] Lỗi khi gọi API bóc tách dữ liệu (Extract): {}", e.getMessage());
                    return Mono.just("{}");
                });
    }

    /**
     * 6. Phân cụm các chứng từ chưa xác định (Cluster Unknowns)
     */
    public Mono<JsonNode> clusterUnknownDocuments(int nClusters) {
        Map<String, Integer> body = Map.of("n_clusters", nClusters);

        return webClient.post()
                .uri("/api/v1/ml/cluster-unknowns")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .onErrorResume(Throwable.class, e -> {
                    log.error("[ML Client] Lỗi khi gọi K-Means Clustering: {}", e.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * 7. Phân tích bất thường về giá trị (Anomaly Analysis)
     */
    public Mono<JsonNode> analyzeAnomaly(double currentAmount, List<Double> historicalAmounts) {
        Map<String, Object> body = Map.of(
                "current_amount", currentAmount,
                "historical_amounts", historicalAmounts
        );

        return webClient.post()
                .uri("/api/v1/ml/analyze-anomaly")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .onErrorResume(Throwable.class, e -> {
                    log.error("[ML Client] Lỗi khi phân tích Anomaly: {}", e.getMessage());
                    return Mono.empty();
                });
    }
}