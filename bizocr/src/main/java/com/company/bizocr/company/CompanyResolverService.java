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
4. Không có trong DB nội bộ -> Fallback: TaxApiClient (nếu OCR có MST) -> ExternalTaxSearchService -> TaxApiClient
5. Vẫn không tra được MST -> tạo Company UNVERIFIED để tự học + hiển thị gợi ý người duyệt
6. Không đủ tín hiệu gì cả -> NEEDS_REVIEW, không tạo gì
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

    private static final Pattern TAX_CODE_PATTERN =
            Pattern.compile(
                    "(?i)(?:MÃ\\s*SỐ\\s*THUẾ"
                            + "|MA\\s*SO\\s*THUE"
                            + "|MST"
                            + "|TAX\\s*CODE)"
                            + "\\s*[:\\-]?\\s*"
                            + "([0-9]{10}(?:\\s*[-/]\\s*[0-9]{3})?)"
            );

    private static final Pattern COMPANY_CODE_PATTERN =
            Pattern.compile(
                    "(?i)(?:MÃ\\s*CÔNG\\s*TY"
                            + "|MA\\s*CONG\\s*TY"
                            + "|MÃ\\s*ĐƠN\\s*VỊ"
                            + "|MA\\s*DON\\s*VI"
                            + "|COMPANY\\s*CODE)"
                            + "\\s*[:\\-]?\\s*"
                            + "([A-Z0-9\\-_/]+)"
            );

    // Regex nhận diện chức danh người liên hệ trên namecard
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
                    .switchIfEmpty(resolveByCompanyCodeOrName(rawText));
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
                    .switchIfEmpty(resolveByName(rawText));
        }
        return resolveByName(rawText);
    }

    private Mono<CompanyInfo> resolveByName(String rawText) {
        String companyName = extractCompanyName(rawText);
        if (companyName.isBlank()) {
            return Mono.just(notFound("NAME_NOT_FOUND"));
        }
        String normalizedName = nameNormalizer.normalize(companyName);

        return companyRepository
                .findByNormalizedName(normalizedName)
                .filter(this::isActive)
                .map(company -> toCompanyInfo(company, 0.98, "NAME_EXACT"))
                .switchIfEmpty(
                        companyAliasRepository
                                .findByNormalizedAlias(normalizedName)
                                .flatMap(alias -> companyRepository.findById(alias.getCompanyId()))
                                .filter(this::isActive)
                                .map(company -> toCompanyInfo(company, 0.95, "ALIAS"))
                )
                // Không có trong DB nội bộ -> thử fallback VietQR/Search trước khi bó tay
                .switchIfEmpty(Mono.defer(() -> resolveViaExternalSources(rawText, companyName, normalizedName)));
    }

    /**
     * Fallback khi DB nội bộ (taxCode/companyCode/name/alias) đều không khớp:
     * 1. Nếu OCR đọc được MST trực tiếp trong text -> tra VietQR -> VERIFIED
     * 2. Nếu không có MST -> Search Google tìm MST theo tên công ty -> tra VietQR -> VERIFIED
     * 3. Vẫn không ra MST -> tạo Company UNVERIFIED để tự học và hiển thị gợi ý người duyệt
     */
    private Mono<CompanyInfo> resolveViaExternalSources(String rawText, String companyName, String normalizedName) {
        String taxCodeFromText = extractTaxCode(rawText);

        Mono<TaxLookupResponse> taxLookupMono = !taxCodeFromText.isBlank()
                ? taxApiClient.lookup(taxCodeFromText, companyName)
                : Mono.empty();

        return taxLookupMono
                .switchIfEmpty(Mono.defer(() ->
                        externalTaxSearchService.searchTaxCodeOnline(companyName, null)
                                .flatMap(searchedTaxCode -> taxApiClient.lookup(searchedTaxCode, companyName))
                ))
                .flatMap(taxLookup -> createOrUpgradeVerified(taxLookup, companyName,
                        !taxCodeFromText.isBlank() ? CompanyVerificationSource.VIETQR_DIRECT : CompanyVerificationSource.WEB_SEARCH_VIETQR))
                .switchIfEmpty(Mono.defer(() -> createUnverified(companyName, normalizedName)));
    }

    private Mono<CompanyInfo> createOrUpgradeVerified(TaxLookupResponse taxLookup, String rawAlias, CompanyVerificationSource source) {
        String normalizedTaxCode = nameNormalizer.normalizeTaxCode(taxLookup.getTaxCode());

        return companyRepository.findByTaxCode(normalizedTaxCode)
                .defaultIfEmpty(Company.builder().active(true).createdAt(LocalDateTime.now().toString()).build())
                .flatMap(company -> {
                    company.setTaxCode(normalizedTaxCode);
                    company.setLegalName(taxLookup.getLegalName());
                    company.setNormalizedName(nameNormalizer.normalize(taxLookup.getLegalName()));
                    company.setAddress(taxLookup.getAddress());
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
        log.warn("[CompanyResolver] Không xác thực được MST qua VietQR/Search, tạo UNVERIFIED cho: {}", companyName);

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

        return companyRepository.save(unverified)
                .map(saved -> toCompanyInfo(saved, 0.5, "OCR_ONLY"));
    }

    private Mono<Company> selfLearnAlias(Company company, String rawAlias) {
        if (rawAlias == null || rawAlias.isBlank()
                || rawAlias.equalsIgnoreCase(company.getLegalName())) {
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
                    log.warn("[Self-Learning] Bỏ qua Alias trùng cho Company ID {}: {}", company.getId(), e.getMessage());
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

    // Trích xuất chức danh người liên hệ (namecard)
    private String extractContactTitle(String rawText) {
        if (rawText == null || rawText.isBlank()) return "";
        for (String line : rawText.split("\\R")) {
            String value = line.trim();
            if (TITLE_PATTERN.matcher(value).find()) {
                return value;
            }
        }
        return "";
    }

    // Trích xuất tên người liên hệ (lấy dòng ngay phía trên chức danh)
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
        log.info("[CompanyResolver] Tìm thấy companyId={}, name={}, matchedBy={}, score={}, verification={}",
                company.getId(), company.getLegalName(), matchedBy, score, company.getVerificationStatus());
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
                .verificationStatus(company.getVerificationStatus() != null
                        ? company.getVerificationStatus().name() : null)
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

    /**
     * Trích xuất các tín hiệu thô từ OCR text để gợi ý cho form nhập liệu.
     * KHÔNG tra DB, KHÔNG match company, KHÔNG gọi VietQR/Search — chỉ đọc pattern trong text.
     */
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