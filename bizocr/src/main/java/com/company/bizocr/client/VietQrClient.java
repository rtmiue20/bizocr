package com.company.bizocr.client;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class VietQrClient {

    private final WebClient vietQrWebClient;

    public Mono<JsonNode> lookupByTaxCode(String taxCode) {
        if (taxCode == null || taxCode.isBlank()) {
            return Mono.empty();
        }

        return vietQrWebClient.get()
                .uri("/v2/business/{taxCode}", taxCode)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .filter(res -> res.has("code") && "00".equals(res.get("code").asText()))
                .map(res -> res.get("data"));
    }
}