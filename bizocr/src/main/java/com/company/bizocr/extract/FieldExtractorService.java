package com.company.bizocr.extract;

import com.company.bizocr.model.dto.ClassifyResult;
import com.company.bizocr.model.entity.TemplateFieldConfig;
import com.company.bizocr.service.OllamaQueueService;
import com.company.bizocr.service.TemplateConfigLoaderService;
import com.company.bizocr.validator.ExtractionValidatorService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class FieldExtractorService {

    private static final Pattern EMAIL_ARTIFACT_PATTERN = Pattern.compile(
            "([\\w.+-]+)\\s*[oO0]([a-zA-Z0-9-]+)\\s+"
                    + "(vn|com|net|org|edu|gov|info|biz)\\b"
    );

    private static final Pattern WEBSITE_ARTIFACT_PATTERN = Pattern.compile(
            "\\b(?i)www[\\s\\.]*([a-zA-Z0-9-]+)"
                    + "[\\s\\.]+"
                    + "(vn|com|net|org|edu|gov|info|biz)\\b"
    );

    private static final Pattern MARKDOWN_LINK_PATTERN = Pattern.compile(
            "\\[([^\\]]+)\\]\\([^)]+\\)"
    );

    private final ObjectMapper objectMapper;
    private final TemplateConfigLoaderService templateConfigLoaderService;
    private final RegexExtractorService regexExtractorService;
    private final ExtractionValidatorService extractionValidatorService;
    private final OllamaQueueService ollamaQueueService;

    public FieldExtractorService(
            ObjectMapper objectMapper,
            TemplateConfigLoaderService templateConfigLoaderService,
            RegexExtractorService regexExtractorService,
            ExtractionValidatorService extractionValidatorService,
            OllamaQueueService ollamaQueueService
    ) {
        this.objectMapper = objectMapper;
        this.templateConfigLoaderService = templateConfigLoaderService;
        this.regexExtractorService = regexExtractorService;
        this.extractionValidatorService = extractionValidatorService;
        this.ollamaQueueService = ollamaQueueService;
    }

    /**
     * Tương thích ngược với các caller cũ không truyền documentId.
     */
    public Mono<String> extractFields(
            ClassifyResult classifyResult,
            String rawText
    ) {
        return extractFields(
                UUID.randomUUID().toString(),
                classifyResult,
                rawText
        );
    }

    /**
     * Bóc tách dữ liệu theo cơ chế:
     *
     * 1. Dynamic config nếu có.
     * 2. Regex hardcoded nếu không có dynamic config.
     * 3. NAMECARD dùng gate riêng.
     * 4. Chỉ đẩy Ollama queue khi regex thực sự không đủ dữ liệu.
     */
    public Mono<String> extractFields(
            String documentId,
            ClassifyResult classifyResult,
            String rawText
    ) {
        return Mono.fromCallable(() -> {
            if (rawText == null || rawText.isBlank()) {
                return "{}";
            }

            if (classifyResult == null) {
                log.warn(
                        "Không thể bóc tách vì classifyResult bị null "
                                + "(documentId={})",
                        documentId
                );
                return "{}";
            }

            String documentType = classifyResult.getDocumentType();

            Map<String, Object> regexFields;
            boolean regexSuccess;
            boolean isNameCard = isNameCard(documentType);

            /*
             * NAMECARD có schema riêng và không có total_amount.
             *
             * Luôn dùng hardcoded regex cho NAMECARD để:
             * - Bắt đúng contact_name.
             * - Bắt đúng contact_title.
             * - Không phụ thuộc cấu hình DB thiếu hoặc sai.
             * - Không bị gate hóa đơn yêu cầu total_amount.
             */
            List<TemplateFieldConfig> dynamicConfigs = isNameCard
                    ? List.of()
                    : templateConfigLoaderService
                    .getFieldConfigs(documentType);

            double extractionConfidence;

            if (isNameCard) {
                regexFields = regexExtractorService
                        .extractFromHardcodedRegex(
                                documentType,
                                rawText
                        );

                boolean hasContactName = hasTextField(
                        regexFields,
                        "contact_name"
                );

                boolean hasContactTitle = hasTextField(
                        regexFields,
                        "contact_title"
                );

                boolean hasContactChannel =
                        hasTextField(regexFields, "email")
                                || hasTextField(regexFields, "phone")
                                || hasTextField(regexFields, "website");

                boolean hasOrganization =
                        hasTextField(regexFields, "company_name")
                                || hasTextField(regexFields, "tax_code");

                int matchedNameCardSignals = 0;

                if (hasContactName) {
                    matchedNameCardSignals++;
                }

                if (hasContactTitle) {
                    matchedNameCardSignals++;
                }

                if (hasContactChannel) {
                    matchedNameCardSignals++;
                }

                if (hasOrganization) {
                    matchedNameCardSignals++;
                }

                extractionConfidence =
                        (double) matchedNameCardSignals / 4.0;

                /*
                 * NAMECARD đạt khi có:
                 * - Tên người.
                 * - Chức danh.
                 * - Một phương thức liên hệ.
                 * - Tên công ty hoặc mã số thuế.
                 *
                 * Không yêu cầu total_amount.
                 */
                regexSuccess = hasContactName
                        && hasContactTitle
                        && hasContactChannel
                        && hasOrganization;

            } else if (!dynamicConfigs.isEmpty()) {
                /*
                 * Bóc tách bằng cấu hình động trong database.
                 */
                regexFields = regexExtractorService
                        .extractFromDynamicConfig(
                                dynamicConfigs,
                                rawText
                        );

                List<TemplateFieldConfig> requiredConfigs =
                        dynamicConfigs.stream()
                                .filter(cfg ->
                                        Boolean.TRUE.equals(
                                                cfg.getRequired()
                                        )
                                )
                                .toList();

                if (!requiredConfigs.isEmpty()) {
                    long matchedRequiredCount = requiredConfigs
                            .stream()
                            .map(TemplateFieldConfig::getFieldKey)
                            .filter(key ->
                                    regexFields.containsKey(key)
                                            && regexFields.get(key) != null
                            )
                            .count();

                    extractionConfidence =
                            (double) matchedRequiredCount
                                    / requiredConfigs.size();

                    regexSuccess =
                            matchedRequiredCount
                                    == requiredConfigs.size();

                } else {
                    long matchedCount = dynamicConfigs
                            .stream()
                            .map(TemplateFieldConfig::getFieldKey)
                            .filter(key ->
                                    regexFields.containsKey(key)
                                            && regexFields.get(key) != null
                            )
                            .count();

                    extractionConfidence = dynamicConfigs.isEmpty()
                            ? 0.0
                            : (double) matchedCount
                              / dynamicConfigs.size();

                    regexSuccess = !regexFields.isEmpty();
                }

            } else {
                /*
                 * Bóc tách bằng regex hardcoded cho các loại chứng từ khác.
                 */
                regexFields = regexExtractorService
                        .extractFromHardcodedRegex(
                                documentType,
                                rawText
                        );

                boolean hasRequiredIdentifier =
                        regexFields.containsKey("tax_code")
                                || regexFields.containsKey("customer_id")
                                || regexFields.containsKey("service_name")
                                || regexFields.containsKey("party_a")
                                || "SERVICE_PAYMENT_VOUCHER"
                                .equalsIgnoreCase(documentType)
                                || "RECEIPT"
                                .equalsIgnoreCase(documentType)
                                || "SERVICE_BILL"
                                .equalsIgnoreCase(documentType);

                boolean hasAmount =
                        regexFields.containsKey("total_amount")
                                && regexFields.get("total_amount") != null;

                int totalExpected = 2;
                int matchedCount = 0;

                if (hasRequiredIdentifier) {
                    matchedCount++;
                }

                if (hasAmount) {
                    matchedCount++;
                }

                extractionConfidence =
                        (double) matchedCount / totalExpected;

                regexSuccess = hasRequiredIdentifier && hasAmount;
            }

            // ----------------------------------------------------------
            // GATE: Chỉ cho phép regex pass khi confidence đủ cao.
            // ----------------------------------------------------------
            boolean isExtractionConfidenceHigh =
                    extractionConfidence >= 0.80;

            if (regexSuccess && isExtractionConfidenceHigh) {
                log.info(
                        ">> [GATING PASS] Bóc tách thành công qua Regex "
                                + "(documentType={}, confidence={}). "
                                + "Bỏ qua AI.",
                        documentType,
                        extractionConfidence
                );

                String rawJson =
                        objectMapper.writeValueAsString(regexFields);

                String normalizedJson =
                        applyPostExtractionNormalization(rawJson);

                return extractionValidatorService
                        .validateAndSanitize(
                                documentType,
                                normalizedJson,
                                rawText
                        );
            }

            // ----------------------------------------------------------
            // AI FALLBACK: Chỉ dùng khi regex không đủ dữ liệu.
            // ----------------------------------------------------------
            log.warn(
                    ">> [GATING REJECT] Regex không đủ dữ liệu "
                            + "(documentType={}, confidence={}). "
                            + "Đẩy task [{}] vào Redis Queue.",
                    documentType,
                    extractionConfidence,
                    documentId
            );

            ollamaQueueService.enqueueTask(
                    documentId,
                    documentType,
                    rawText
            );

            return objectMapper.writeValueAsString(
                    Map.of(
                            "review_status",
                            "PENDING_AI_WORKER",
                            "document_id",
                            documentId,
                            "message",
                            "Chứng từ đang trong hàng đợi xử lý AI"
                    )
            );

        }).subscribeOn(Schedulers.boundedElastic());
    }

    private String applyPostExtractionNormalization(String json) {
        try {
            JsonNode rootNode = objectMapper.readTree(json);

            if (!rootNode.isObject()) {
                return json;
            }

            ObjectNode objectNode = (ObjectNode) rootNode;
            ObjectNode updatedNode =
                    objectMapper.createObjectNode();

            objectNode.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                JsonNode valueNode = entry.getValue();

                if (valueNode.isTextual()) {
                    String normalized = normalizeExtractedValue(
                            key,
                            valueNode.asText()
                    );

                    updatedNode.put(key, normalized);
                } else {
                    updatedNode.set(key, valueNode);
                }
            });

            return objectMapper.writeValueAsString(updatedNode);

        } catch (Exception error) {
            log.error(
                    "Lỗi khi normalize JSON kết quả",
                    error
            );

            return json;
        }
    }

    private boolean isNameCard(String documentType) {
        return "NAMECARD".equalsIgnoreCase(documentType)
                || "NAME_CARD".equalsIgnoreCase(documentType)
                || "DANH_THIEP".equalsIgnoreCase(documentType);
    }

    private boolean hasTextField(
            Map<String, Object> fields,
            String fieldName
    ) {
        if (fields == null
                || fieldName == null
                || !fields.containsKey(fieldName)) {
            return false;
        }

        Object value = fields.get(fieldName);

        return value != null
                && !value.toString().isBlank();
    }

    private String normalizeExtractedValue(
            String fieldKey,
            String rawValue
    ) {
        if (rawValue == null) {
            return null;
        }

        String value = rawValue;

        if ("website".equalsIgnoreCase(fieldKey)) {
            value = stripMarkdownLink(value);
            value = repairWebsite(value);

        } else if ("email".equalsIgnoreCase(fieldKey)) {
            value = repairEmail(value);
        }

        return value.trim();
    }

    private String repairEmail(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }

        Matcher matcher =
                EMAIL_ARTIFACT_PATTERN.matcher(value);

        return matcher.find()
                ? matcher.replaceAll("$1@$2.$3")
                : value;
    }

    private String repairWebsite(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }

        Matcher matcher =
                WEBSITE_ARTIFACT_PATTERN.matcher(value);

        return matcher.find()
                ? matcher.replaceAll("www.$1.$2").toLowerCase()
                : value;
    }

    private String stripMarkdownLink(String value) {
        if (value == null) {
            return null;
        }

        Matcher matcher =
                MARKDOWN_LINK_PATTERN.matcher(value);

        return matcher.find()
                ? matcher.replaceAll("$1")
                : value;
    }
}