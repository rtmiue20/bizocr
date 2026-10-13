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
public class CardPageResponse {
    private List<CardItemResponse> items;
    private long total;
    private int page;
    private int pageSize;
    private int totalPages;
}
