package com.company.bizocr.controller;

import com.company.bizocr.model.dto.request.ContactUpdateRequest;
import com.company.bizocr.model.dto.response.ContactDetailResponse;
import com.company.bizocr.model.dto.response.ContactSearchResult;
import com.company.bizocr.model.entity.Contact;
import com.company.bizocr.security.CurrentUserResolver;
import com.company.bizocr.service.NamecardSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/v1/contacts")
@RequiredArgsConstructor
public class ContactController {

    private final NamecardSearchService searchService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping
    public Mono<ContactSearchResult> searchContacts(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String industry,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {

        return currentUserResolver.getCurrentUser()
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chưa xác thực")))
                .flatMap(user -> searchService.searchContacts(q, industry, user.companyId(), page, size));
    }

    @GetMapping("/{id}")
    public Mono<ContactDetailResponse> getContactDetail(@PathVariable Long id) {
        return currentUserResolver.getCurrentUser()
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chưa xác thực")))
                .flatMap(user -> searchService.getContactDetail(id, user.companyId()));
    }

    @PutMapping("/{id}")
    public Mono<Contact> updateContact(
            @PathVariable Long id,
            @RequestBody ContactUpdateRequest request) {
        return currentUserResolver.getCurrentUser()
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chưa xác thực")))
                .flatMap(user -> searchService.updateContact(id, request, user.companyId()));
    }

    @PostMapping("/{id}/contacted")
    public Mono<Contact> markContacted(@PathVariable Long id) {
        return currentUserResolver.getCurrentUser()
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chưa xác thực")))
                .flatMap(user -> searchService.markContacted(id, user.companyId()));
    }

    @PostMapping("/{id}/confirm-merge")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> confirmMerge(@PathVariable Long id) {
        return currentUserResolver.getCurrentUser()
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chưa xác thực")))
                .flatMap(user -> searchService.confirmMerge(id, user.companyId()));
    }

    @DeleteMapping("/{id}/dismiss-duplicate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> dismissDuplicate(@PathVariable Long id) {
        return currentUserResolver.getCurrentUser()
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chưa xác thực")))
                .flatMap(user -> searchService.dismissDuplicate(id, user.companyId()));
    }
}
