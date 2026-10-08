package com.company.bizocr.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ExternalTaxSearchService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String internalToken;

    public ExternalTaxSearchService(
            WebClient webClient,
            ObjectMapper objectMapper,
            @Value("${bizocr.security.internal-token:${INTERNAL_TOKEN:dev-internal-token}}") String internalToken) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
        this.internalToken = internalToken;
    }

    /**
     * URL của Python microservice sử dụng DuckDuckGo DDGS.
     *
     * application.properties:
     *
     * bizocr.search.ddg.base-url=http://localhost:8000
     *
     * Docker Compose:
     *
     * bizocr.search.ddg.base-url=http://tax-search:8000
     */
    @Value("${bizocr.search.ddg.base-url:http://localhost:8000}")
    private String duckDuckGoSearchBaseUrl;

    private static final Pattern TAX_CODE_PATTERN =
            Pattern.compile(
                    "\\b[0-9]{10}(?:-[0-9]{3})?\\b"
            );

    private static final Pattern INDUSTRY_CODE_PATTERN =
            Pattern.compile("\\b([0-9]{4})\\b");

    private static final Pattern INDUSTRY_KEYWORD_PATTERN =
            Pattern.compile(
                    "(?i)(ngành\\s*nghề"
                            + "|mã\\s*ngành"
                            + "|nganh\\s*nghe"
                            + "|ma\\s*nganh)"
            );

    private static final int MAX_INDUSTRY_CANDIDATES = 5;

    /**
     * Tìm MST và mã ngành thông qua Python DDGS service.
     *
     * <p>Domain được ưu tiên ở Python service vì thường chính xác hơn
     * tên công ty OCR. MST nhận được vẫn chỉ là ứng viên và phải được
     * verify tiếp qua VietQR hoặc database nội bộ.</p>
     */
    public Mono<ExternalSearchResult> searchCompanyInfoOnline(
            String companyName,
            String domain
    ) {
        if (isBlank(companyName) && isBlank(domain)) {
            log.warn(
                    "[WebSearch] Bỏ qua tìm kiếm vì companyName và domain đều rỗng"
            );
            return Mono.empty();
        }

        Map<String, String> requestBody = new HashMap<>();

        requestBody.put(
                "companyName",
                companyName == null ? "" : companyName.trim()
        );

        if (!isBlank(domain)) {
            requestBody.put("domain", domain.trim());
        }

        log.info(
                "[WebSearch] Đang tra cứu thông tin công ty bằng "
                        + "DuckDuckGo (companyName={}, domain={})",
                companyName,
                domain
        );

        return webClient
                .post()
                .uri(buildDuckDuckGoEndpoint())
                .header("X-Internal-Token", internalToken)
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(
                        status -> status.isError(),
                        response -> response
                                .bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body ->
                                        Mono.error(
                                                new IllegalStateException(
                                                        "DuckDuckGo service returned "
                                                                + response.statusCode()
                                                                + ": "
                                                                + body
                                                )
                                        )
                                )
                )
                .bodyToMono(String.class)
                .map(this::parseDuckDuckGoResult)
                .filter(result ->
                        !isBlank(result.taxCode())
                                || !result.industryCodeCandidates().isEmpty()
                )
                .onErrorResume(error -> {
                    log.error(
                            "[WebSearch] Lỗi gọi DuckDuckGo service: {}",
                            error.getMessage(),
                            error
                    );

                    /*
                     * Không làm hỏng CompanyResolverService.
                     * Khi DDG lỗi, pipeline có thể tiếp tục tìm trong
                     * database nội bộ hoặc xử lý theo fallback khác.
                     */
                    return Mono.empty();
                });
    }

    /**
     * Tương thích ngược với các caller cũ chỉ cần lấy taxCode.
     */
    public Mono<String> searchTaxCodeOnline(
            String companyName,
            String domain
    ) {
        return searchCompanyInfoOnline(companyName, domain)
                .mapNotNull(ExternalSearchResult::taxCode)
                .filter(taxCode -> !taxCode.isBlank());
    }

    /**
     * Parse response từ Python DDGS service.
     */
    private ExternalSearchResult parseDuckDuckGoResult(
            String responseBody
    ) {
        if (isBlank(responseBody)) {
            return new ExternalSearchResult(
                    null,
                    List.of()
            );
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);

            String taxCode = root
                    .path("taxCode")
                    .asText(null);

            List<String> industryCodes =
                    extractIndustryCodesFromResponse(root);

            /*
             * Fallback nếu Python response không có industryCodes
             * nhưng raw response vẫn chứa thông tin ngành nghề.
             */
            if (industryCodes.isEmpty()) {
                industryCodes =
                        extractIndustryCodeCandidates(responseBody);
            }

            /*
             * Fallback nếu Python response không trả taxCode trực tiếp.
             */
            if (isBlank(taxCode)) {
                taxCode = extractTaxCode(responseBody);
            }

            if (!isBlank(taxCode)) {
                log.info(
                        "[WebSearch] Đã nhận MST từ DuckDuckGo: {} "
                                + "(chưa xác thực, sẽ verify qua VietQR)",
                        taxCode
                );
            } else {
                log.warn(
                        "[WebSearch] DuckDuckGo không trả về MST hợp lệ"
                );
            }

            return new ExternalSearchResult(
                    taxCode,
                    industryCodes
            );

        } catch (Exception error) {
            log.warn(
                    "[WebSearch] Không parse được response DuckDuckGo: {}",
                    error.getMessage()
            );

            /*
             * Vẫn thử quét regex nếu response không đúng JSON.
             */
            return new ExternalSearchResult(
                    extractTaxCode(responseBody),
                    extractIndustryCodeCandidates(responseBody)
            );
        }
    }

    /**
     * Đọc danh sách mã ngành từ field industryCodes của Python service.
     */
    private List<String> extractIndustryCodesFromResponse(
            JsonNode root
    ) {
        LinkedHashSet<String> codes =
                new LinkedHashSet<>();

        JsonNode industryCodes =
                root.path("industryCodes");

        if (industryCodes.isArray()) {
            for (JsonNode code : industryCodes) {
                if (code.isTextual()
                        && code.asText().matches("\\d{4}")) {
                    codes.add(code.asText());
                }

                if (codes.size() >= MAX_INDUSTRY_CANDIDATES) {
                    break;
                }
            }
        }

        return List.copyOf(codes);
    }

    /**
     * Quét MST trong raw response nếu Python service không trả field taxCode.
     */
    private String extractTaxCode(String responseBody) {
        if (isBlank(responseBody)) {
            return null;
        }

        Matcher matcher =
                TAX_CODE_PATTERN.matcher(responseBody);

        if (matcher.find()) {
            String foundTaxCode = matcher.group();

            log.info(
                    "[WebSearch] Quét được MST trong response: {} "
                            + "(chưa xác thực)",
                    foundTaxCode
            );

            return foundTaxCode;
        }

        return null;
    }

    /**
     * Quét mã ngành VSIC gần các keyword ngành nghề.
     *
     * <p>Đây chỉ là best effort trên snippet search. Mã ngành
     * phải được xem là ứng viên và chưa được xác thực đầy đủ.</p>
     */
    private List<String> extractIndustryCodeCandidates(
            String searchResponse
    ) {
        if (isBlank(searchResponse)) {
            return List.of();
        }

        LinkedHashSet<String> candidates =
                new LinkedHashSet<>();

        Matcher keywordMatcher =
                INDUSTRY_KEYWORD_PATTERN.matcher(searchResponse);

        while (keywordMatcher.find()
                && candidates.size() < MAX_INDUSTRY_CANDIDATES) {

            int windowStart = keywordMatcher.end();

            int windowEnd = Math.min(
                    searchResponse.length(),
                    windowStart + 200
            );

            String window = searchResponse.substring(
                    windowStart,
                    windowEnd
            );

            Matcher codeMatcher =
                    INDUSTRY_CODE_PATTERN.matcher(window);

            while (codeMatcher.find()
                    && candidates.size() < MAX_INDUSTRY_CANDIDATES) {

                candidates.add(codeMatcher.group(1));
            }
        }

        if (!candidates.isEmpty()) {
            log.info(
                    "[WebSearch] Nhận được {} mã ngành ứng viên "
                            + "từ DuckDuckGo: {}",
                    candidates.size(),
                    candidates
            );
        }

        return List.copyOf(candidates);
    }

    private String buildDuckDuckGoEndpoint() {
        if (duckDuckGoSearchBaseUrl == null
                || duckDuckGoSearchBaseUrl.isBlank()) {
            return "http://localhost:8000/api/tax-search";
        }

        if (duckDuckGoSearchBaseUrl.endsWith("/")) {
            return duckDuckGoSearchBaseUrl
                    + "api/tax-search";
        }

        return duckDuckGoSearchBaseUrl
                + "/api/tax-search";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record ExternalSearchResult(
            String taxCode,
            List<String> industryCodeCandidates
    ) {
    }
}