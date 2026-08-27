package com.company.bizocr.model.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Setter
@Table("company_aliases")
public class CompanyAlias {

    @Id
    private Long id;

    @Column("company_id")
    private Long companyId;

    @Column("alias_name")
    private String aliasName;

    @Column("normalized_alias")
    private String normalizedAlias;

    private String source;

    @Column("confidence_score")
    private Double confidenceScore;

    @Column("created_at")
    private String createdAt;
}