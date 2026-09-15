package com.company.bizocr.model.dto.schema;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VatInvoiceFields(
        // Ràng buộc định dạng MST chuẩn (10 chữ số hoặc 13 chữ số có/không có dấu gạch ngang)
        @Pattern(regexp = "^[0-9]{10}(-?[0-9]{3})?$", message = "Sai định dạng mã số thuế")
        String tax_code,

        String seller_name,

        @Pattern(regexp = "^\\d{2}/\\d{2}/\\d{4}$", message = "Ngày hóa đơn phải đúng định dạng dd/MM/yyyy")
        String invoice_date,

        @PositiveOrZero(message = "Tổng tiền không được âm")
        Double total_amount,

        @PositiveOrZero(message = "Tiền thuế không được âm")
        Double vat_amount
) {}