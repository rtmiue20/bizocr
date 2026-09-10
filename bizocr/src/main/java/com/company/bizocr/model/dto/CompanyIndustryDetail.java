package com.company.bizocr.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyIndustryDetail {
    private String code;        // Mã ngành (VD: 6201)
    private String name;        // Tên ngành
    private Boolean isMain;     // Trạng thái ngành chính/phụ
}