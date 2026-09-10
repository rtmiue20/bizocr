package com.company.bizocr.model.entity;

import lombok.*;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("company_industries")
public class CompanyIndustry {
    private Long companyId;
    private String industryCode;
    private Boolean isMain;
}