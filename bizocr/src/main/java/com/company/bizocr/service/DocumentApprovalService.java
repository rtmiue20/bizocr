package com.company.bizocr.service;

import com.company.bizocr.client.TaxApiClient;
import com.company.bizocr.model.dto.CompanyInfo;
import com.company.bizocr.model.dto.request.DocumentApproveRequest;
import com.company.bizocr.model.dto.response.DocumentApproveResponse;
import com.company.bizocr.model.entity.Company;
import com.company.bizocr.model.entity.CompanyAlias;
import com.company.bizocr.model.entity.FieldCorrectionAudit;
import com.company.bizocr.repository.CompanyAliasRepository;
import com.company.bizocr.repository.CompanyRepository;
import com.company.bizocr.repository.DocumentRepository;
import com.company.bizocr.repository.FieldCorrectionAuditRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentApprovalService {

    private final DocumentRepository documentRepository;
    private final CompanyRepository companyRepository;
    private final CompanyAliasRepository aliasRepository;
    private final FieldCorrectionAuditRepository auditRepository;
    private final TaxApiClient taxApiClient;
    private final ObjectMapper objectMapper;

    @Transactional
    public Mono<DocumentApproveResponse> approveDocument(Long documentId, DocumentApproveRequest request) {
        return documentRepository.findById(documentId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Không tìm thấy Document ID: " + documentId)))
                .flatMap(doc -> {
                    // 1. Log lịch sử sửa đổi (Audit)
                    Mono<FieldCorrectionAudit> auditMono = saveAuditLog(doc.getId(), doc.getExtractedFields(), request);

                    // 2. Tra cứu API Thuế đồng bộ bằng WebClient
                    return taxApiClient.lookup(request.getTaxCode(), request.getCompanyName())
                            .defaultIfEmpty(new com.company.bizocr.model.dto.response.TaxLookupResponse()) // Fallback nếu không thấy
                            .flatMap(taxLookup -> upsertCompany(request, taxLookup)
                                    .flatMap(company -> {
                                        // 3. Cập nhật trạng thái Document
                                        try {
                                            doc.setStatus("APPROVED");
                                            doc.setDocumentType(request.getApprovedType());
                                            doc.setCompanyId(company.getId());
                                            doc.setCompanyMatchScore(taxLookup.getTaxCode() != null ? 1.0 : 0.8);
                                            doc.setCompanyMatchedBy(taxLookup.getTaxCode() != null ? "TAX_API" : "MANUAL");
                                            doc.setExtractedFields(objectMapper.writeValueAsString(request.getUpdatedFields()));
                                            doc.setReviewedBy(request.getReviewedBy());
                                            doc.setReviewedAt(LocalDateTime.now().toString());
                                        } catch (Exception e) {
                                            return Mono.error(e);
                                        }

                                        return auditMono.then(documentRepository.save(doc))
                                                .map(savedDoc -> DocumentApproveResponse.builder()
                                                        .documentId(savedDoc.getId())
                                                        .status(savedDoc.getStatus())
                                                        .taxLookup(taxLookup)
                                                        .company(CompanyInfo.builder()
                                                                .companyId(company.getId())
                                                                .legalName(company.getLegalName())
                                                                .taxCode(company.getTaxCode())
                                                                .build())
                                                        .build());
                                    }));
                });
    }

    private Mono<FieldCorrectionAudit> saveAuditLog(Long docId, String originalFields, DocumentApproveRequest req) {
        if (req.getUpdatedFields() == null) return Mono.empty();
        try {
            FieldCorrectionAudit audit = FieldCorrectionAudit.builder()
                    .documentId(docId)
                    .fieldName("extractedFields")
                    .originalValue(originalFields)
                    .correctedValue(objectMapper.writeValueAsString(req.getUpdatedFields()))
                    .reviewedBy(req.getReviewedBy())
                    .reviewedAt(LocalDateTime.now().toString())
                    .build();
            return auditRepository.save(audit);
        } catch (Exception e) {
            return Mono.empty();
        }
    }

    private Mono<Company> upsertCompany(DocumentApproveRequest req, com.company.bizocr.model.dto.response.TaxLookupResponse tax) {
        String finalTaxCode = (tax.getTaxCode() != null) ? tax.getTaxCode() : req.getTaxCode();
        String finalName = (tax.getLegalName() != null) ? tax.getLegalName() : req.getCompanyName();

        if (finalTaxCode == null || finalTaxCode.isBlank()) {
            return Mono.just(Company.builder().id(null).legalName(finalName).build());
        }

        return companyRepository.findByTaxCode(finalTaxCode)
                .switchIfEmpty(Mono.defer(() -> {
                    Company newCompany = Company.builder()
                            .taxCode(finalTaxCode)
                            .legalName(finalName)
                            .shortName(req.getCompanyName())
                            .address(tax.getAddress())
                            .active(true)
                            .createdAt(LocalDateTime.now().toString())
                            .build();
                    return companyRepository.save(newCompany);
                }))
                .flatMap(company -> {
                    // Lưu Namecard alias nếu tên trên namecard khác tên công ty chính thức
                    if (req.getCompanyName() != null && !req.getCompanyName().equalsIgnoreCase(finalName)) {
                        CompanyAlias alias = new CompanyAlias();
                        alias.setCompanyId(company.getId());
                        alias.setAliasName(req.getCompanyName());
                        alias.setSource("NAMECARD");
                        alias.setCreatedAt(LocalDateTime.now().toString());
                        return aliasRepository.save(alias).thenReturn(company);
                    }
                    return Mono.just(company);
                });
    }
}