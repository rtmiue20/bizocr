package com.company.bizocr.controller;

import com.company.bizocr.service.OcrQueueConsumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Endpoint nội bộ để quản lý OCR queue và DLQ.
 * Chỉ dành cho ADMIN — bảo vệ bởi Spring Security role ADMIN.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/ocr")
@RequiredArgsConstructor
public class AdminOcrController {

    private final OcrQueueConsumer ocrQueueConsumer;

    /**
     * Chạy lại 1 document cụ thể từ DLQ.
     * Dùng ngay để retry document 2127 sau khi đã fix CompanyFinderService.
     *
     * <pre>
     * curl -X POST http://localhost:8080/api/v1/admin/ocr/requeue/2127 \
     *      -H "Authorization: Bearer <token>"
     * </pre>
     */
    @PostMapping("/requeue/{documentId}")
    public Mono<Map<String, Object>> requeueDocument(@PathVariable Long documentId) {
        if (documentId == null || documentId <= 0) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "documentId không hợp lệ: " + documentId));
        }
        log.info("[AdminOcr] Nhận yêu cầu requeue documentId={}", documentId);
        return ocrQueueConsumer.requeueDocumentFromDlq(documentId)
                .map(ok -> Map.<String, Object>of(
                        "success", ok,
                        "documentId", documentId,
                        "message", "documentId=" + documentId + " đã được đẩy lại queue chính. Status reset về PENDING."
                ))
                .onErrorMap(IllegalArgumentException.class,
                        e -> new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage()));
    }

    /**
     * Chạy lại toàn bộ document trong DLQ.
     * Dùng sau khi fix xong lỗi hệ thống.
     *
     * <pre>
     * curl -X POST http://localhost:8080/api/v1/admin/ocr/requeue-all \
     *      -H "Authorization: Bearer <token>"
     * </pre>
     */
    @PostMapping("/requeue-all")
    public Mono<Map<String, Object>> requeueAll() {
        log.info("[AdminOcr] Nhận yêu cầu requeue toàn bộ DLQ");
        return ocrQueueConsumer.requeueAllFromDlq()
                .map(count -> Map.<String, Object>of(
                        "success", true,
                        "requeuedCount", count,
                        "message", "Đã đẩy " + count + " document từ DLQ về queue chính."
                ));
    }
}
