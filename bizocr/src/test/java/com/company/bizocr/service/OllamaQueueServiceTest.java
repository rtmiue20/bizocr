package com.company.bizocr.service;

import com.company.bizocr.repository.DocumentRepository;
import com.company.bizocr.validator.ExtractionValidatorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OllamaQueueServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ListOperations<String, String> listOperations;

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ExtractionValidatorService extractionValidatorService;

    @Mock
    private DocumentRepository documentRepository;

    private ObjectMapper objectMapper;
    private OllamaQueueService queueService;

    @BeforeEach
    void setUp() {
        when(chatClientBuilder.build()).thenReturn(chatClient);
        objectMapper = new ObjectMapper();
        queueService = new OllamaQueueService(
                redisTemplate, objectMapper, chatClientBuilder, extractionValidatorService, documentRepository);
    }

    @Test
    @DisplayName("Vòng đời Worker - Khởi động và Dừng êm qua SmartLifecycle đúng quy trình")
    void testLifecycleStartAndStop() {
        assertFalse(queueService.isRunning(), "Ban đầu worker chưa chạy");
        assertTrue(queueService.isAutoStartup(), "Phải auto startup trong Spring Context");
        assertTrue(queueService.getPhase() > 0, "Phase phải cao hơn LettuceConnectionFactory (Phase 0)");

        queueService.start();
        assertTrue(queueService.isRunning(), "Worker phải đang chạy sau khi start()");

        // Gọi start lần 2 không gây lỗi (idempotent)
        queueService.start();
        assertTrue(queueService.isRunning());

        // Dừng worker qua SmartLifecycle stop(callback)
        AtomicBoolean callbackExecuted = new AtomicBoolean(false);
        queueService.stop(() -> callbackExecuted.set(true));
        assertFalse(queueService.isRunning(), "Worker phải dừng sau khi stop()");
        assertTrue(callbackExecuted.get(), "Callback phải được thực thi khi stop()");

        // Gọi stopWorker lần 2 an toàn (idempotent)
        assertDoesNotThrow(() -> queueService.stopWorker());
    }

    @Test
    @DisplayName("enqueueTask - Đẩy task thành công khi worker đang chạy")
    void testEnqueueTaskWhenRunning() {
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        queueService.startWorker();

        queueService.enqueueTask("doc-1", "VAT_INVOICE", "raw text");

        verify(listOperations, times(1)).leftPush(eq("ocr:ollama:tasks"), anyString());
        queueService.stopWorker();
    }

    @Test
    @DisplayName("enqueueTask - Bỏ qua push task khi service đã dừng")
    void testEnqueueTaskWhenStopped() {
        // queueService chưa start hoặc đã dừng
        queueService.enqueueTask("doc-2", "VAT_INVOICE", "raw text");

        verify(redisTemplate, never()).opsForList();
    }
}