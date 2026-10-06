package com.company.bizocr.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactSearchResult {
    private List<ContactSummaryDto> items;
    private long totalElements;
    private int page;
    private int size;
    private int totalPages;
}
