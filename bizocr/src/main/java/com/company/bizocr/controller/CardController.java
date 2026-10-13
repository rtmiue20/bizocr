package com.company.bizocr.controller;

import com.company.bizocr.company.CompanyNameNormalizer;
import com.company.bizocr.model.dto.response.CardItemResponse;
import com.company.bizocr.model.dto.response.CardPageResponse;
import com.company.bizocr.model.dto.response.IndustryCountResponse;
import com.company.bizocr.model.entity.Company;
import com.company.bizocr.model.entity.Contact;
import com.company.bizocr.model.entity.Document;
import com.company.bizocr.repository.CardQueryRepository;
import com.company.bizocr.repository.CompanyRepository;
import com.company.bizocr.repository.ContactRepository;
import com.company.bizocr.repository.DocumentRepository;
import com.company.bizocr.security.CurrentUserResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/cards")
@RequiredArgsConstructor
public class CardController {

    private final ContactRepository contactRepository;
    private final CompanyRepository companyRepository;
    private final DocumentRepository documentRepository;
    private final CompanyNameNormalizer nameNormalizer;
    private final CurrentUserResolver currentUserResolver;
    private final CardQueryRepository cardQueryRepository;

    /**
     * Bước 4 & Bước 5: Endpoint danh sách và lọc
     * GET /api/v1/cards?page=0&pageSize=8&industry=&q=
     * Điều kiện: status = APPROVED, đúng tenant (owner_company_id), ORDER BY approved_at DESC, id DESC.
     */
    @GetMapping
    public Mono<CardPageResponse> getCards(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "8") int pageSize,
            @RequestParam(required = false) String industry,
            @RequestParam(required = false) String q) {

        long offset = (long) page * pageSize;

        return currentUserResolver.getCurrentUser()
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chưa xác thực")))
                .flatMap(user -> {
                    Long ownerId = user.companyId();

                    Flux<Contact> contactsFlux;
                    Mono<Long> totalCountMono;

                    boolean hasQuery = q != null && !q.trim().isEmpty();
                    String cleanIndustry = industry != null && !industry.trim().isEmpty() ? industry.trim() : null;

                    if (hasQuery) {
                        String normQuery = nameNormalizer.normalize(q.trim());
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

                        contactsFlux = contactRepository.searchApprovedCards(searchExpr, ownerId, cleanIndustry, pageSize, offset);
                        totalCountMono = contactRepository.countSearchApprovedCards(searchExpr, ownerId, cleanIndustry);
                    } else {
                        contactsFlux = contactRepository.findApprovedCards(ownerId, cleanIndustry, pageSize, offset);
                        totalCountMono = contactRepository.countApprovedCards(ownerId, cleanIndustry);
                    }

                    return contactsFlux
                            .flatMap(contact -> enrichToCardItem(contact, offset))
                            .collectList()
                            .zipWith(totalCountMono.defaultIfEmpty(0L))
                            .map(tuple -> {
                                List<CardItemResponse> items = tuple.getT1();
                                long total = tuple.getT2();
                                int totalPages = pageSize > 0 ? (int) Math.ceil((double) total / pageSize) : 0;

                                return CardPageResponse.builder()
                                        .items(items)
                                        .total(total)
                                        .page(page)
                                        .pageSize(pageSize)
                                        .totalPages(totalPages)
                                        .build();
                            });
                });
    }

    /**
     * Endpoint thống kê đếm số thẻ theo ngành nghề (cho các Chip lọc ngành nghề).
     */
    @GetMapping("/industries")
    public Mono<List<IndustryCountResponse>> getCardIndustries() {
        return currentUserResolver.getCurrentUser()
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chưa xác thực")))
                .flatMap(user -> cardQueryRepository.countCardsByIndustry(user.companyId())
                        .collectList()
                );
    }

    private Mono<CardItemResponse> enrichToCardItem(Contact c, long offset) {
        Mono<Company> compMono = c.getCompanyId() != null
                ? companyRepository.findById(c.getCompanyId()).defaultIfEmpty(new Company())
                : Mono.just(new Company());

        Mono<Document> docMono = c.getDocumentId() != null
                ? documentRepository.findById(c.getDocumentId()).defaultIfEmpty(new Document())
                : Mono.just(new Document());

        return Mono.zip(compMono, docMono)
                .map(tuple -> {
                    Company comp = tuple.getT1();
                    Document doc = tuple.getT2();

                    String companyName = comp.getLegalName() != null ? comp.getLegalName() : "";
                    String taxCode = comp.getTaxCode() != null ? comp.getTaxCode() : (doc.getTaxCode() != null ? doc.getTaxCode() : "");
                    String industry = c.getIndustry() != null && !c.getIndustry().isBlank()
                            ? c.getIndustry()
                            : (comp.getIndustry() != null ? comp.getIndustry() : "Khác");

                    String address = "";
                    if (c.getNotes() != null && c.getNotes().startsWith("Địa chỉ: ")) {
                        address = c.getNotes().substring("Địa chỉ: ".length()).trim();
                    } else if (comp.getAddress() != null) {
                        address = comp.getAddress();
                    }

                    String imageUrl = c.getDocumentId() != null
                            ? "/api/v1/documents/" + c.getDocumentId() + "/image"
                            : null;
                    String thumbnailUrl = c.getDocumentId() != null
                            ? "/api/v1/documents/" + c.getDocumentId() + "/thumbnail"
                            : null;

                    // isNew: duyệt trong vòng 24 giờ qua hoặc là thẻ đầu tiên
                    boolean isNew = false;
                    LocalDateTime approvedTime = c.getApprovedAt() != null ? c.getApprovedAt() : c.getCreatedAt();
                    if (approvedTime != null && approvedTime.isAfter(LocalDateTime.now().minusHours(24))) {
                        isNew = true;
                    }

                    return CardItemResponse.builder()
                            .id(c.getId())
                            .documentId(c.getDocumentId())
                            .companyId(c.getCompanyId())
                            .fullName(c.getFullName())
                            .title(c.getTitle())
                            .department(c.getDepartment())
                            .companyName(companyName)
                            .taxCode(taxCode)
                            .phone(c.getPhone())
                            .mobile(c.getMobile())
                            .email(c.getEmail())
                            .companyPhone(c.getCompanyPhone())
                            .companyEmail(c.getCompanyEmail())
                            .fax(c.getFax())
                            .website(c.getWebsite())
                            .address(address)
                            .industry(industry)
                            .imageUrl(imageUrl)
                            .thumbnailUrl(thumbnailUrl)
                            .isNew(isNew)
                            .approvedAt(c.getApprovedAt())
                            .createdAt(c.getCreatedAt())
                            .build();
                });
    }
}
