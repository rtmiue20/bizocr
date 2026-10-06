package com.company.bizocr.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table("users")
public class User {
    @Id
    private Long id;
    private String username;
    private String passwordHash;
    private String email;
    private String phoneNumber;
    private String role;
    private Long companyId;
    private Boolean active;
    private String createdAt;
    /**
     * Chỉ bật tay cho kế toán tin tưởng. Được ghi vào JWT claim "trusted_labeler"
     * để kiểm tra khi duyệt document có đủ điều kiện vào lô huấn luyện không.
     */
    private Boolean trustedLabeler;

    public boolean isTrustedLabeler() {
        return Boolean.TRUE.equals(trustedLabeler);
    }
}