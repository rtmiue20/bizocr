package com.company.bizocr.model.entity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
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
}