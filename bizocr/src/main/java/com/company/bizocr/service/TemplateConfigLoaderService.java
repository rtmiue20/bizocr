package com.company.bizocr.service;

import com.company.bizocr.classify.DocumentTemplate;
import com.company.bizocr.classify.TemplateMatcher;
import com.company.bizocr.model.entity.TemplateConfig;
import com.company.bizocr.model.entity.TemplateFieldConfig;
import com.company.bizocr.repository.TemplateConfigRepository;
import com.company.bizocr.repository.TemplateFieldConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nạp danh mục Template Matching động (bảng template_config / template_field_config,
 * seed từ Excel) vào:
 * 1. TemplateMatcher — để ClassifierService nhận diện chứng từ theo Anchors, CỘNG THÊM
 *    vào (không thay thế) 8 loại hardcode có sẵn trong initStandardTemplates().
 * 2. Cache nội bộ — để FieldExtractorService tra cứu field cần bóc tách theo regex.
 *
 * Cho phép mở rộng danh mục chứng từ mới mà KHÔNG cần sửa/deploy lại code Java:
 * chỉ cần thêm dòng vào 2 bảng DB rồi gọi reload() (hoặc restart app).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateConfigLoaderService {

    // Priority thấp hơn các template hardcode trong TemplateMatcher (8-10) để khi 2 bên
    // cùng khớp 1 văn bản, template hardcode (đã tune kỹ) được ưu tiên hơn.
    private static final int DYNAMIC_TEMPLATE_PRIORITY = 5;
    private static final double DYNAMIC_TEMPLATE_CONFIDENCE_THRESHOLD = 0.70;

    private final TemplateConfigRepository templateConfigRepository;
    private final TemplateFieldConfigRepository templateFieldConfigRepository;
    private final TemplateMatcher templateMatcher;

    private final Map<String, List<TemplateFieldConfig>> fieldConfigCache = new ConcurrentHashMap<>();

    @EventListener(ApplicationReadyEvent.class)
    public void loadOnStartup() {
        reload().subscribe(
                unused -> {},
                error -> log.error("[TemplateConfigLoader] Nạp cấu hình Template từ DB thất bại", error),
                () -> log.info("[TemplateConfigLoader] Đã nạp xong danh mục Template động từ DB, {} loại chứng từ",
                        fieldConfigCache.size())
        );
    }

    /**
     * Nạp lại toàn bộ cấu hình từ DB. Có thể gọi thủ công (ví dụ từ 1 endpoint quản trị sau này)
     * để cập nhật danh mục ngay lập tức mà không cần restart ứng dụng.
     */
    public Mono<Void> reload() {
        log.info("[TemplateConfigLoader] Đang dọn dẹp cache và nạp lại cấu hình Template từ DB...");

        // 1. Dọn dẹp cache RAM cục bộ và trong TemplateMatcher
        fieldConfigCache.clear();
        templateMatcher.clearDynamicTemplates();

        // 2. Nạp lại các cấu hình ACTIVE từ DB
        return templateConfigRepository.findByStatus("ACTIVE")
                .flatMap(config ->
                        templateFieldConfigRepository.findByDocTypeCodeOrderByDisplayOrder(config.getDocTypeCode())
                                .collectList()
                                .doOnNext(fields -> {
                                    fieldConfigCache.put(config.getDocTypeCode(), fields);
                                    templateMatcher.registerTemplate(toDocumentTemplate(config));
                                    log.info("[TemplateConfigLoader] Đã đăng ký chứng từ động: {} ({}), {} field",
                                            config.getDocTypeCode(), config.getDisplayName(), fields.size());
                                })
                )
                .then();
    }

    /**
     * Danh sách field cần bóc tách cho 1 loại chứng từ, dùng bởi FieldExtractorService.
     * Trả về list rỗng nếu loại chứng từ này chưa được cấu hình qua DB
     * (khi đó FieldExtractorService sẽ tự rơi về logic regex hardcode như cũ).
     */
    public List<TemplateFieldConfig> getFieldConfigs(String docTypeCode) {
        if (docTypeCode == null) {
            return List.of();
        }
        return fieldConfigCache.getOrDefault(docTypeCode, List.of());
    }

    private DocumentTemplate toDocumentTemplate(TemplateConfig config) {
        List<String> anchors = config.getAnchors() == null
                ? List.of()
                : Arrays.stream(config.getAnchors().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .toList();

        return DocumentTemplate.builder()
                .documentType(config.getDocTypeCode())
                .displayName(config.getDisplayName())
                .category("DYNAMIC_DB")
                .priority(DYNAMIC_TEMPLATE_PRIORITY)
                .confidenceThreshold(DYNAMIC_TEMPLATE_CONFIDENCE_THRESHOLD)
                .primaryKeywords(anchors)
                .build();
    }
}