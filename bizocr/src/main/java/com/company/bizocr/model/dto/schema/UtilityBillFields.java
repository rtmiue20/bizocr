package com.company.bizocr.model.dto.schema;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.PositiveOrZero;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UtilityBillFields(
        @JsonAlias("customer_code")
        String customer_id,
        String customer_name,
        String bill_period,
        @PositiveOrZero(message = "Tổng tiền không được âm")
        Double total_amount
) {}
