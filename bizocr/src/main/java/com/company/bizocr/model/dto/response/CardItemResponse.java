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
public class CardItemResponse {
    private Long id;
    private Long documentId;
    private Long companyId;
    private String fullName;
    private String title;
    private String department;
    private String companyName;
    private String taxCode;
    private String phone;
    private String mobile;
    private String email;
    private String companyPhone;
    private String companyEmail;
    private String fax;
    private String website;
    private String address;
    private String industry;
    private String imageUrl;
    private String thumbnailUrl;
    private Boolean isNew;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
}
