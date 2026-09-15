package com.company.bizocr.model.dto.schema;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NameCardField(
        @JsonAlias({"contact_name", "full_name"})
        String name,

        String phone,

        String email,

        @JsonAlias("company_name")
        String company,

        @JsonAlias({"contact_title", "title", "position"})
        String job_title,

        String address
) {}
