package com.company.bizocr.company;

import com.company.bizocr.client.TaxApiClient;
import com.company.bizocr.model.dto.CompanyInfo;
import com.company.bizocr.model.dto.CompanySuggestion;
import com.company.bizocr.model.dto.response.TaxLookupResponse;
import com.company.bizocr.model.entity.Company;
import com.company.bizocr.model.entity.CompanyAlias;
import com.company.bizocr.model.entity.CompanyVerificationSource;
import com.company.bizocr.model.entity.CompanyVerificationStatus;
import com.company.bizocr.repository.CompanyAliasRepository;
import com.company.bizocr.repository.CompanyRepository;
import com.company.bizocr.service.ExternalTaxSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
1. Tìm Mã số thuế trong text -> khớp DB theo taxCode
2. Tìm Mã công ty trong text -> khớp DB theo companyCode
3. Chuẩn hóa tên công ty -> khớp normalized_name / alias trong DB
4. Không có trong DB nội bộ -> Fallback: Dùng TaxApiClient (nếu có MST) -> Nếu không có MST, gọi External (Google Search) lấy MST -> Gọi TaxApiClient
5. Vẫn không tra được MST -> tạo Company UNVERIFIED để tự học + hiển thị gợi ý người duyệt
*/

@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyResolverService {

    private final CompanyRepository companyRepository;
    private final CompanyAliasRepository companyAliasRepository;
    private final CompanyNameNormalizer nameNormalizer;
    private final TaxApiClient taxApiClient;
    private final ExternalTaxSearchService externalTaxSearchService;
    private final CompanyFinderService companyFinderService;

    private static final Pattern TAX_CODE_PATTERN =
            Pattern.compile(
                    "(?i)(?:MÃ\\s*SỐ\\s*THUẾ|MA\\s*SO\\s*THUE|MST|TAX\\s*CODE)\\s*[:\\-]?\\s*([0-9]{10}(?:\\s*[-/]\\s*[0-9]{3})?)"
            );

    private static final Pattern COMPANY_CODE_PATTERN =
            Pattern.compile(
                    "(?i)(?:MÃ\\s*CÔNG\\s*TY|MA\\s*CONG\\s*TY|MÃ\\s*ĐƠN\\s*VỊ|MA\\s*DON\\s*VI|COMPANY\\s*CODE)\\s*[:\\-]?\\s*([A-Z0-9\\-_/]+)"
            );

    private static final Pattern TITLE_PATTERN =
            Pattern.compile(
                    "(?i)\\b(giám đốc|tổng giám đốc|ceo|trưởng phòng|quản lý|manager|director|kế toán trưởng)\\b"
            );

    public Mono<CompanyInfo> resolve(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return Mono.just(notFound("EMPTY_OCR"));
        }
        String taxCode = extractTaxCode(rawText);
        if (!taxCode.isBlank()) {
            return companyRepository
                    .findByTaxCode(taxCode)
                    .filter(this::isActive)
                    .map(company -> toCompanyInfo(company, 1.0, "TAX_CODE"))
                    .switchIfEmpty(Mono.defer(() -> resolveByCompanyCodeOrName(rawText)));
        }
        return resolveByCompanyCodeOrName(rawText);
    }

    private Mono<CompanyInfo> resolveByCompanyCodeOrName(String rawText) {
        String companyCode = extractCompanyCode(rawText);
        if (!companyCode.isBlank()) {
            return companyRepository
                    .findByCompanyCode(companyCode)
                    .filter(this::isActive)
                    .map(company -> toCompanyInfo(company, 1.0, "COMPANY_CODE"))
                    .switchIfEmpty(Mono.defer(() -> resolveByName(rawText)));
        }
        return resolveByName(rawText);
    }

    private Mono<CompanyInfo> resolveByName(String rawText) {
        String companyName = extractCompanyName(rawText);
        if (companyName.isBlank()) {
            return Mono.just(notFound("NAME_NOT_FOUND"));
        }
        String normalizedName = nameNormalizer.normalize(companyName);

        // Dùng CompanyFinderService: chịu được nhiều dòng trùng normalized_name,
        // bỏ qua tên rỗng, tự fallback alias bên trong.
        return companyFinderService
                .findBestByNameOrAlias(normalizedName)
                .filter(this::isActive)
                .map(company -> toCompanyInfo(company, 0.98, "NAME_EXACT"))
                .switchIfEmpty(Mono.defer(() -> resolveViaExternalSources(rawText, companyName, normalizedName)));
    }


    /**
     * Luồng Fallback API ngoài:
     * - Có MST từ OCR -> VietQR/TaxAPI.
     * - Không có MST -> Dùng Google Search tìm MST -> Có MST -> VietQR/TaxAPI.
     */
    private Mono<CompanyInfo> resolveViaExternalSources(String rawText, String companyName, String normalizedName) {
        String taxCodeFromText = extractTaxCode(rawText);

        if (!taxCodeFromText.isBlank()) {
            log.info("[CompanyResolver] Gọi Tax API từ MST OCR: {}", taxCodeFromText);
            return callTaxApiAndSave(taxCodeFromText, companyName, rawText, normalizedName, CompanyVerificationSource.VIETQR_DIRECT);
        }

        log.info("[CompanyResolver] Không có MST. Fallback tìm MST qua External Search cho: {}", companyName);

        return externalTaxSearchService.searchTaxCodeOnline(companyName, null)
                .onErrorResume(e -> {
                    log.warn("[CompanyResolver] External Search bị lỗi mạng/timeout: {}", e.getMessage());
                    return Mono.empty();
                })
                .flatMap(searchedTaxCode -> {
                    log.info("[CompanyResolver] Đã tìm thấy MST qua Search: {}", searchedTaxCode);
                    return callTaxApiAndSave(searchedTaxCode, companyName, rawText, normalizedName, CompanyVerificationSource.WEB_SEARCH_VIETQR);
                })
                .switchIfEmpty(Mono.defer(() -> createUnverified(companyName, normalizedName)));
    }

    private Mono<CompanyInfo> callTaxApiAndSave(String taxCode, String companyName, String rawAlias, String normalizedName, CompanyVerificationSource source) {
        return taxApiClient.lookup(taxCode, companyName)
                .onErrorResume(e -> {
                    log.warn("[CompanyResolver] Tax API bị lỗi với MST {}: {}", taxCode, e.getMessage());
                    return Mono.empty();
                })
                .flatMap(taxLookup -> createOrUpgradeVerified(taxLookup, rawAlias, source))
                .switchIfEmpty(Mono.defer(() -> createUnverified(companyName, normalizedName)));
    }

    private Mono<CompanyInfo> createOrUpgradeVerified(TaxLookupResponse taxLookup, String rawAlias, CompanyVerificationSource source) {
        String normalizedTaxCode = nameNormalizer.normalizeTaxCode(taxLookup.getTaxCode());

        return companyRepository.findByTaxCode(normalizedTaxCode)
                .defaultIfEmpty(Company.builder().active(true).createdAt(LocalDateTime.now().toString()).build())
                .flatMap(company -> {
                    company.setTaxCode(normalizedTaxCode);
                    if (taxLookup.getLegalName() != null && !taxLookup.getLegalName().isBlank()) {
                        company.setLegalName(taxLookup.getLegalName());
                        company.setNormalizedName(nameNormalizer.normalize(taxLookup.getLegalName()));
                    }
                    if (taxLookup.getAddress() != null && !taxLookup.getAddress().isBlank()) {
                        company.setAddress(taxLookup.getAddress());
                    }
                    company.setActive(true);
                    company.setVerificationStatus(CompanyVerificationStatus.VERIFIED);
                    company.setVerificationSource(source);
                    company.setMatchConfidence(1.0);
                    company.setVerifiedAt(LocalDateTime.now());
                    company.setUpdatedAt(LocalDateTime.now().toString());
                    return companyRepository.save(company);
                })
                .flatMap(saved -> selfLearnAlias(saved, rawAlias))
                .map(saved -> toCompanyInfo(saved, 1.0, source.name()));
    }

    private Mono<CompanyInfo> createUnverified(String companyName, String normalizedName) {
        log.warn("[CompanyResolver] Không xác thực được qua API, tạo UNVERIFIED cho: {}", companyName);

        Company unverified = Company.builder()
                .legalName(companyName)
                .normalizedName(normalizedName)
                .active(true)
                .verificationStatus(CompanyVerificationStatus.UNVERIFIED)
                .verificationSource(CompanyVerificationSource.OCR_ONLY)
                .matchConfidence(null)
                .verifiedAt(null)
                .createdAt(LocalDateTime.now().toString())
                .build();

        // saveOrFetch: phòng race condition khi nhiều pipeline xử lý cùng lúc
        return companyFinderService.saveOrFetch(unverified)
                .map(saved -> toCompanyInfo(saved, 0.5, "OCR_ONLY"));
    }


    private Mono<Company> selfLearnAlias(Company company, String rawAlias) {
        if (rawAlias == null || rawAlias.isBlank() || rawAlias.equalsIgnoreCase(company.getLegalName())) {
            return Mono.just(company);
        }

        CompanyAlias alias = new CompanyAlias();
        alias.setCompanyId(company.getId());
        alias.setAliasName(rawAlias);
        alias.setNormalizedAlias(nameNormalizer.normalize(rawAlias));
        alias.setSource("AUTO_RESOLVER");
        alias.setConfidenceScore(0.8);
        alias.setCreatedAt(LocalDateTime.now().toString());

        return companyAliasRepository.save(alias)
                .doOnSuccess(a -> log.info("[Self-Learning] Đã học Alias '{}' cho Company ID: {}", rawAlias, company.getId()))
                .thenReturn(company)
                .onErrorResume(e -> {
                    log.warn("[Self-Learning] Bỏ qua Alias trùng cho Company: {}", e.getMessage());
                    return Mono.just(company);
                });
    }

    private String extractTaxCode(String rawText) {
        Matcher matcher = TAX_CODE_PATTERN.matcher(rawText);
        if (!matcher.find()) return "";
        return nameNormalizer.normalizeTaxCode(matcher.group(1));
    }

    private String extractCompanyCode(String rawText) {
        Matcher matcher = COMPANY_CODE_PATTERN.matcher(rawText);
        if (!matcher.find()) return "";
        return nameNormalizer.normalizeCompanyCode(matcher.group(1));
    }

    private String extractCompanyName(String rawText) {
        for (String line : rawText.split("\\R")) {
            String value = line.trim();
            if (value.isBlank()) continue;
            String upper = value.toUpperCase();
            if (upper.contains("CÔNG TY") || upper.contains("CONG TY")
                    || upper.startsWith("CTY ") || upper.contains(" COMPANY")
                    || upper.contains(" CORPORATION")) {
                return value;
            }
        }
        return "";
    }

    private String extractContactTitle(String rawText) {
        if (rawText == null || rawText.isBlank()) return "";
        for (String line : rawText.split("\\R")) {
            String value = line.trim();
            if (TITLE_PATTERN.matcher(value).find()) return value;
        }
        return "";
    }

    private String extractContactName(String rawText) {
        if (rawText == null || rawText.isBlank()) return "";
        String[] lines = rawText.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (TITLE_PATTERN.matcher(line).find()) {
                if (i > 0) {
                    String possibleName = lines[i - 1].trim();
                    if (!possibleName.toUpperCase().contains("CÔNG TY") && possibleName.length() < 50) {
                        return possibleName;
                    }
                }
                break;
            }
        }
        return "";
    }

    private boolean isActive(Company company) {
        return !Boolean.FALSE.equals(company.getActive());
    }

    private CompanyInfo toCompanyInfo(Company company, double score, String matchedBy) {
        return CompanyInfo.builder()
                .companyId(company.getId())
                .companyCode(company.getCompanyCode())
                .taxCode(company.getTaxCode())
                .companyName(company.getLegalName())
                .legalName(company.getLegalName())
                .normalizedName(company.getNormalizedName())
                .matchScore(score)
                .matchedBy(matchedBy)
                .found(true)
                .verificationStatus(company.getVerificationStatus() != null ? company.getVerificationStatus().name() : null)
                .build();
    }

    private CompanyInfo notFound(String reason) {
        return CompanyInfo.builder()
                .matchScore(0.0)
                .matchedBy(reason)
                .found(false)
                .verificationStatus(null)
                .build();
    }

    public CompanySuggestion suggestFields(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return new CompanySuggestion("", "", "", "");
        }
        return new CompanySuggestion(
                extractTaxCode(rawText),
                extractCompanyName(rawText),
                extractContactName(rawText),
                extractContactTitle(rawText)
        );
    }
}