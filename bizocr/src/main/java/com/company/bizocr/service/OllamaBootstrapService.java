package com.company.bizocr.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Tự động quản lý vòng đời tiến trình Ollama khi chạy Spring Boot ở local (Windows).
 *
 * Do driver GPU (Quadro P1000, driver 556.12) bị lỗi "CUDA error: unsupported toolchain"
 * khiến llama-server crash liên tục, buộc phải ép Ollama chạy CPU-only
 * (CUDA_VISIBLE_DEVICES="", OLLAMA_LLM_LIBRARY=cpu) cho đến khi driver được downgrade/sửa.
 *
 * CHỈ chạy trên Windows local dev (bizocr.ollama.auto-manage=true, mặc định true).
 * Trong Docker/Linux, Ollama chạy ở container riêng (OLLAMA_BASE_URL trỏ tới đó) -> tắt cơ chế này.
 */
@Slf4j
@Component
public class OllamaBootstrapService {

    @Value("${bizocr.ollama.auto-manage:true}")
    private boolean autoManage;

    @Value("${bizocr.ollama.force-cpu:true}")
    private boolean forceCpu;

    @Value("${bizocr.ollama.startup-timeout-seconds:60}")
    private int startupTimeoutSeconds;

    private final WebClient healthCheckClient = WebClient.builder()
            .baseUrl("http://127.0.0.1:11434")
            .build();

    @EventListener(ApplicationReadyEvent.class)
    public void manageOllamaOnStartup() {
        if (!autoManage) {
            log.info("[OllamaBootstrap] Tự động quản lý Ollama đang TẮT (bizocr.ollama.auto-manage=false), bỏ qua.");
            return;
        }
        if (!isWindows()) {
            log.info("[OllamaBootstrap] Không phải Windows, bỏ qua tự động quản lý tiến trình Ollama (giả định Docker/Linux tự quản lý riêng).");
            return;
        }

        try {
            killExistingOllamaProcesses();
            Thread.sleep(1500); // đợi Windows giải phóng cổng 11434 hẳn trước khi serve lại
            startOllamaServe();
            waitUntilReady();
        } catch (Exception e) {
            log.error("[OllamaBootstrap] Không thể tự khởi động Ollama, AI fallback sẽ không hoạt động cho tới khi khởi động thủ công: {}", e.getMessage(), e);
        }
    }

    private void killExistingOllamaProcesses() throws IOException, InterruptedException {
        for (String processName : List.of("ollama app.exe", "ollama.exe")) {
            ProcessBuilder pb = new ProcessBuilder("taskkill", "/IM", processName, "/F");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.waitFor(5, TimeUnit.SECONDS);
        }
        log.info("[OllamaBootstrap] Đã dọn dẹp tiến trình Ollama cũ (nếu có).");
    }

    private void startOllamaServe() throws IOException {
        ProcessBuilder pb = new ProcessBuilder("ollama", "serve");

        if (forceCpu) {
            // Ép CPU-only: driver GPU hiện đang lỗi CUDA PTX toolchain.
            // Xoá driver GPU khi driver được sửa xong bằng cách set bizocr.ollama.force-cpu=false.
            pb.environment().put("CUDA_VISIBLE_DEVICES", "");
            pb.environment().put("OLLAMA_LLM_LIBRARY", "cpu");
        }

        // Log riêng ra file, không dính vào console/log của Spring Boot cho rối
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(new java.io.File("ollama-runtime.log")));
        pb.redirectError(ProcessBuilder.Redirect.appendTo(new java.io.File("ollama-runtime.log")));

        Process process = pb.start();
        log.info("[OllamaBootstrap] Đã khởi động tiến trình 'ollama serve' (PID={}, forceCpu={}). Log tại ollama-runtime.log",
                process.pid(), forceCpu);

        // KHÔNG gọi process.waitFor() -> đây là tiến trình chạy nền dài hạn, không phải lệnh chạy-xong-thoát.
        // Không cần kill khi Spring Boot tắt: để Ollama tiếp tục chạy nền độc lập, tránh phải khởi động lại mỗi lần restart app khi dev.
    }

    private void waitUntilReady() {
        log.info("[OllamaBootstrap] Đang chờ Ollama sẵn sàng (tối đa {}s)...", startupTimeoutSeconds);

        healthCheckClient.get()
                .uri("/api/tags")
                .retrieve()
                .toBodilessEntity()
                .retryWhen(Retry.fixedDelay(startupTimeoutSeconds, Duration.ofSeconds(1))
                        .filter(e -> true))
                .doOnSuccess(res -> log.info("[OllamaBootstrap] Ollama đã sẵn sàng nhận request."))
                .doOnError(e -> log.error("[OllamaBootstrap] Ollama không phản hồi sau {}s, kiểm tra ollama-runtime.log để xem lỗi.", startupTimeoutSeconds))
                .onErrorResume(e -> Mono.empty())
                .block(Duration.ofSeconds(startupTimeoutSeconds + 5L));
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}