package com.company.bizocr.model.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactUpdateRequest {
    private String fullName;
    private String title;
    private String department;
    private String phone;
    private String mobile;
    private String email;
    private String website;
    private String metAt;
    private LocalDate metDate;
    private String notes;
    private String tags;
    private Long companyId;
}
