package com.company.bizocr.model.dto.schema;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ContractFields(
        @NotBlank(message = "Thiếu thông tin Bên A")
        String party_a,

        @NotBlank(message = "Thiếu thông tin Bên B")
        String party_b,

        @Positive(message = "Giá trị hợp đồng phải lớn hơn 0")
        @JsonAlias("contract_value")
        Double total_amount,

        String effective_date,
        String summary
) {}