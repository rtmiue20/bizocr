package com.company.bizocr.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    @Value("${bizocr.python-service.url:${ocr.service.url:http://localhost:8000}}")
    private String ocrServiceUrl;

    private HttpClient buildTimeoutHttpClient(int connectTimeoutMillis, int readWriteTimeoutSeconds) {
        return HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMillis)
                .responseTimeout(Duration.ofSeconds(readWriteTimeoutSeconds))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(readWriteTimeoutSeconds, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(readWriteTimeoutSeconds, TimeUnit.SECONDS)));
    }

    @Bean
    public WebClient ocrWebClient() {
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();

        HttpClient httpClient = buildTimeoutHttpClient(10000, 30);

        return WebClient.builder()
                .baseUrl(ocrServiceUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(strategies)
                .build();
    }

    @Primary // Đánh dấu đây là WebClient mặc định cho toàn hệ thống
    @Bean
    public WebClient externalWebClient() {
        HttpClient httpClient = buildTimeoutHttpClient(5000, 10);

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}