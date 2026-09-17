package com.company.bizocr.security;

import java.util.List;

public record CurrentUser(String username, List<String> roles, Long companyId) {
    public String rolesAsString() {
        return String.join(",", roles); // fit vào reviewed_by_role VARCHAR(50)
    }
}