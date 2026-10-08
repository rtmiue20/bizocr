package com.company.bizocr.security;

import java.util.List;

public record CurrentUser(
        Long userId,
        String username,
        List<String> roles,
        Long companyId,
        boolean trustedLabeler
) {
    public CurrentUser(Long userId, String username, List<String> roles, Long companyId) {
        this(userId, username, roles, companyId, false);
    }

    public CurrentUser(String username, List<String> roles, Long companyId) {
        this(null, username, roles, companyId, false);
    }

    public CurrentUser(String username, List<String> roles, Long companyId, boolean trustedLabeler) {
        this(null, username, roles, companyId, trustedLabeler);
    }

    public String rolesAsString() {
        return String.join(",", roles);
    }
}