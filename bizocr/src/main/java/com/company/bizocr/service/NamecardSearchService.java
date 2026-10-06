package com.company.bizocr.service;

import com.company.bizocr.company.CompanyNameNormalizer;
import com.company.bizocr.model.dto.request.ContactUpdateRequest;
import com.company.bizocr.model.dto.response.ContactDetailResponse;
import com.company.bizocr.model.dto.response.ContactSearchResult;
import com.company.bizocr.model.dto.response.ContactSummaryDto;
import com.company.bizocr.model.entity.Company;
import com.company.bizocr.model.entity.Contact;
import com.company.bizocr.model.entity.DocumentImage;
import com.company.bizocr.repository.CompanyRepository;
import com.company.bizocr.repository.ContactRepository;
import com.company.bizocr.repository.DocumentImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NamecardSearchService {

    private final ContactRepository contactRepository;
    private final CompanyRepository companyRepository;
    private final DocumentImageRepository documentImageRepository;
    private final CompanyNameNormalizer nameNormalizer;

    public Mono<ContactSearchResult> searchContacts(String query, String industryCode, Long ownerCompanyId, int page, int size) {
        long offset = (long) page * size;

        Flux<Contact> contactsFlux;
        Mono<Long> totalCountMono;

        boolean hasQuery = query != null && !query.trim().isEmpty();
        boolean hasIndustry = industryCode != null && !industryCode.trim().isEmpty();

        if (hasQuery) {
            String normQuery = nameNormalizer.normalize(query.trim());
            // Tạo biểu thức boolean search: "ABC XYZ" -> "+ABC* +XYZ*"
            StringBuilder boolQuery = new StringBuilder();
            for (String word : normQuery.split("\\s+")) {
                if (!word.isBlank()) {
                    boolQuery.append("+").append(word).append("* ");
                }
            }
            String searchExpr = boolQuery.toString().trim();
            if (searchExpr.isBlank()) {
                searchExpr = normQuery + "*";
            }

            final String finalExpr = searchExpr;
            contactsFlux = contactRepository.fullTextSearchByName(finalExpr, ownerCompanyId, size, offset);
            totalCountMono = contactRepository.countFullTextSearchByName(finalExpr, ownerCompanyId);
        } else if (hasIndustry) {
            contactsFlux = contactRepository.findByOwnerCompanyIdAndIndustryCode(ownerCompanyId, industryCode.trim(), size, offset);
            totalCountMono = contactRepository.countByOwnerCompanyIdAndIndustryCode(ownerCompanyId, industryCode.trim());
        } else {
            contactsFlux = contactRepository.findByOwnerCompanyIdPaged(ownerCompanyId, size, offset);
            totalCountMono = contactRepository.countByOwnerCompanyId(ownerCompanyId);
        }

        return contactsFlux
                .flatMap(contact -> enrichToSummary(contact))
                .collectList()
                .zipWith(totalCountMono.defaultIfEmpty(0L))
                .map(tuple -> {
                    List<ContactSummaryDto> items = tuple.getT1();
                    long total = tuple.getT2();
                    int totalPages = size > 0 ? (int) Math.ceil((double) total / size) : 0;

                    return ContactSearchResult.builder()
                            .items(items)
                            .totalElements(total)
                            .page(page)
                            .size(size)
                            .totalPages(totalPages)
                            .build();
                });
    }

    private Mono<ContactSummaryDto> enrichToSummary(Contact c) {
        ContactSummaryDto dto = ContactSummaryDto.builder()
                .id(c.getId())
                .ownerCompanyId(c.getOwnerCompanyId())
                .documentId(c.getDocumentId())
                .companyId(c.getCompanyId())
                .fullName(c.getFullName())
                .title(c.getTitle())
                .department(c.getDepartment())
                .phone(c.getPhone())
                .mobile(c.getMobile())
                .email(c.getEmail())
                .website(c.getWebsite())
                .tags(c.getTags())
                .metAt(c.getMetAt())
                .lastContacted(c.getLastContacted())
                .dedupStatus(c.getDedupStatus())
                .possibleDuplicateId(c.getPossibleDuplicateId())
                .createdAt(c.getCreatedAt())
                .build();

        Mono<String> companyNameMono = c.getCompanyId() != null
                ? companyRepository.findById(c.getCompanyId()).map(Company::getLegalName).defaultIfEmpty("")
                : Mono.just("");

        Mono<DocumentImage> frontImageMono = c.getDocumentId() != null
                ? documentImageRepository.findByDocumentIdAndSide(c.getDocumentId(), "FRONT").defaultIfEmpty(new DocumentImage())
                : Mono.just(new DocumentImage());

        return Mono.zip(companyNameMono, frontImageMono)
                .map(tuple -> {
                    dto.setCompanyName(tuple.getT1());
                    DocumentImage img = tuple.getT2();
                    if (img.getId() != null) {
                        dto.setFrontThumbKey(img.getThumbKey());
                        dto.setFrontStorageKey(img.getStorageKey());
                    }
                    return dto;
                });
    }

    public Mono<ContactDetailResponse> getContactDetail(Long contactId, Long ownerCompanyId) {
        return contactRepository.findById(contactId)
                .filter(c -> ownerCompanyId.equals(c.getOwnerCompanyId()))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Không tìm thấy Contact hoặc không có quyền truy cập")))
                .flatMap(contact -> {
                    Mono<Company> compMono = contact.getCompanyId() != null
                            ? companyRepository.findById(contact.getCompanyId()).defaultIfEmpty(new Company())
                            : Mono.just(new Company());

                    Mono<List<DocumentImage>> imagesMono = contact.getDocumentId() != null
                            ? documentImageRepository.findByDocumentId(contact.getDocumentId()).collectList()
                            : Mono.just(Collections.emptyList());

                    Mono<Contact> dupMono = (contact.getPossibleDuplicateId() != null)
                            ? contactRepository.findById(contact.getPossibleDuplicateId()).defaultIfEmpty(new Contact())
                            : Mono.just(new Contact());

                    return Mono.zip(compMono, imagesMono, dupMono)
                            .map(tuple -> ContactDetailResponse.builder()
                                    .contact(contact)
                                    .company(tuple.getT1().getId() != null ? tuple.getT1() : null)
                                    .images(tuple.getT2())
                                    .possibleDuplicateContact(tuple.getT3().getId() != null ? tuple.getT3() : null)
                                    .build());
                });
    }

    public Mono<Contact> updateContact(Long contactId, ContactUpdateRequest req, Long ownerCompanyId) {
        return contactRepository.findById(contactId)
                .filter(c -> ownerCompanyId.equals(c.getOwnerCompanyId()))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Không tìm thấy Contact hoặc không có quyền truy cập")))
                .flatMap(c -> {
                    if (req.getFullName() != null && !req.getFullName().isBlank()) {
                        c.setFullName(req.getFullName().trim());
                        c.setNormalizedName(nameNormalizer.normalize(req.getFullName().trim()));
                    }
                    if (req.getTitle() != null) c.setTitle(req.getTitle().trim());
                    if (req.getDepartment() != null) c.setDepartment(req.getDepartment().trim());
                    if (req.getPhone() != null) c.setPhone(req.getPhone().trim());
                    if (req.getMobile() != null) c.setMobile(req.getMobile().trim());
                    if (req.getEmail() != null) c.setEmail(req.getEmail().trim().toLowerCase());
                    if (req.getWebsite() != null) c.setWebsite(req.getWebsite().trim());
                    if (req.getMetAt() != null) c.setMetAt(req.getMetAt().trim());
                    if (req.getMetDate() != null) c.setMetDate(req.getMetDate());
                    if (req.getNotes() != null) c.setNotes(req.getNotes().trim());
                    if (req.getTags() != null) c.setTags(req.getTags().trim());
                    if (req.getCompanyId() != null) c.setCompanyId(req.getCompanyId());

                    c.setUpdatedAt(LocalDateTime.now());
                    return contactRepository.save(c);
                });
    }

    public Mono<Contact> markContacted(Long contactId, Long ownerCompanyId) {
        return contactRepository.findById(contactId)
                .filter(c -> ownerCompanyId.equals(c.getOwnerCompanyId()))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Không tìm thấy Contact hoặc không có quyền truy cập")))
                .flatMap(c -> {
                    c.setLastContacted(LocalDateTime.now());
                    c.setUpdatedAt(LocalDateTime.now());
                    return contactRepository.save(c);
                });
    }

    @Transactional
    public Mono<Void> confirmMerge(Long contactId, Long ownerCompanyId) {
        return contactRepository.findById(contactId)
                .filter(c -> ownerCompanyId.equals(c.getOwnerCompanyId()))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Không tìm thấy Contact")))
                .flatMap(duplicateContact -> {
                    Long targetId = duplicateContact.getPossibleDuplicateId();
                    if (targetId == null) {
                        return Mono.error(new IllegalStateException("Contact này không có cờ nghi trùng"));
                    }
                    return contactRepository.findById(targetId)
                            .flatMap(target -> {
                                if (duplicateContact.getDocumentId() != null && target.getDocumentId() == null) {
                                    target.setDocumentId(duplicateContact.getDocumentId());
                                }
                                if (duplicateContact.getNotes() != null && !duplicateContact.getNotes().isBlank()) {
                                    target.setNotes((target.getNotes() != null ? target.getNotes() + "\n" : "") + duplicateContact.getNotes());
                                }
                                target.setUpdatedAt(LocalDateTime.now());
                                return contactRepository.save(target)
                                        .then(contactRepository.delete(duplicateContact));
                            });
                });
    }

    public Mono<Void> dismissDuplicate(Long contactId, Long ownerCompanyId) {
        return contactRepository.findById(contactId)
                .filter(c -> ownerCompanyId.equals(c.getOwnerCompanyId()))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Không tìm thấy Contact")))
                .flatMap(c -> {
                    c.setDedupStatus("OK");
                    c.setPossibleDuplicateId(null);
                    c.setUpdatedAt(LocalDateTime.now());
                    return contactRepository.save(c).then();
                });
    }
}
