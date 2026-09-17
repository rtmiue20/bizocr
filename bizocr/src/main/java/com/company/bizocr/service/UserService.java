package com.company.bizocr.service;

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

    public Mono<User> register(RegisterRequest req) {
        return userRepository.existsByUsername(req.getUsername())
                .flatMap(exists -> exists ? Mono.error(new IllegalArgumentException("Username đã tồn tại")) : Mono.just(false))
                .then(userRepository.existsByEmail(req.getEmail()))
                .flatMap(exists -> exists ? Mono.error(new IllegalArgumentException("Email đã tồn tại")) : Mono.just(false))
                .then(resolveCompany(req.getTaxCode(), req.getCompanyName()))
                .flatMap(company -> {
                    User newUser = User.builder()
                            .username(req.getUsername())
                            .passwordHash(passwordEncoder.encode(req.getPassword()))
                            .email(req.getEmail())
                            .phoneNumber(req.getPhoneNumber())
                            .role("REVIEWER") // Hardcode role vì lý do bảo mật
                            .companyId(company.getId())
                            .active(true)
                            .createdAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                            .build();
                    return userRepository.save(newUser);
                });
    }

    private Mono<Company> resolveCompany(String taxCode, String companyName) {
        String normalizedTaxCode = companyNameNormalizer.normalizeTaxCode(taxCode);
        String normalizedName = companyNameNormalizer.normalize(companyName);

        Mono<Company> createNewCompanyMono = Mono.defer(() -> {
            Company newCompany = Company.builder()
                    .taxCode(normalizedTaxCode)
                    .legalName(companyName)
                    .normalizedName(normalizedName)
                    .verificationStatus(CompanyVerificationStatus.UNVERIFIED)
                    .verificationSource(CompanyVerificationSource.MANUAL_REGISTRATION)
                    .active(true)
                    .createdAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                    .build();
            return companyRepository.save(newCompany);
        });

        if (normalizedTaxCode != null && !normalizedTaxCode.isEmpty()) {
            return companyRepository.findByTaxCode(normalizedTaxCode)
                    .switchIfEmpty(companyRepository.findByNormalizedName(normalizedName))
                    .switchIfEmpty(createNewCompanyMono);
        } else {
            return companyRepository.findByNormalizedName(normalizedName)
                    .switchIfEmpty(createNewCompanyMono);
        }
    }
}
