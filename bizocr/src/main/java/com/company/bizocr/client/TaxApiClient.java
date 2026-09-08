package com.company.bizocr.client;

import com.company.bizocr.model.dto.response.TaxLookupResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class TaxApiClient {

    private final WebClient taxWebClient;

    public Mono<TaxLookupResponse> lookup(
            String taxCode,
            String companyName
    ) {
        return taxWebClient
                .get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/company");

                    if (taxCode != null && !taxCode.isBlank()) {
                        uriBuilder.queryParam("taxCode", taxCode);
                    }

                    if (companyName != null && !companyName.isBlank()) {
                        uriBuilder.queryParam("name", companyName);
                    }

                    return uriBuilder.build();
                })
                .retrieve()
                .onStatus(
                        status -> status.value() == 404,
                        response -> Mono.empty()
                )
                .bodyToMono(TaxLookupResponse.class)
                .timeout(Duration.ofSeconds(5));
    }
}
