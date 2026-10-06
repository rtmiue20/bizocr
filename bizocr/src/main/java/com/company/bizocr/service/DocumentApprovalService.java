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
import com.company.bizocr.model.entity.CompanyVerificationSource;
import com.company.bizocr.model.entity.CompanyVerificationStatus;
import com.company.bizocr.model.entity.Document;
import com.company.bizocr.model.entity.DocumentStatus;
import com.company.bizocr.repository.*;
import com.company.bizocr.validator.ExtractionValidatorService;
import com.company.bizocr.security.CurrentUser;
import com.company.bizocr.model.entity.Contact;
import com.company.bizocr.service.ContactDeduplicationService;
import com.company.bizocr.statemachine.DocumentStateMachine;
import com.company.bizocr.statemachine.DocumentStateMachine.TransitionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentApprovalService {

    private final DocumentRepository documentRepository;
    private final CompanyRepository companyRepository;
    private final CompanyAliasRepository aliasRepository;
    private final TaxApiClient taxApiClient;
    private final ObjectMapper objectMapper;
    private final CompanyNameNormalizer nameNormalizer;
    private final ExternalTaxSearchService externalTaxSearchService;
    private final IndustryRepository industryRepository;
    private final FieldAuditService fieldAuditService;
    private final DocumentStateMachine stateMachine;
    private final ExtractionValidatorService extractionValidatorService;
    private final TrainingBatchService trainingBatchService;
    private final ContactDeduplicationService contactDeduplicationService;

    @Transactional
    public Mono<DocumentApproveResponse> approveDocument(Long documentId, DocumentApproveRequest request, CurrentUser reviewer) {
        return documentRepository.findById(documentId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Không tìm thấy Document ID: " + documentId)))
                .flatMap(doc -> {
                    // ── Xác định trạng thái hiện tại của doc, cho phép cả VALIDATED → APPROVED ──
                    DocumentStatus currentStatus = DocumentStatus.fromString(doc.getStatus());
                    if (currentStatus == null) {
                        return Mono.error(new IllegalStateException(
                                "[Approve] documentId=" + documentId + " có status không hợp lệ: " + doc.getStatus()));
                    }
                    if (!DocumentStatus.isAllowed(currentStatus, DocumentStatus.APPROVED)) {
                        log.error("[Approve] documentId={} không thể duyệt từ trạng thái={}", documentId, currentStatus);
                        return Mono.error(new IllegalStateException(
                                "Không thể duyệt document ở trạng thái: " + currentStatus));
                    }

                    // ── Capture snapshot TRƯỚC khi ghi đè — dùng cho audit diff ──────────────
                    final String originalFieldsSnapshot = (doc.getExtractedFields() != null
                            && !doc.getExtractedFields().isBlank())
                            ? doc.getExtractedFields()
                            : "{}";

                    return taxApiClient.lookup(request.getTaxCode(), request.getCompanyName())
                            .defaultIfEmpty(new TaxLookupResponse())
                            .flatMap(taxLookup -> upsertCompany(request, taxLookup)
                                    .flatMap(company -> {

                                        // 1. Audit Log Mono — truyền originalJson tường minh
                                        Mono<Boolean> auditMono;
                                        try {
                                            String updatedFieldsJson = objectMapper.writeValueAsString(request.getUpdatedFields());
                                            auditMono = fieldAuditService
                                                    .logFieldCorrections(doc, originalFieldsSnapshot, updatedFieldsJson)
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

                                        // Gán metadata cho Entity Document
                                        try {
                                            doc.setDocumentType(request.getApprovedType());
                                            doc.setCompanyId(company.getId());
                                            doc.setContactName(request.getContactName());
                                            doc.setContactTitle(request.getContactTitle());
                                            doc.setCompanyMatchScore(taxLookup.getTaxCode() != null ? 1.0 : 0.8);
                                            doc.setCompanyMatchedBy(taxLookup.getTaxCode() != null ? "TAX_API" : "MANUAL");
                                            doc.setExtractedFields(objectMapper.writeValueAsString(request.getUpdatedFields()));
                                            doc.setHumanVerified(Boolean.TRUE.equals(request.getHumanVerified()));
                                            // Lấy từ JWT (CurrentUserResolver) — không lấy từ request body
                                            doc.setReviewedBy(reviewer.username());
                                            doc.setReviewedAt(LocalDateTime.now().format(
                                                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                                        } catch (Exception e) {
                                            return Mono.error(e);
                                        }

                                        // 2. Google Search Suggested Industries Mono
                                        Mono<List<CompanyIndustryDetail>> suggestedIndustriesMono = (company.getLegalName() != null && !company.getLegalName().isBlank())
                                                ? externalTaxSearchService
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
                                                })
                                                : Mono.just(List.of()); // Trả về list rỗng nếu không có tên công ty

                                        // Zip song song: audit + industries, rồi mới transition FSM
                                        return Mono.zip(auditMono, suggestedIndustriesMono)
                                                .flatMap(tuple -> {
                                                    List<CompanyIndustryDetail> suggestedIndustries = tuple.getT2();

                                                    // ── FSM: currentStatus → APPROVED ────────────────
                                                    return stateMachine.transition(documentId, currentStatus, DocumentStatus.APPROVED)
                                                            .flatMap(fsmResult -> {
                                                                if (fsmResult == TransitionResult.MISMATCH) {
                                                                    log.error("[Approve] documentId={} FSM MISMATCH khi APPROVE — trạng thái đã thay đổi bởi luồng khác", documentId);
                                                                    return Mono.error(new IllegalStateException(
                                                                            "documentId=" + documentId + " bị thay đổi trạng thái bởi luồng song song"));
                                                                }
                                                                if (fsmResult == TransitionResult.IDEMPOTENT) {
                                                                    log.warn("[Approve] documentId={} đã APPROVED rồi (idempotent retry)", documentId);
                                                                }

                                                                // ── Tính training_eligible ──────────────────────────────
                                                                // Điều kiện: trustedLabeler + humanVerified + qua validator + !IDEMPOTENT
                                                                boolean passedValidator = extractionValidatorService.isValid(
                                                                        request.getApprovedType(),
                                                                        doc.getExtractedFields(),
                                                                        doc.getRawOcrText()
                                                                );
                                                                boolean eligible = reviewer.trustedLabeler()
                                                                        && Boolean.TRUE.equals(doc.getHumanVerified())
                                                                        && passedValidator
                                                                        && fsmResult != TransitionResult.IDEMPOTENT;
                                                                doc.setTrainingEligible(eligible);
                                                                if (eligible) {
                                                                    log.info("[Approve] documentId={} đủ điều kiện huấn luyện (trustedLabeler=true, humanVerified=true, passedValidator=true)", documentId);
                                                                }

                                                                // Cập nhật status trên entity và lưu xuống DB
                                                                doc.setStatus(DocumentStatus.APPROVED.name());
                                                                return documentRepository.save(doc)
                                                                        .flatMap(savedDoc -> {
                                                                            // Xếp vào lô huấn luyện nếu đủ điều kiện
                                                                            Mono<Void> enrollMono = Boolean.TRUE.equals(savedDoc.getTrainingEligible())
                                                                                    ? trainingBatchService.enrollDocument(savedDoc).then()
                                                                                    : Mono.empty();

                                                                            return enrollMono.thenReturn(savedDoc);
                                                                        })
                                                                        .flatMap(savedDoc -> {
                                                                            if ("NAMECARD".equalsIgnoreCase(savedDoc.getDocumentType())
                                                                                    || "NAMECARD".equalsIgnoreCase(request.getApprovedType())) {
                                                                                return upsertContactIfNamecard(savedDoc, request, company, reviewer)
                                                                                        .onErrorResume(e -> {
                                                                                            log.error("[Approve] Lỗi tạo Contact từ Namecard (không làm hỏng phê duyệt): {}", e.getMessage(), e);
                                                                                            return Mono.empty();
                                                                                        })
                                                                                        .thenReturn(savedDoc);
                                                                            }
                                                                            return Mono.just(savedDoc);
                                                                        })
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
                                                });
                                    })
                                    .doOnError(e -> log.error(
                                            "[Approve] Lỗi khi duyệt documentId={}, taxCode={}, companyName={}: {}",
                                            documentId, request.getTaxCode(), request.getCompanyName(),
                                            e.getMessage(), e))
                            );
                });

    }


    private Mono<Company> upsertCompany(DocumentApproveRequest req, TaxLookupResponse tax) {
        String finalTaxCode = (tax.getTaxCode() != null) ? tax.getTaxCode() : req.getTaxCode();
        String finalName = (tax.getLegalName() != null) ? tax.getLegalName() : req.getCompanyName();

        // Thêm kiểm tra null trước khi normalize
        String normalizedTaxCode = (finalTaxCode != null && !finalTaxCode.isBlank())
                ? nameNormalizer.normalizeTaxCode(finalTaxCode)
                : "";

        if (!normalizedTaxCode.isBlank()) {
            return upsertByTaxCode(normalizedTaxCode, finalName, req, tax);
        }

        return upsertByName(finalName, req);
    }

    private Mono<Company> upsertByTaxCode(String normalizedTaxCode, String finalName, DocumentApproveRequest req, TaxLookupResponse tax) {
        return companyRepository.findByTaxCode(normalizedTaxCode)
                .flatMap(existingCompany -> {
                    // Nếu Tax API trả về MST hợp lệ hoặc người duyệt xác thực, nâng cấp trạng thái thành VERIFIED
                    if (tax.getTaxCode() != null || req.getTaxCode() != null) {
                        existingCompany.setLegalName(finalName);
                        existingCompany.setNormalizedName(finalName != null ? nameNormalizer.normalize(finalName) : "");
                        if (tax.getAddress() != null && !tax.getAddress().isBlank()) {
                            existingCompany.setAddress(tax.getAddress());
                        }
                        existingCompany.setVerificationStatus(CompanyVerificationStatus.VERIFIED);
                        existingCompany.setVerificationSource(CompanyVerificationSource.VIETQR_DIRECT);
                        existingCompany.setVerifiedAt(LocalDateTime.now());
                        existingCompany.setUpdatedAt(LocalDateTime.now().toString());
                        return companyRepository.save(existingCompany);
                    }
                    return Mono.just(existingCompany);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    Company newCompany = Company.builder()
                            .taxCode(normalizedTaxCode)
                            .legalName(finalName)
                            .normalizedName(finalName != null ? nameNormalizer.normalize(finalName) : "")
                            .shortName(req.getCompanyName())
                            .address(tax.getAddress())
                            .active(true)
                            .verificationStatus(tax.getTaxCode() != null ? CompanyVerificationStatus.VERIFIED : CompanyVerificationStatus.UNVERIFIED)
                            .verificationSource(tax.getTaxCode() != null ? CompanyVerificationSource.VIETQR_DIRECT : CompanyVerificationSource.OCR_ONLY)
                            .verifiedAt(tax.getTaxCode() != null ? LocalDateTime.now() : null)
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

    private Mono<Contact> upsertContactIfNamecard(
            Document savedDoc,
            DocumentApproveRequest req,
            Company company,
            CurrentUser reviewer) {

        Map<String, Object> fields = req.getUpdatedFields() != null ? req.getUpdatedFields() : Collections.emptyMap();

        String fullName = req.getContactName();
        if (fullName == null || fullName.isBlank()) {
            fullName = getStringField(fields, "contact_name", "full_name", "name");
        }
        if (fullName == null || fullName.isBlank()) {
            log.info("[Approve] Không có tên liên hệ để tạo Contact cho documentId={}", savedDoc.getId());
            return Mono.empty();
        }

        String title = req.getContactTitle();
        if (title == null || title.isBlank()) {
            title = getStringField(fields, "contact_title", "title", "job_title", "position");
        }

        String phone = getStringField(fields, "phone", "personal_phone");
        String mobile = getStringField(fields, "mobile", "cellphone");
        String companyPhone = getStringField(fields, "company_phone", "company_tel", "office_phone", "tel");
        String email = getStringField(fields, "email", "personal_email", "mail");
        String companyEmail = getStringField(fields, "company_email", "office_email", "corp_email");
        String fax = getStringField(fields, "fax", "fax_number", "so_fax");
        String website = getStringField(fields, "website", "web");
        String department = getStringField(fields, "department");
        String address = getStringField(fields, "address");

        Contact newContact = Contact.builder()
                .ownerCompanyId(reviewer.companyId())
                .createdBy(reviewer.userId() != null ? reviewer.userId() : 0L)
                .documentId(savedDoc.getId())
                .companyId(company != null ? company.getId() : null)
                .fullName(fullName.trim())
                .title(title)
                .department(department)
                .phone(phone)
                .mobile(mobile)
                .email(email)
                .website(website)
                .fax(fax)
                .companyPhone(companyPhone)
                .companyEmail(companyEmail)
                .notes(address != null && !address.isBlank() ? "Địa chỉ: " + address : null)
                .build();

        return contactDeduplicationService.deduplicateAndSave(newContact);
    }

    private String getStringField(Map<String, Object> map, String... keys) {
        for (String k : keys) {
            Object v = map.get(k);
            if (v != null && !v.toString().isBlank()) {
                return v.toString().trim();
            }
        }
        return null;
    }
}