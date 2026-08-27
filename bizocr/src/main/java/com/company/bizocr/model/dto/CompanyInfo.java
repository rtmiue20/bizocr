package com.company.bizocr.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyInfo {

    private Long companyId;
    private String companyCode;
    private String taxCode;
    private String companyName;
    private String normalizedName;
    private Double matchScore;
    private String matchedBy;
    private boolean found;
}