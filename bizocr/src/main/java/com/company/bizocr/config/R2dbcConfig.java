package com.company.bizocr.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

@Configuration
@EnableR2dbcRepositories(basePackages = "com.company.bizocr.repository")
public class R2dbcConfig {
    // Phần lớn cấu hình (URL, User, Pass) đã được Spring Boot tự động lo nhờ application.yaml.
    // Annotation @EnableR2dbcRepositories giúp Spring quét chính xác thư mục chứa DocumentRepository.
    // File này được tạo sẵn để bạn dễ dàng mở rộng các tính năng như Auditing (tự động điền ngày tạo/cập nhật) trong các Phase sau.
}