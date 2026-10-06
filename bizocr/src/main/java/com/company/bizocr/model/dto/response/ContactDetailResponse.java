package com.company.bizocr.model.dto.response;

import com.company.bizocr.model.entity.Company;
import com.company.bizocr.model.entity.Contact;
import com.company.bizocr.model.entity.DocumentImage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactDetailResponse {
    private Contact contact;
    private Company company;
    private List<DocumentImage> images;
    private Contact possibleDuplicateContact;
}
