package com.company.bizocr.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalTaxSearchService {

    private final WebClient webClient;

    @Value("${bizocr.search.google.api-key:}")
    private String googleApiKey;

    @Value("${bizocr.search.google.cx:}")
    private String googleSearchEngineId;

    private static final Pattern TAX_CODE_PATTERN = Pattern.compile("\\b[0-9]{10}(-[0-9]{3})?\\b");

    // Mã ngành VSIC: 4 chữ số, thường xuất hiện gần từ khóa "ngành nghề"/"mã ngành".
    // Mức A (shallow): chỉ bắt được nếu snippet Google tình cờ chứa nó, KHÔNG đảm bảo đầy đủ danh sách ngành.
    private static final Pattern INDUSTRY_CODE_PATTERN = Pattern.compile("\\b([0-9]{4})\\b");
    private static final Pattern INDUSTRY_KEYWORD_PATTERN =
            Pattern.compile("(?i)(ngành\\s*nghề|mã\\s*ngành|nganh\\s*nghe|ma\\s*nganh)");

    private static final int MAX_INDUSTRY_CANDIDATES = 5;

    /**
     * Trả về đồng thời taxCode và danh sách mã ngành nghi nhận được, từ CÙNG 1 lần gọi Search API
     * (không gọi 2 lần để không tốn gấp đôi quota 100 query/ngày miễn phí).
     */
    public Mono<ExternalSearchResult> searchCompanyInfoOnline(String companyName, String domain) {
        if (isBlank(companyName) && isBlank(domain)) {
            return Mono.empty();
        }
        if (isBlank(googleApiKey) || isBlank(googleSearchEngineId)) {
            log.warn("[WebSearch] Chưa cấu hình Google Search API key/cx, bỏ qua bước Search fallback.");
            return Mono.empty();
        }

        String query = String.format("\"%s\" %s \"mã số thuế\" \"ngành nghề\"",
                companyName != null ? companyName : "",
                domain != null ? domain : "").trim();

        log.info("[WebSearch] Đang truy vết thông tin công ty trên Internet với từ khóa: {}", query);

        return webClient.get()
                .uri("https://customsearch.googleapis.com/customsearch/v1", uriBuilder -> uriBuilder
                        .queryParam("key", googleApiKey)
                        .queryParam("cx", googleSearchEngineId)
                        .queryParam("q", query)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseSearchResult)
                .onErrorResume(e -> {
                    log.error("[WebSearch] Lỗi gọi Search API: {}", e.getMessage());
                    return Mono.empty();
                });
    }

    /** Giữ lại cho các chỗ gọi cũ chỉ cần taxCode (VD CompanyResolverService fallback theo MST trực tiếp). */
    public Mono<String> searchTaxCodeOnline(String companyName, String domain) {
        return searchCompanyInfoOnline(companyName, domain)
                .mapNotNull(ExternalSearchResult::taxCode)
                .filter(tc -> !tc.isBlank());
    }

    private ExternalSearchResult parseSearchResult(String searchJsonResponse) {
        String taxCode = extractTaxCode(searchJsonResponse);
        List<String> industryCodes = extractIndustryCodeCandidates(searchJsonResponse);
        return new ExternalSearchResult(taxCode, industryCodes);
    }

    private String extractTaxCode(String searchJsonResponse) {
        Matcher matcher = TAX_CODE_PATTERN.matcher(searchJsonResponse);
        if (matcher.find()) {
            String foundTaxCode = matcher.group();
            log.info("[WebSearch] Đã truy vết MST (chưa xác thực, sẽ verify qua VietQR): {}", foundTaxCode);
            return foundTaxCode;
        }
        return null;
    }

    /**
     * Mức A - best effort: chỉ quét các cụm 4 chữ số nằm gần từ khóa "ngành nghề"/"mã ngành"
     * trong toàn bộ response JSON (bao gồm title + snippet của các kết quả trả về).
     * KHÔNG đảm bảo đầy đủ — Google snippet thường bị cắt ngắn nên nhiều khi không bắt được gì cả,
     * đó là giới hạn đã biết trước của mức A. Muốn chính xác hơn cần fetch + parse nguyên trang (mức B).
     */
    private List<String> extractIndustryCodeCandidates(String searchJsonResponse) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        Matcher keywordMatcher = INDUSTRY_KEYWORD_PATTERN.matcher(searchJsonResponse);

        while (keywordMatcher.find() && candidates.size() < MAX_INDUSTRY_CANDIDATES) {
            int windowStart = keywordMatcher.end();
            int windowEnd = Math.min(searchJsonResponse.length(), windowStart + 200); // cửa sổ 200 ký tự sau từ khóa
            String window = searchJsonResponse.substring(windowStart, windowEnd);

            Matcher codeMatcher = INDUSTRY_CODE_PATTERN.matcher(window);
            while (codeMatcher.find() && candidates.size() < MAX_INDUSTRY_CANDIDATES) {
                candidates.add(codeMatcher.group(1));
            }
        }

        if (!candidates.isEmpty()) {
            log.info("[WebSearch] Nghi nhận {} mã ngành ứng viên (mức A, chưa xác thực): {}", candidates.size(), candidates);
        }
        return List.copyOf(candidates);
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    public record ExternalSearchResult(String taxCode, List<String> industryCodeCandidates) {}
}