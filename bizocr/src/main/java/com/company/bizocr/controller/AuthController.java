package com.company.bizocr.controller;

import com.company.bizocr.model.dto.request.RegisterRequest; // Hãy đảm bảo import đúng package của bạn
import com.company.bizocr.repository.UserRepository;
import com.company.bizocr.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final UserService userService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // Khởi tạo qua constructor thay vì @Autowired để chuẩn format của Spring
    public AuthController(JwtEncoder jwtEncoder, UserService userService,
                          UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.jwtEncoder = jwtEncoder;
        this.userService = userService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/register")
    public Mono<ResponseEntity<Map<String, String>>> register(@RequestBody RegisterRequest request) {
        return userService.register(request)
                .map(user -> ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "Đăng ký thành công!")))
                .onErrorResume(IllegalArgumentException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()))));
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<Map<String, String>>> login(@RequestBody LoginRequest request) {
        return userRepository.findByUsername(request.username())
                // Kiểm tra mật khẩu mã hóa trong DB so với mật khẩu raw gửi lên
                .filter(user -> passwordEncoder.matches(request.password(), user.getPasswordHash()))
                .switchIfEmpty(Mono.error(new BadCredentialsException("Sai tài khoản hoặc mật khẩu!")))
                .map(user -> {
                    Instant now = Instant.now();

                    JwtClaimsSet claims = JwtClaimsSet.builder()
                            .subject(user.getUsername()) // Lấy ID hoặc Username thực tế
                            .issuedAt(now)
                            .expiresAt(now.plusSeconds(36000)) // Token sống 10 tiếng
                            .claim("roles", List.of(user.getRole())) // Trích xuất role từ user trong DB
                            .claim("companyId", user.getCompanyId()) // Gắn ID Company thực tế
                            .claim("trusted_labeler", user.isTrustedLabeler()) // Quyền tạo training data
                            .build();

                    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
                    String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

                    return ResponseEntity.ok(Map.of("access_token", token));
                })
                // Xử lý lỗi đăng nhập sai để trả về HTTP 401 thay vì 500
                .onErrorResume(BadCredentialsException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()))));
    }

    // Record DTO nhận body request
    public record LoginRequest(String username, String password) {}
}