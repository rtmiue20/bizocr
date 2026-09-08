package com.company.bizocr.model.dto.response;

import com.company.bizocr.model.dto.CompanyInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentApproveResponse {
    private Long documentId;
    private String status;
    private CompanyInfo company;
    private TaxLookupResponse taxLookup;
    private List<String> warnings;
}
