package com.company.bizocr.company;

import com.company.bizocr.model.dto.CompanyInfo;
import com.company.bizocr.model.entity.Company;
import com.company.bizocr.model.entity.CompanyAlias;
import com.company.bizocr.repository.CompanyAliasRepository;
import com.company.bizocr.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
1. Tìm Mã số thuế
2. Tìm Mã công ty
3. Chuẩn hóa tên công ty
4. Tìm normalized_name chính xác
5. Tìm trong company_aliases
6. Không tìm thấy → NEEDS_REVIEW
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyResolverService {
    private final CompanyRepository companyRepository;
    private final CompanyAliasRepository companyAliasRepository;
    private final CompanyNameNormalizer nameNormalizer;
    /**
     * Hỗ trợ:
     *
     * MST: 0312345678
     * Mã số thuế: 0312345678
     * MA SO THUE: 0312345678
     */
    private static final Pattern TAX_CODE_PATTERN =
            Pattern.compile(
                    "(?i)(?:MÃ\\s*SỐ\\s*THUẾ"
                            + "|MA\\s*SO\\s*THUE"
                            + "|MST"
                            + "|TAX\\s*CODE)"
                            + "\\s*[:\\-]?\\s*"
                            + "([0-9]{10}(?:\\s*[-/]\\s*[0-9]{3})?)"
            );
    /**
     * Hỗ trợ:
     *
     * Mã công ty: ABC001
     * Ma cong ty: ABC001
     * Company code: ABC001
     */
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
    /**
     * Thứ tự tra cứu:
     *
     * 1. Mã số thuế
     * 2. Mã công ty
     * 3. Tên công ty chuẩn hóa
     * 4. Alias
     * 5. Không tìm thấy
     */
    public Mono<CompanyInfo> resolve(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return Mono.just(notFound("EMPTY_OCR"));
        }
        String taxCode = extractTaxCode(rawText);
        if (!taxCode.isBlank()) {
            return companyRepository
                    .findByTaxCode(taxCode)
                    .filter(this::isActive)
                    .map(company ->
                            toCompanyInfo(
                                    company,
                                    1.0,
                                    "TAX_CODE"
                            )
                    )
                    .switchIfEmpty(
                            resolveByCompanyCodeOrName(rawText)
                    );
        }
        return resolveByCompanyCodeOrName(rawText);
    }
    private Mono<CompanyInfo> resolveByCompanyCodeOrName(
            String rawText
    ) {
        String companyCode = extractCompanyCode(rawText);
        if (!companyCode.isBlank()) {
            return companyRepository
                    .findByCompanyCode(companyCode)
                    .filter(this::isActive)
                    .map(company ->
                            toCompanyInfo(
                                    company,
                                    1.0,
                                    "COMPANY_CODE"
                            )
                    )
                    .switchIfEmpty(
                            resolveByName(rawText)
                    );
        }
        return resolveByName(rawText);
    }
    private Mono<CompanyInfo> resolveByName(String rawText) {
        String companyName = extractCompanyName(rawText);
        if (companyName.isBlank()) {
            return Mono.just(notFound("NAME_NOT_FOUND"));
        }
        String normalizedName =
                nameNormalizer.normalize(companyName);
        return companyRepository
                .findByNormalizedName(normalizedName)
                .filter(this::isActive)
                .map(company ->
                        toCompanyInfo(
                                company,
                                0.98,
                                "NAME_EXACT"
                        )
                )
                .switchIfEmpty(
                        companyAliasRepository
                                .findByNormalizedAlias(normalizedName)
                                .flatMap(alias ->
                                        companyRepository.findById(
                                                alias.getCompanyId()
                                        )
                                )
                                .filter(this::isActive)
                                .map(company ->
                                        toCompanyInfo(
                                                company,
                                                0.95,
                                                "ALIAS"
                                        )
                                )
                )
                .switchIfEmpty(
                        Mono.fromSupplier(() -> {
                            log.warn(
                                    "[CompanyResolver] "
                                            + "Không tìm thấy công ty: {}",
                                    companyName
                            );
                            return CompanyInfo.builder()
                                    .companyName(companyName)
                                    .normalizedName(normalizedName)
                                    .matchScore(0.0)
                                    .matchedBy("NAME_NOT_FOUND")
                                    .found(false)
                                    .build();
                        })
                );
    }
    private String extractTaxCode(String rawText) {
        Matcher matcher =
                TAX_CODE_PATTERN.matcher(rawText);
        if (!matcher.find()) {
            return "";
        }
        return nameNormalizer.normalizeTaxCode(
                matcher.group(1)
        );
    }
    private String extractCompanyCode(String rawText) {
        Matcher matcher =
                COMPANY_CODE_PATTERN.matcher(rawText);
        if (!matcher.find()) {
            return "";
        }
        return nameNormalizer.normalizeCompanyCode(
                matcher.group(1)
        );
    }
    /**
     * Tạm thời lấy dòng có chứa từ khóa công ty.
     * Sau này có thể nâng cấp bằng OCR candidate hoặc AI.
     */
    private String extractCompanyName(String rawText) {
        for (String line : rawText.split("\\R")) {
            String value = line.trim();
            if (value.isBlank()) {
                continue;
            }
            String upper = value.toUpperCase();
            if (upper.contains("CÔNG TY")
                    || upper.contains("CONG TY")
                    || upper.startsWith("CTY ")
                    || upper.contains(" COMPANY")
                    || upper.contains(" CORPORATION")) {
                return value;
            }
        }
        return "";
    }
    private boolean isActive(Company company) {
        return !Boolean.FALSE.equals(
                company.getActive()
        );
    }
    private CompanyInfo toCompanyInfo(
            Company company,
            double score,
            String matchedBy
    ) {
        log.info(
                "[CompanyResolver] Tìm thấy companyId={}, "
                        + "name={}, matchedBy={}, score={}",
                company.getId(),
                company.getLegalName(),
                matchedBy,
                score
        );
        return CompanyInfo.builder()
                .companyId(company.getId())
                .companyCode(company.getCompanyCode())
                .taxCode(company.getTaxCode())
                .companyName(company.getLegalName())
                .normalizedName(company.getNormalizedName())
                .matchScore(score)
                .matchedBy(matchedBy)
                .found(true)
                .build();
    }
    private CompanyInfo notFound(String reason) {
        return CompanyInfo.builder()
                .matchScore(0.0)
                .matchedBy(reason)
                .found(false)
                .build();
    }
}

