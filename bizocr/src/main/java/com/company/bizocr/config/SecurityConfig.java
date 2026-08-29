package com.company.bizocr.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        // 1. Endpoint công khai & Đăng nhập
                        .pathMatchers("/actuator/health", "/api/v1/public/**", "/api/v1/auth/**").permitAll()

                        // 2. Phân quyền RBAC cho duyệt chứng từ
                        .pathMatchers(HttpMethod.POST, "/api/v1/documents/*/approve").hasAnyRole("ADMIN", "REVIEWER")

                        // (Khuyến nghị) Xuất báo cáo Excel cũng nên giới hạn quyền, không để mọi user đã login đều export được
                        .pathMatchers(HttpMethod.GET, "/api/v1/documents/export").hasAnyRole("ADMIN", "REVIEWER")

                        // 3. Các endpoint chứng từ khác
                        .pathMatchers("/api/v1/documents/**").authenticated()

                        // 4. Chặn các request còn lại
                        .anyExchange().authenticated()
                )
                // Chuyển sang xác thực Bearer Token (JWT / OAuth2 Resource Server)
                // QUAN TRỌNG: gắn jwtAuthenticationConverter() để map claim "roles" trong token
                // sang GrantedAuthority có tiền tố ROLE_, nếu không hasAnyRole()/hasRole() sẽ luôn trả 403
                // vì mặc định Spring chỉ đọc claim "scope"/"scp" và gắn tiền tố SCOPE_.
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwtSpec ->
                        jwtSpec.jwtAuthenticationConverter(jwtAuthenticationConverter())
                ))
                .build();
    }

    /**
     * Map claim "roles" trong JWT (VD: {"roles": ["ADMIN", "REVIEWER"]})
     * sang GrantedAuthority dạng ROLE_ADMIN / ROLE_REVIEWER để hasRole()/hasAnyRole() hoạt động đúng.
     *
     * LƯU Ý: Tên claim "roles" cần khớp với claim thực tế do Authorization Server (Keycloak/Auth0/...)
     * phát hành. Một số IdP dùng "realm_access.roles" (Keycloak) hoặc cấu trúc lồng khác — cần chỉnh
     * lại hàm extractRoles() bên dưới cho khớp với cấu trúc token thật.
     */
    private Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthenticationConverter() {
        return jwt -> {
            Collection<GrantedAuthority> authorities = extractRoles(jwt).stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                    .collect(Collectors.toList());

            AbstractAuthenticationToken token = new JwtAuthenticationToken(jwt, authorities);
            return Mono.just(token);
        };
    }

    @SuppressWarnings("unchecked")
    private List<String> extractRoles(Jwt jwt) {
        // Trường hợp phổ biến: JWT có claim phẳng "roles": ["ADMIN", "REVIEWER"]
        Object rolesClaim = jwt.getClaim("roles");
        if (rolesClaim instanceof List) {
            return (List<String>) rolesClaim;
        }

        // Trường hợp Keycloak: claim lồng "realm_access": { "roles": [...] }
        Object realmAccess = jwt.getClaim("realm_access");
        if (realmAccess instanceof Map) {
            Object nestedRoles = ((Map<String, Object>) realmAccess).get("roles");
            if (nestedRoles instanceof List) {
                return (List<String>) nestedRoles;
            }
        }

        return List.of();
    }
}