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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
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

    /**
     * Cấu hình CORS cho môi trường dev/test.
     * QUAN TRỌNG: liệt kê rõ origin của console/frontend test (VD: IntelliJ dev server ở
     * localhost:63342, hoặc Vite/React dev server ở localhost:5173/3000...) thay vì dùng "*",
     * vì khi allowCredentials(true) (bắt buộc để gửi header Authorization), Spring sẽ ném lỗi
     * nếu allowedOrigins là "*". Trước khi lên production, thay danh sách này bằng domain thật.
     *
     * FIX: IntelliJ built-in web server KHÔNG cố định cổng 63342 — nếu cổng đó đang bị chiếm bởi
     * project/instance khác đang mở, IntelliJ tự tăng lên 63343, 63344... Liệt kê cứng từng cổng
     * (setAllowedOrigins) sẽ liên tục bị lệch mỗi khi IntelliJ đổi cổng. Chuyển sang
     * setAllowedOriginPatterns với wildcard "*" cho phần cổng — vẫn chỉ giới hạn ở localhost/127.0.0.1
     * (không mở cho origin ngoài máy dev), nhưng không còn phụ thuộc đúng 1 con số cổng cụ thể.
     * setAllowedOriginPatterns (khác setAllowedOrigins) hỗ trợ wildcard và vẫn tương thích
     * allowCredentials(true).
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(
                "http://localhost:*",   // IntelliJ built-in web server, Vite, React dev server... cổng bất kỳ
                "http://127.0.0.1:*"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                // Gắn CORS vào filter chain — thiếu dòng này thì bean corsConfigurationSource()
                // ở trên vẫn tồn tại nhưng KHÔNG được Spring Security áp dụng, request preflight
                // (OPTIONS) vẫn bị chặn trước khi tới controller.
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
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