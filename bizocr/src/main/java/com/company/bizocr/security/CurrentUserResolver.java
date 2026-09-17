package com.company.bizocr.security;

import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class CurrentUserResolver {

    public Mono<CurrentUser> getCurrentUser() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> (JwtAuthenticationToken) ctx.getAuthentication())
                .map(token -> {
                    Jwt jwt = token.getToken();
                    List<String> roles = token.getAuthorities().stream()
                            .map(a -> a.getAuthority().replace("ROLE_", ""))
                            .collect(Collectors.toList());

                    // Lấy claim và ép kiểu an toàn
                    Object rawCompanyId = jwt.getClaim("companyId");
                    Long companyId = parseClaimAsLong(rawCompanyId);

                    return new CurrentUser(jwt.getSubject(), roles, companyId);
                });
    }

    /**
     * Ép kiểu linh hoạt claim companyId về Long, tránh lỗi ClassCastException.
     */
    private Long parseClaimAsLong(Object claim) {
        if (claim == null) {
            return null;
        }
        if (claim instanceof Long l) {
            return l;
        }
        if (claim instanceof Integer i) {
            return i.longValue();
        }
        if (claim instanceof Double d) {
            return d.longValue();
        }
        if (claim instanceof Number n) {
            return n.longValue();
        }
        if (claim instanceof String s && !s.isBlank()) {
            try {
                return Long.valueOf(s.trim());
            } catch (NumberFormatException e) {
                // Ignore exception and return null
            }
        }
        return null;
    }
}