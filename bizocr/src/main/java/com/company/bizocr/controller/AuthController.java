package com.company.bizocr.controller;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final JwtEncoder jwtEncoder;

    public AuthController(JwtEncoder jwtEncoder) {
        this.jwtEncoder = jwtEncoder;
    }

    @PostMapping("/login")
    public Mono<Map<String, String>> login(@RequestBody LoginRequest request) {
        // Hardcode để test: user = admin, password = admin
        if ("admin".equals(request.username()) && "admin".equals(request.password())) {
            Instant now = Instant.now();

            JwtClaimsSet claims = JwtClaimsSet.builder()
                    .subject("admin_user_id")
                    .issuedAt(now)
                    .expiresAt(now.plusSeconds(36000)) // Token sống 10 tiếng
                    .claim("roles", List.of("ADMIN", "REVIEWER")) // Inject Role vào token
                    .claim("companyId", 1L) // Gắn sẵn CompanyID để Controller khác lấy
                    .build();

            JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
            String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

            return Mono.just(Map.of("access_token", token));
        }

        return Mono.error(new RuntimeException("Sai thông tin đăng nhập!"));
    }

    // Record DTO nhận body request
    public record LoginRequest(String username, String password) {}
}