package com.company.bizocr.model.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("industries")
public class Industry {
    @Id // Lưu ý: Với String ID trong R2DBC, cần cấu hình cẩn thận Persistable nếu cần insert mới từ code
    private String code;
    private String name;
    private String description;
    private Boolean isActive;
}