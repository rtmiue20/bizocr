package com.company.bizocr.model.dto.response;

import com.company.bizocr.model.entity.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentPageResponse {
    private List<Document> items;
    private long totalElements;
    private int page;
    private int size;
    private int totalPages;
}