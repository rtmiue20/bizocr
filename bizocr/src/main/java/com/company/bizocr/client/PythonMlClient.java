package com.company.bizocr.client;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class PythonMlClient {

    private final WebClient webClient;

    // Khởi tạo WebClient trỏ tới cổng 8000 của Python
    public PythonMlClient(WebClient.Builder webClientBuilder,
                          @Value("${python.ml.url:http://localhost:8000}") String mlUrl) {
        this.webClient = webClientBuilder.baseUrl(mlUrl).build();
    }

    /**
     * 1. Gọi sang Python để dự đoán loại chứng từ (Predict)
     */
    public Mono<JsonNode> predictDocumentType(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return Mono.empty(); // Trả về Mono rỗng nếu không có text
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
     * 2. Gọi sang Python để thu thập dữ liệu huấn luyện (Collect)
     */
    public Mono<Void> collectApprovedData(String rawText, String approvedType) {
        if (rawText == null || rawText.isBlank() || approvedType == null) {
            return Mono.empty();
        }

        Map<String, String> body = Map.of(
                "rawText", rawText,
                "label", approvedType
        );

        return webClient.post()
                .uri("/api/v1/ml/collect")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSuccess(v -> log.info("[ML Client] Đã gửi dữ liệu sang Python ML để collect (Label: {})", approvedType))
                .onErrorResume(Throwable.class, e -> {
                    log.error("[ML Client] Lỗi khi gọi API thu thập dữ liệu (Collect): {}", e.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * 3. Gọi sang Python để bóc tách các trường thông tin (Extract Fields)
     */
    public Mono<String> extractFields(String rawText, String documentType) {
        if (rawText == null || rawText.isBlank()) {
            return Mono.just("{}"); // Mặc định trả về JSON rỗng
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
                    // Trích xuất chuỗi JSON từ trường "extractedFields"
                    if (jsonNode != null && jsonNode.has("extractedFields")) {
                        return jsonNode.get("extractedFields").toString();
                    }
                    return "{}";
                })
                .onErrorResume(Throwable.class, e -> {
                    // Bắt chính xác Throwable để lấy được e.getMessage()
                    log.error("[ML Client] Lỗi khi gọi API bóc tách dữ liệu (Extract): {}", e.getMessage());
                    return Mono.just("{}"); // Fallback an toàn, trả về JSON rỗng để không chết luồng
                });
    }

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