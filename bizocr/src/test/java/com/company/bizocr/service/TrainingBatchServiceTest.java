package com.company.bizocr.service;

import com.company.bizocr.client.PythonMlClient;
import com.company.bizocr.model.entity.Document;
import com.company.bizocr.model.entity.TrainingBatch;
import com.company.bizocr.model.entity.TrainingBatchStatus;
import com.company.bizocr.repository.DocumentRepository;
import com.company.bizocr.repository.TrainingBatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class TrainingBatchServiceTest {

    private TrainingBatchRepository batchRepository;
    private DocumentRepository documentRepository;
    private PythonMlClient pythonMlClient;
    private TrainingBatchService service;

    @BeforeEach
    void setUp() {
        batchRepository = mock(TrainingBatchRepository.class);
        documentRepository = mock(DocumentRepository.class);
        pythonMlClient = mock(PythonMlClient.class);
        service = new TrainingBatchService(batchRepository, documentRepository, pythonMlClient);
        ReflectionTestUtils.setField(service, "batchSize", 25);
    }

    @Test
    @DisplayName("Tài liệu không đủ điều kiện (training_eligible = false) -> không làm gì, trả về empty")
    void ineligibleDoc_shouldBeSkipped() {
        Document doc = Document.builder()
                .id(1L)
                .trainingEligible(false)
                .build();

        StepVerifier.create(service.enrollDocument(doc))
                .verifyComplete();

        verifyNoInteractions(batchRepository);
        verifyNoInteractions(documentRepository);
        verifyNoInteractions(pythonMlClient);
    }

    @Test
    @DisplayName("Tài liệu đã có training_batch_id -> bỏ qua không xếp lại")
    void alreadyAssignedDoc_shouldBeSkipped() {
        Document doc = Document.builder()
                .id(2L)
                .trainingEligible(true)
                .trainingBatchId(10L)
                .build();

        StepVerifier.create(service.enrollDocument(doc))
                .verifyComplete();

        verifyNoInteractions(batchRepository);
    }

    @Test
    @DisplayName("Xếp vào lô thành công khi chưa đủ batchSize -> size tăng lên, chưa trigger Python")
    void enrollDoc_whenBatchNotFull_shouldIncrementSizeOnly() {
        Document doc = Document.builder()
                .id(3L)
                .trainingEligible(true)
                .modelVersion("rule-template-v1")
                .build();

        TrainingBatch openBatch = TrainingBatch.builder()
                .id(100L)
                .status(TrainingBatchStatus.OPEN.name())
                .size(5)
                .build();

        TrainingBatch updatedBatch = TrainingBatch.builder()
                .id(100L)
                .status(TrainingBatchStatus.OPEN.name())
                .size(6)
                .build();

        when(batchRepository.findLatestByStatus(TrainingBatchStatus.OPEN.name()))
                .thenReturn(Mono.just(openBatch));
        when(documentRepository.assignToBatchIfEligible(3L, 100L))
                .thenReturn(Mono.just(1));
        when(batchRepository.incrementBatchSize(100L))
                .thenReturn(Mono.just(1));
        when(batchRepository.findById(100L))
                .thenReturn(Mono.just(updatedBatch));

        StepVerifier.create(service.enrollDocument(doc))
                .assertNext(batch -> {
                    assertThat(batch.getId()).isEqualTo(100L);
                    assertThat(batch.getSize()).isEqualTo(6);
                    assertThat(batch.getStatus()).isEqualTo(TrainingBatchStatus.OPEN.name());
                })
                .verifyComplete();

        verify(batchRepository, never()).updateStatus(anyLong(), eq(TrainingBatchStatus.READY.name()));
        verifyNoInteractions(pythonMlClient);
    }

    @Test
    @DisplayName("Xếp vào lô và đạt batchSize (25) -> chuyển READY, kích hoạt Python ML, chuyển RUNNING")
    void enrollDoc_whenBatchReachesThreshold_shouldTriggerPythonTraining() {
        Document doc = Document.builder()
                .id(4L)
                .trainingEligible(true)
                .modelVersion("rule-template-v1")
                .build();

        TrainingBatch openBatch = TrainingBatch.builder()
                .id(200L)
                .status(TrainingBatchStatus.OPEN.name())
                .size(24)
                .build();

        TrainingBatch fullBatch = TrainingBatch.builder()
                .id(200L)
                .status(TrainingBatchStatus.OPEN.name())
                .size(25)
                .build();

        TrainingBatch runningBatch = TrainingBatch.builder()
                .id(200L)
                .status(TrainingBatchStatus.RUNNING.name())
                .size(25)
                .build();

        when(batchRepository.findLatestByStatus(TrainingBatchStatus.OPEN.name()))
                .thenReturn(Mono.just(openBatch));
        when(documentRepository.assignToBatchIfEligible(4L, 200L))
                .thenReturn(Mono.just(1));
        when(batchRepository.incrementBatchSize(200L))
                .thenReturn(Mono.just(1));
        when(batchRepository.findById(200L))
                .thenReturn(Mono.just(fullBatch), Mono.just(runningBatch));
        when(batchRepository.updateStatus(200L, TrainingBatchStatus.READY.name()))
                .thenReturn(Mono.just(1));
        when(pythonMlClient.triggerLearnBatch(200L))
                .thenReturn(Mono.empty());
        when(batchRepository.updateStatus(200L, TrainingBatchStatus.RUNNING.name()))
                .thenReturn(Mono.just(1));

        StepVerifier.create(service.enrollDocument(doc))
                .assertNext(batch -> {
                    assertThat(batch.getId()).isEqualTo(200L);
                    assertThat(batch.getStatus()).isEqualTo(TrainingBatchStatus.RUNNING.name());
                })
                .verifyComplete();

        verify(batchRepository).updateStatus(200L, TrainingBatchStatus.READY.name());
        verify(pythonMlClient).triggerLearnBatch(200L);
        verify(batchRepository).updateStatus(200L, TrainingBatchStatus.RUNNING.name());
    }
}
