package com.company.bizocr.service;

import com.company.bizocr.company.CompanyNameNormalizer;
import com.company.bizocr.model.entity.Contact;
import com.company.bizocr.repository.ContactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContactDeduplicationService {

    private final ContactRepository contactRepository;
    private final CompanyNameNormalizer nameNormalizer;
    private final JaroWinklerSimilarity similarity = new JaroWinklerSimilarity();

    /**
     * Chuẩn hóa email: lowercase, trim.
     */
    public String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase();
    }

    /**
     * Chuẩn hóa số điện thoại về dạng 0xxxxxxxxx (bỏ +84, 84, khoảng trắng, dấu gạch ngang, chấm).
     */
    public String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String clean = phone.replaceAll("[^0-9+]", "");
        if (clean.startsWith("+84")) {
            clean = "0" + clean.substring(3);
        } else if (clean.startsWith("84") && clean.length() >= 10) {
            clean = "0" + clean.substring(2);
        }
        return clean.trim();
    }

    /**
     * Xử lý trùng lặp và lưu Contact:
     * 1. Check trùng Email trong cùng owner_company_id -> Auto Merge
     * 2. Check trùng Phone -> chỉ Auto Merge nếu Tên gần giống (similarity >= 0.70)
     * 3. Check trùng Tên + Cùng Company -> Đặt cờ POSSIBLE_DUPLICATE (KHÔNG gộp tự động)
     * 4. Không trùng -> Lưu mới với status OK
     */
    public Mono<Contact> deduplicateAndSave(Contact incoming) {
        String cleanEmail = normalizeEmail(incoming.getEmail());
        String cleanPhone = normalizePhone(incoming.getPhone());
        String cleanMobile = normalizePhone(incoming.getMobile());
        String normName = nameNormalizer.normalize(incoming.getFullName());

        incoming.setEmail(cleanEmail);
        incoming.setPhone(cleanPhone);
        incoming.setMobile(cleanMobile);
        incoming.setNormalizedName(normName);
        if (incoming.getDedupStatus() == null) {
            incoming.setDedupStatus("OK");
        }

        Long ownerId = incoming.getOwnerCompanyId();

        // Bước 1: Check Email
        if (cleanEmail != null && !cleanEmail.isBlank()) {
            return contactRepository.findByEmailAndOwnerCompanyId(cleanEmail, ownerId)
                    .flatMap(existing -> {
                        log.info("[Dedup] Tìm thấy trùng Email: '{}' với Contact id={}. Tiến hành auto-merge.", cleanEmail, existing.getId());
                        mergeIntoExisting(existing, incoming);
                        return contactRepository.save(existing);
                    })
                    .switchIfEmpty(Mono.defer(() -> checkPhoneDedup(incoming, normName, ownerId)));
        }

        return checkPhoneDedup(incoming, normName, ownerId);
    }

    private Mono<Contact> checkPhoneDedup(Contact incoming, String normName, Long ownerId) {
        String targetPhone = incoming.getPhone() != null ? incoming.getPhone() : incoming.getMobile();
        if (targetPhone != null && !targetPhone.isBlank()) {
            return contactRepository.findByPhoneAndOwnerCompanyId(targetPhone, ownerId)
                    .flatMap(existing -> {
                        // Tính độ tương đồng tên
                        String existingNorm = existing.getNormalizedName() != null ? existing.getNormalizedName() : nameNormalizer.normalize(existing.getFullName());
                        double sim = calculateSimilarity(normName, existingNorm);
                        if (sim >= 0.70) {
                            log.info("[Dedup] Trùng SĐT '{}' và tên tương tự (sim={}): auto-merge vào Contact id={}", targetPhone, sim, existing.getId());
                            mergeIntoExisting(existing, incoming);
                            return contactRepository.save(existing);
                        } else {
                            log.info("[Dedup] Trùng SĐT '{}' nhưng tên khác biệt ('{}' vs '{}', sim={}). Không auto-merge vì nghi số tổng đài.",
                                    targetPhone, normName, existingNorm, sim);
                            return checkNameAndCompanyDedup(incoming, normName, ownerId);
                        }
                    })
                    .switchIfEmpty(Mono.defer(() -> checkNameAndCompanyDedup(incoming, normName, ownerId)));
        }

        return checkNameAndCompanyDedup(incoming, normName, ownerId);
    }

    private Mono<Contact> checkNameAndCompanyDedup(Contact incoming, String normName, Long ownerId) {
        if (normName.isBlank() || incoming.getCompanyId() == null) {
            return saveNewContact(incoming);
        }

        // Tìm kiếm các Contact trong cùng company
        return contactRepository.findByOwnerCompanyIdPaged(ownerId, 100, 0)
                .filter(c -> incoming.getCompanyId().equals(c.getCompanyId()))
                .filter(c -> {
                    String existingNorm = c.getNormalizedName() != null ? c.getNormalizedName() : nameNormalizer.normalize(c.getFullName());
                    double sim = calculateSimilarity(normName, existingNorm);
                    return sim >= 0.85;
                })
                .next()
                .flatMap(existing -> {
                    log.warn("[Dedup] Nghi ngờ trùng Tên + Company ('{}' vs Contact id={}). Gắn cờ POSSIBLE_DUPLICATE.", incoming.getFullName(), existing.getId());
                    incoming.setDedupStatus("POSSIBLE_DUPLICATE");
                    incoming.setPossibleDuplicateId(existing.getId());
                    return saveNewContact(incoming);
                })
                .switchIfEmpty(Mono.defer(() -> saveNewContact(incoming)));
    }

    private Mono<Contact> saveNewContact(Contact contact) {
        LocalDateTime now = LocalDateTime.now();
        contact.setCreatedAt(now);
        contact.setUpdatedAt(now);
        return contactRepository.save(contact);
    }

    /**
     * Merge thông tin còn thiếu của existing contact từ incoming contact.
     */
    private void mergeIntoExisting(Contact target, Contact source) {
        if ((target.getTitle() == null || target.getTitle().isBlank()) && source.getTitle() != null) {
            target.setTitle(source.getTitle());
        }
        if ((target.getDepartment() == null || target.getDepartment().isBlank()) && source.getDepartment() != null) {
            target.setDepartment(source.getDepartment());
        }
        if ((target.getCompanyId() == null) && source.getCompanyId() != null) {
            target.setCompanyId(source.getCompanyId());
        }
        if ((target.getPhone() == null || target.getPhone().isBlank()) && source.getPhone() != null) {
            target.setPhone(source.getPhone());
        }
        if ((target.getMobile() == null || target.getMobile().isBlank()) && source.getMobile() != null) {
            target.setMobile(source.getMobile());
        }
        if ((target.getEmail() == null || target.getEmail().isBlank()) && source.getEmail() != null) {
            target.setEmail(source.getEmail());
        }
        if ((target.getWebsite() == null || target.getWebsite().isBlank()) && source.getWebsite() != null) {
            target.setWebsite(source.getWebsite());
        }
        if (source.getDocumentId() != null) {
            target.setDocumentId(source.getDocumentId());
        }
        if (source.getMetAt() != null && !source.getMetAt().isBlank()) {
            target.setMetAt(source.getMetAt());
        }
        if (source.getMetDate() != null) {
            target.setMetDate(source.getMetDate());
        }
        if (source.getNotes() != null && !source.getNotes().isBlank()) {
            String combinedNotes = target.getNotes() == null ? source.getNotes() : target.getNotes() + "\n" + source.getNotes();
            target.setNotes(combinedNotes);
        }
        target.setUpdatedAt(LocalDateTime.now());
    }

    private double calculateSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return 0.0;
        }
        if (s1.equalsIgnoreCase(s2)) {
            return 1.0;
        }
        return similarity.apply(s1.toLowerCase(), s2.toLowerCase());
    }
}
