package com.company.bizocr.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("training_batches")
public class TrainingBatch {

    @Id
    private Long id;

    @Builder.Default
    private String status = TrainingBatchStatus.OPEN.name();

    @Builder.Default
    private Integer size = 0;

    @Column("model_version_before")
    private String modelVersionBefore;

    @Column("model_version_after")
    private String modelVersionAfter;

    @Column("metrics_json")
    private String metricsJson;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}
