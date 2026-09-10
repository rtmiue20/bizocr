package com.company.bizocr.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Transient;
import com.company.bizocr.model.dto.CompanyIndustryDetail;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("companies")
public class Company {
    @Id
    private Long id;
    /**
     * Mã nội bộ của công ty.
     */
    @Column("company_code")
    private String companyCode;
    /**
     * Mã số thuế.
     */
    @Column("tax_code")
    private String taxCode;
    /**
     * Tên pháp lý đầy đủ của công ty.
     */
    @Column("legal_name")
    private String legalName;
    /**
     * Tên viết tắt hoặc tên giao dịch.
     */
    @Column("short_name")
    private String shortName;
    /**
     * Tên đã chuẩn hóa để tìm kiếm.
     */
    @Column("normalized_name")
    private String normalizedName;
    private String address;
    private String phone;
    private String email;
    private String website;
    private String industry;
    private Boolean active;
    @Column("created_at")
    private String createdAt;
    @Column("updated_at")
    private String updatedAt;


    private String legalRepresentative;

    @Transient
    private List<CompanyIndustryDetail> industries;

    private CompanyVerificationStatus verificationStatus;
    private CompanyVerificationSource verificationSource;
    private Double matchConfidence;      // 0.0–1.0, null nếu không áp dụng (VD: khớp trực tiếp MST)
    private java.time.LocalDateTime verifiedAt;


}