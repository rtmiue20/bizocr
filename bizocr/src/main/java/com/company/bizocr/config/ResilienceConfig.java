package com.company.bizocr.config;

import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

// Bảo vệ hệ thống & Giới hạn Rate Limit
@Configuration
public class ResilienceConfig {

    @Bean
    public RateLimiterRegistry rateLimiterRegistry() {
        // Cấu hình giới hạn: Tối đa cho phép gọi OpenAI 50 requests mỗi phút
        RateLimiterConfig openAiConfig = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .limitForPeriod(50)
                .timeoutDuration(Duration.ofSeconds(3)) // Thời gian chờ nếu luồng bị đầy
                .build();

        return RateLimiterRegistry.of(openAiConfig);
    }
}