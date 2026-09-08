package com.company.bizocr.controller;

import com.company.bizocr.service.TemplateConfigLoaderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/templates")
@RequiredArgsConstructor
@Slf4j
public class TemplateConfigController {

    private final TemplateConfigLoaderService templateConfigLoaderService;

    @PostMapping("/reload")
    public Mono<ResponseEntity<Map<String, Object>>> reloadTemplates() {
        log.info("API request: Nạp lại cấu hình Template Matching từ Database");

        return templateConfigLoaderService.reload()
                .then(Mono.just(ResponseEntity.ok(Map.<String, Object>of(
                        "success", true,
                        "message", "Đã nạp lại danh mục Template và Field từ DB thành công!"
                ))))
                .onErrorResume(e -> {
                    log.error("Lỗi khi nạp lại cấu hình Template: ", e);
                    return Mono.just(ResponseEntity.internalServerError().body(Map.<String, Object>of(
                            "success", false,
                            "message", "Lỗi hệ thống khi nạp lại cấu hình: " + e.getMessage()
                    )));
                });
    }
}