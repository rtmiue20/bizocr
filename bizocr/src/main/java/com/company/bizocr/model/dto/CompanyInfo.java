package com.company.bizocr.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyInfo {

    private Long companyId;
    private String legalName;
    private String companyCode;
    private String taxCode;
    private String companyName;
    private String normalizedName;
    private Double matchScore;
    private String matchedBy;
    private boolean found;
    private String verificationStatus;

    /**
     * Ngành nghề nghi nhận được (mức A - best effort), dạng "code - name".
     * Rỗng nếu Search API không tìm ra hoặc chưa cấu hình Search.
     * KHÔNG đảm bảo đầy đủ/chính xác 100% - chỉ mang tính gợi ý cho người duyệt xác nhận lại.
     */
    private List<CompanyIndustryDetail> suggestedIndustries;
}