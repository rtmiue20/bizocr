package com.company.bizocr.model.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentApproveRequest {
    private Long expectedVersion;
    private String approvedType;        // VD: NAMECARD, ELECTRICITY_BILL
    private String companyName;         // Tên công ty do con người xác nhận
    private String taxCode;             // Mã số thuế (nếu có)
    private Map<String, Object> updatedFields; // JSON fields đã được human correction
    private String contactName;
    private String contactTitle;
}