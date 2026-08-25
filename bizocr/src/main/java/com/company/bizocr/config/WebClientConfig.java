package com.company.bizocr.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${ocr.service.url:http://localhost:8000}")
    private String ocrServiceUrl;

    @Bean
    public WebClient ocrWebClient() {
        // Tăng giới hạn bộ nhớ buffer lên 16MB cho dữ liệu hình ảnh/JSON lớn
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();

        return WebClient.builder()
                .baseUrl(ocrServiceUrl)
                .exchangeStrategies(strategies)
                .build();
    }
}