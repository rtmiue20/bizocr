package com.company.bizocr.service;

import com.company.bizocr.company.CompanyFinderService;
import com.company.bizocr.model.dto.request.RegisterRequest;
import com.company.bizocr.model.entity.Company;
import com.company.bizocr.model.entity.CompanyVerificationSource;
import com.company.bizocr.model.entity.CompanyVerificationStatus;
import com.company.bizocr.model.entity.User;
import com.company.bizocr.repository.CompanyRepository;
import com.company.bizocr.repository.UserRepository;
import com.company.bizocr.company.CompanyNameNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;
    private final CompanyNameNormalizer companyNameNormalizer;
    private final CompanyFinderService companyFinderService;

    public Mono<User> register(RegisterRequest req) {
        if (req.getUsername() == null || req.getUsername().isBlank()) {
            return Mono.error(new IllegalArgumentException("Tên đăng nhập không được để trống"));
        }
        if (req.getPassword() == null || req.getPassword().isBlank()) {
            return Mono.error(new IllegalArgumentException("Mật khẩu không được để trống"));
        }
        if (req.getEmail() == null || req.getEmail().isBlank()) {
            return Mono.error(new IllegalArgumentException("Email không được để trống"));
        }

        String username = req.getUsername().trim();
        String email = req.getEmail().trim();

        return userRepository.existsByUsername(username)
                .flatMap(exists -> exists ? Mono.error(new IllegalArgumentException("Username đã tồn tại")) : Mono.just(false))
                .then(userRepository.existsByEmail(email))
                .flatMap(exists -> exists ? Mono.error(new IllegalArgumentException("Email đã tồn tại")) : Mono.just(false))
                .then(resolveCompany(req.getTaxCode(), req.getCompanyName(), username))
                .flatMap(company -> {
                    User newUser = User.builder()
                            .username(username)
                            .passwordHash(passwordEncoder.encode(req.getPassword()))
                            .email(email)
                            .phoneNumber(req.getPhoneNumber() != null ? req.getPhoneNumber().trim() : null)
                            .role("REVIEWER") // Hardcode role vì lý do bảo mật
                            .companyId(company.getId())
                            .trustedLabeler(false)
                            .active(true)
                            .createdAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                            .build();
                    return userRepository.save(newUser);
                });
    }

    private Mono<Company> resolveCompany(String taxCode, String companyName, String username) {
        String rawTax = (taxCode != null && !taxCode.isBlank()) ? companyNameNormalizer.normalizeTaxCode(taxCode) : null;
        String normalizedTaxCode = (rawTax != null && !rawTax.isBlank()) ? rawTax : null;

        String rawName = (companyName != null && !companyName.isBlank()) ? companyName.trim() : null;
        String fallbackLegalName = rawName != null ? rawName : ("Cá nhân (" + username + ")");
        String normalizedName = companyNameNormalizer.normalize(fallbackLegalName);
        if (normalizedName == null || normalizedName.isBlank()) {
            normalizedName = ("CANHAN " + username.toUpperCase());
        }

        final String finalNormalizedName = normalizedName;
        final String finalLegalName = fallbackLegalName;
        final String finalTaxCode = normalizedTaxCode;

        Mono<Company> createNewCompanyMono = Mono.defer(() -> {
            Company newCompany = Company.builder()
                    .taxCode(finalTaxCode)
                    .legalName(finalLegalName)
                    .normalizedName(finalNormalizedName)
                    .verificationStatus(CompanyVerificationStatus.UNVERIFIED)
                    .verificationSource(CompanyVerificationSource.MANUAL_REGISTRATION)
                    .active(true)
                    .createdAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                    .build();
            // saveOrFetch: phòng race condition khi nhiều user cùng đăng ký cùng công ty
            return companyFinderService.saveOrFetch(newCompany);
        });

        if (finalTaxCode != null) {
            // Có MST: tra theo MST trước (khớp duy nhất), fallback tên
            return companyRepository.findByTaxCode(finalTaxCode)
                    .switchIfEmpty(companyFinderService.findBestByName(finalNormalizedName))
                    .switchIfEmpty(createNewCompanyMono);
        } else {
            // Không có MST: dùng CompanyFinderService chịu được nhiều dòng trùng
            return companyFinderService.findBestByName(finalNormalizedName)
                    .switchIfEmpty(createNewCompanyMono);
        }
    }
}