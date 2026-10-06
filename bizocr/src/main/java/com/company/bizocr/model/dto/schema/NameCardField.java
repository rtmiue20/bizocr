package com.company.bizocr.model.dto.schema;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NameCardField(
        @JsonAlias({"contact_name", "full_name"})
        String name,

        @JsonAlias({"personal_phone", "mobile", "cell", "cellphone"})
        String phone,

        @JsonAlias({"company_phone", "company_tel", "office_phone"})
        String company_phone,

        @JsonAlias({"personal_email", "mail"})
        String email,

        @JsonAlias({"company_email", "office_email", "corp_email"})
        String company_email,

        @JsonAlias({"fax_number", "so_fax"})
        String fax,

        @JsonAlias("company_name")
        String company,

        @JsonAlias({"contact_title", "title", "position"})
        String job_title,

        String address,

        @JsonAlias({"tax_code", "mst"})
        String tax_code,

        String website
) {}

