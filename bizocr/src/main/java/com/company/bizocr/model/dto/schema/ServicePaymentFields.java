package com.company.bizocr.model.dto.schema;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.PositiveOrZero;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ServicePaymentFields(
        String receipt_no,
        @JsonAlias("service_provider")
        String service_name,
        String payment_date,
        @PositiveOrZero(message = "Tổng tiền không được âm")
        @JsonAlias("amount")
        Double total_amount
) {}
