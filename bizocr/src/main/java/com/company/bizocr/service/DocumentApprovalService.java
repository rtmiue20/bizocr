package com.company.bizocr.service;

import com.company.bizocr.client.PythonMlClient;
import com.company.bizocr.client.TaxApiClient;
import com.company.bizocr.company.CompanyNameNormalizer;
import com.company.bizocr.model.dto.CompanyIndustryDetail;
import com.company.bizocr.model.dto.CompanyInfo;
import com.company.bizocr.model.dto.request.DocumentApproveRequest;
import com.company.bizocr.model.dto.response.DocumentApproveResponse;
import com.company.bizocr.model.dto.response.TaxLookupResponse;
import com.company.bizocr.model.entity.Company;
import com.company.bizocr.model.entity.CompanyAlias;
import com.company.bizocr.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentApprovalService {

    private final DocumentRepository documentRepository;
    private final CompanyRepository companyRepository;
    private final CompanyAliasRepository aliasRepository;
    private final TaxApiClient taxApiClient;
    private final ObjectMapper objectMapper;
    private final PythonMlClient pythonMlClient;
    private final CompanyNameNormalizer nameNormalizer;
    private final ExternalTaxSearchService externalTaxSearchService;
    private final IndustryRepository industryRepository;
    private final FieldAuditService fieldAuditService;

    @Transactional
    public Mono<DocumentApproveResponse> approveDocument(Long documentId, DocumentApproveRequest request) {
        return documentRepository.findById(documentId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Không tìm thấy Document ID: " + documentId)))
                .flatMap(doc ->
                        taxApiClient.lookup(request.getTaxCode(), request.getCompanyName())
                                .defaultIfEmpty(new TaxLookupResponse())
                                .flatMap(taxLookup -> upsertCompany(request, taxLookup)
                                        .flatMap(company -> {

                                            // 1. Audit Log Mono (Đã sửa lỗi tham số: chỉ gọi 2 tham số)
                                            Mono<Boolean> auditMono;
                                            try {
                                                String updatedFieldsJson = objectMapper.writeValueAsString(request.getUpdatedFields());
                                                auditMono = fieldAuditService.logFieldCorrections(doc, updatedFieldsJson)
                                                        .then(Mono.just(true))
                                                        .defaultIfEmpty(true)
                                                        .onErrorResume(e -> {
                                                            log.error("[Approve] Lỗi khi ghi Audit Log chi tiết: {}", e.getMessage());
                                                            return Mono.just(true);
                                                        });
                                            } catch (Exception e) {
                                                log.error("[Approve] Lỗi parse JSON cho Audit Log: {}", e.getMessage());
                                                auditMono = Mono.just(true);
                                            }

                                            // Gán dữ liệu cho Entity Document
                                            try {
                                                doc.setStatus("APPROVED");
                                                doc.setDocumentType(request.getApprovedType());
                                                doc.setCompanyId(company.getId());

                                                doc.setContactName(request.getContactName());
                                                doc.setContactTitle(request.getContactTitle());

                                                doc.setCompanyMatchScore(taxLookup.getTaxCode() != null ? 1.0 : 0.8);
                                                doc.setCompanyMatchedBy(taxLookup.getTaxCode() != null ? "TAX_API" : "MANUAL");
                                                doc.setExtractedFields(objectMapper.writeValueAsString(request.getUpdatedFields()));
                                                doc.setReviewedBy(request.getReviewedBy());
                                                doc.setReviewedAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                                            } catch (Exception e) {
                                                return Mono.error(e);
                                            }

                                            // 2. Python ML Data Collection Mono
                                            Mono<Boolean> collectMlDataMono = pythonMlClient
                                                    .collectApprovedData(doc.getRawOcrText(), request.getApprovedType())
                                                    .then(Mono.just(true))
                                                    .onErrorResume(e -> Mono.just(false));

                                            // 3. Google Search Suggested Industries Mono
                                            Mono<List<CompanyIndustryDetail>> suggestedIndustriesMono = externalTaxSearchService
                                                    .searchCompanyInfoOnline(company.getLegalName(), null)
                                                    .flatMapMany(result -> industryRepository.findByCodeInAndIsActiveTrue(result.industryCodeCandidates()))
                                                    .map(industry -> CompanyIndustryDetail.builder()
                                                            .code(industry.getCode())
                                                            .name(industry.getName())
                                                            .isMain(false)
                                                            .build())
                                                    .collectList()
                                                    .onErrorResume(e -> {
                                                        log.warn("[Approve] Bỏ qua gợi ý ngành nghề do lỗi Search API: {}", e.getMessage());
                                                        return Mono.just(List.of());
                                                    });

                                            // Zip toàn bộ Mono chạy song song và chờ kết quả
                                            return Mono.zip(auditMono, collectMlDataMono, suggestedIndustriesMono)
                                                    .flatMap(tuple -> {
                                                        List<CompanyIndustryDetail> suggestedIndustries = tuple.getT3();

                                                        // Lưu DB và trả về response
                                                        return documentRepository.save(doc)
                                                                .map(savedDoc -> DocumentApproveResponse.builder()
                                                                        .documentId(savedDoc.getId())
                                                                        .status(savedDoc.getStatus())
                                                                        .taxLookup(taxLookup)
                                                                        .company(CompanyInfo.builder()
                                                                                .companyId(company.getId())
                                                                                .companyCode(company.getCompanyCode())
                                                                                .legalName(company.getLegalName())
                                                                                .companyName(company.getLegalName())
                                                                                .normalizedName(company.getNormalizedName())
                                                                                .taxCode(company.getTaxCode())
                                                                                .matchScore(taxLookup.getTaxCode() != null ? 1.0 : 0.8)
                                                                                .matchedBy(taxLookup.getTaxCode() != null ? "TAX_API" : "MANUAL")
                                                                                .found(true)
                                                                                .suggestedIndustries(suggestedIndustries)
                                                                                .build())
                                                                        .build());
                                                    });
                                        })
                                        .doOnError(e -> log.error(
                                                "[Approve] Lỗi khi duyệt documentId={}, taxCode={}, companyName={}: {}",
                                                documentId, request.getTaxCode(), request.getCompanyName(),
                                                e.getMessage(), e))
                                )
                );
    }

    private Mono<Company> upsertCompany(DocumentApproveRequest req, TaxLookupResponse tax) {
        String finalTaxCode = (tax.getTaxCode() != null) ? tax.getTaxCode() : req.getTaxCode();
        String finalName = (tax.getLegalName() != null) ? tax.getLegalName() : req.getCompanyName();

        String normalizedTaxCode = nameNormalizer.normalizeTaxCode(finalTaxCode);

        if (!normalizedTaxCode.isBlank()) {
            return upsertByTaxCode(normalizedTaxCode, finalName, req, tax);
        }

        return upsertByName(finalName, req);
    }

    private Mono<Company> upsertByTaxCode(String normalizedTaxCode, String finalName, DocumentApproveRequest req, TaxLookupResponse tax) {
        return companyRepository.findByTaxCode(normalizedTaxCode)
                .switchIfEmpty(Mono.defer(() -> {
                    Company newCompany = Company.builder()
                            .taxCode(normalizedTaxCode)
                            .legalName(finalName)
                            .normalizedName(nameNormalizer.normalize(finalName))
                            .shortName(req.getCompanyName())
                            .address(tax.getAddress())
                            .active(true)
                            .createdAt(LocalDateTime.now().toString())
                            .build();
                    return companyRepository.save(newCompany)
                            .doOnError(e -> log.error(
                                    "[Approve] Lỗi tạo Company mới taxCode={}, name={}: {}",
                                    normalizedTaxCode, finalName, e.getMessage(), e));
                }))
                .flatMap(company -> linkAliasIfDifferent(company, req));
    }

    private Mono<Company> upsertByName(String finalName, DocumentApproveRequest req) {
        if (finalName == null || finalName.isBlank()) {
            log.warn("[Approve] Không có taxCode lẫn companyName hợp lệ, không thể xác định Company.");
            return Mono.just(Company.builder().id(null).build());
        }

        String normalizedName = nameNormalizer.normalize(finalName);

        return companyRepository.findByNormalizedName(normalizedName)
                .switchIfEmpty(
                        aliasRepository.findByNormalizedAlias(normalizedName)
                                .flatMap(alias -> companyRepository.findById(alias.getCompanyId()))
                )
                .switchIfEmpty(Mono.defer(() -> {
                    log.info("[Approve] Không có MST, tạo Company mới chỉ theo tên: {}", finalName);
                    Company newCompany = Company.builder()
                            .legalName(finalName)
                            .normalizedName(normalizedName)
                            .shortName(req.getCompanyName())
                            .active(true)
                            .createdAt(LocalDateTime.now().toString())
                            .build();
                    return companyRepository.save(newCompany)
                            .doOnError(e -> log.error(
                                    "[Approve] Lỗi tạo Company mới (không có MST) name={}: {}",
                                    finalName, e.getMessage(), e));
                }))
                .flatMap(company -> linkAliasIfDifferent(company, req));
    }

    private Mono<Company> linkAliasIfDifferent(Company company, DocumentApproveRequest req) {
        String requestedName = req.getCompanyName();
        if (requestedName != null && !requestedName.isBlank()
                && company.getLegalName() != null
                && !requestedName.equalsIgnoreCase(company.getLegalName())) {
            CompanyAlias alias = new CompanyAlias();
            alias.setCompanyId(company.getId());
            alias.setAliasName(requestedName);
            alias.setNormalizedAlias(nameNormalizer.normalize(requestedName));
            alias.setSource("NAMECARD");
            alias.setConfidenceScore(1.0);
            alias.setCreatedAt(LocalDateTime.now().toString());
            return aliasRepository.save(alias)
                    .doOnError(e -> log.error("[Approve] Lỗi lưu CompanyAlias: {}", e.getMessage(), e))
                    .thenReturn(company);
        }
        return Mono.just(company);
    }
}