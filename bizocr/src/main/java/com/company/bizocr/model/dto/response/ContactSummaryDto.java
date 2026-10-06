package com.company.bizocr.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactSummaryDto {
    private Long id;
    private Long ownerCompanyId;
    private Long documentId;
    private Long companyId;
    private String companyName;
    private String fullName;
    private String title;
    private String department;
    private String phone;
    private String mobile;
    private String email;
    private String website;
    private String tags;
    private String metAt;
    private LocalDateTime lastContacted;
    private String dedupStatus;
    private Long possibleDuplicateId;
    private String frontThumbKey;
    private String frontStorageKey;
    private LocalDateTime createdAt;
}
