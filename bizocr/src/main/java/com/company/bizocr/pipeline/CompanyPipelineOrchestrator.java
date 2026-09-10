package com.company.bizocr.pipeline;

import com.company.bizocr.client.TaxApiClient;
import com.company.bizocr.model.dto.OcrExtractedSignals;
import com.company.bizocr.model.entity.Company;
import com.company.bizocr.model.entity.CompanyAlias;
import com.company.bizocr.model.entity.CompanyVerificationSource;
import com.company.bizocr.model.entity.CompanyVerificationStatus;
import com.company.bizocr.repository.CompanyAliasRepository;
import com.company.bizocr.repository.CompanyLookupRepository;
import com.company.bizocr.service.ExternalTaxSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class CompanyPipelineOrchestrator {

    private final CompanyLookupRepository companyRepository;
    private final CompanyAliasRepository aliasRepository;
    private final ExternalTaxSearchService searchService;
    private final TaxApiClient taxApiClient; // Thay VietQrClient: đã xử lý 429/timeout/graceful fallback

    /**
     * Luồng định danh đa tín hiệu (Multi-Signal Identification):
     * 1. OCR có MST trực tiếp        -> VietQR                -> VERIFIED (VIETQR_DIRECT)
     * 2. Khớp DB nội bộ (similarity >80%) -> dùng lại Company có sẵn, giữ nguyên status của nó
     * 3. Search Google/Bing tìm MST  -> VietQR                -> VERIFIED (WEB_SEARCH_VIETQR)
     * 4. Không tra được ở đâu cả     -> lưu tín hiệu thô OCR  -> UNVERIFIED (OCR_ONLY)
     */
    public Mono<Company> execute(OcrExtractedSignals signals) {
        if (hasText(signals.getTaxCode())) {
            return fetchAndUpsertVerified(signals.getTaxCode(), signals.getCompanyName(), CompanyVerificationSource.VIETQR_DIRECT)
                    // Nếu MST OCR đọc sai/không tồn tại trên VietQR, đừng dừng -> rơi tiếp xuống các bước sau
                    .switchIfEmpty(Mono.defer(() -> resolveWithoutDirectTaxCode(signals)));
        }
        return resolveWithoutDirectTaxCode(signals);
    }

    private Mono<Company> resolveWithoutDirectTaxCode(OcrExtractedSignals signals) {
        return companyRepository.findBestMatch(signals.getDomain(), signals.getPhoneNumber(), normalizedOrNull(signals.getCompanyName()))
                .doOnNext(found -> log.info("[Pipeline] Khớp DB nội bộ: {} (status hiện tại: {})",
                        found.getLegalName(), found.getVerificationStatus()))
                .switchIfEmpty(
                        searchService.searchTaxCodeOnline(signals.getCompanyName(), signals.getDomain())
                                .flatMap(searchedTaxCode -> fetchAndUpsertVerified(
                                        searchedTaxCode, signals.getCompanyName(), CompanyVerificationSource.WEB_SEARCH_VIETQR))
                )
                .switchIfEmpty(Mono.defer(() -> saveUnverified(signals)));
    }

    /**
     * Tra VietQR theo MST, sau đó UPSERT (không insert bừa): nếu taxCode đã tồn tại trong DB
     * (kể cả bản ghi trước đó đang UNVERIFIED), cập nhật và nâng cấp lên VERIFIED thay vì tạo row mới.
     */
    private Mono<Company> fetchAndUpsertVerified(String taxCode, String rawAlias, CompanyVerificationSource source) {
        return taxApiClient.lookup(taxCode, rawAlias)
                .flatMap(taxData -> companyRepository.findByTaxCode(taxData.getTaxCode())
                        .defaultIfEmpty(Company.builder().active(true).build())
                        .flatMap(existing -> {
                            existing.setTaxCode(taxData.getTaxCode());
                            existing.setLegalName(taxData.getLegalName());
                            existing.setAddress(taxData.getAddress());
                            existing.setActive(true);
                            existing.setVerificationStatus(CompanyVerificationStatus.VERIFIED);
                            existing.setVerificationSource(source);
                            existing.setMatchConfidence(1.0);
                            existing.setVerifiedAt(LocalDateTime.now());
                            return companyRepository.save(existing);
                        })
                        .flatMap(saved -> selfLearnAlias(saved, rawAlias)));
    }

    /**
     * Bước cuối: không tra được MST ở bất kỳ đâu. Vẫn lưu lại (không được mất dữ liệu),
     * nhưng đánh dấu UNVERIFIED để tách bạch khỏi dữ liệu đã đối chiếu Thuế.
     * Bản ghi này sẽ tự động được nâng cấp lên VERIFIED sau này nếu có chứng từ khác
     * của cùng công ty chứa MST hợp lệ (xem fetchAndUpsertVerified).
     */
    private Mono<Company> saveUnverified(OcrExtractedSignals signals) {
        if (!hasText(signals.getCompanyName()) && !hasText(signals.getDomain())) {
            log.warn("[Pipeline] Không đủ tín hiệu (không tên, không domain, không MST) để định danh công ty, bỏ qua.");
            return Mono.empty();
        }

        log.warn("[Pipeline] Không xác thực được MST qua VietQR/DB/Search, lưu UNVERIFIED cho: {}", signals.getCompanyName());

        Company unverified = Company.builder()
                .taxCode(null)
                .legalName(signals.getCompanyName())
                .active(true)
                .verificationStatus(CompanyVerificationStatus.UNVERIFIED)
                .verificationSource(CompanyVerificationSource.OCR_ONLY)
                .matchConfidence(null)
                .verifiedAt(null)
                .build();

        return companyRepository.save(unverified);
    }

    private Mono<Company> selfLearnAlias(Company savedCompany, String rawAlias) {
        if (rawAlias == null || rawAlias.isBlank()) {
            return Mono.just(savedCompany);
        }
        String normalizedAlias = rawAlias.toUpperCase().trim();
        if (normalizedAlias.equalsIgnoreCase(savedCompany.getLegalName())) {
            return Mono.just(savedCompany); // Trùng tên pháp lý, không cần học alias vô nghĩa
        }

        CompanyAlias alias = CompanyAlias.builder()
                .companyId(savedCompany.getId())
                .normalizedAlias(normalizedAlias)
                .build();

        return aliasRepository.save(alias)
                .doOnSuccess(a -> log.info("[Self-Learning] Đã học Alias mới: '{}' cho Company ID: {}", rawAlias, savedCompany.getId()))
                .thenReturn(savedCompany)
                .onErrorResume(e -> {
                    // Alias trùng (unique constraint) không được làm hỏng luồng chính
                    log.warn("[Self-Learning] Bỏ qua Alias trùng cho Company ID {}: {}", savedCompany.getId(), e.getMessage());
                    return Mono.just(savedCompany);
                });
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private String normalizedOrNull(String name) {
        return hasText(name) ? name.toUpperCase().trim() : null;
    }
}