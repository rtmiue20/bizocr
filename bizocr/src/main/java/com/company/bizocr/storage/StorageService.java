package com.company.bizocr.storage;

import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class StorageService {

    private final Path rootLocation = Paths.get("uploads");

    public StorageService() {
        try {
            // Tự động tạo thư mục 'uploads' ở thư mục gốc của project nếu chưa có
            Files.createDirectories(rootLocation);
        } catch (Exception e) {
            throw new RuntimeException("Không thể tạo thư mục lưu trữ uploads/", e);
        }
    }

    public Mono<String> saveFile(FilePart filePart) {
        // Tạo thư mục con theo ngày (VD: uploads/2026-08-25) để dễ quản lý
        String dateFolder = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        Path targetFolder = this.rootLocation.resolve(dateFolder);

        try {
            Files.createDirectories(targetFolder);
        } catch (Exception e) {
            return Mono.error(new RuntimeException("Lỗi tạo thư mục ngày: " + dateFolder, e));
        }

        // Tạo tên file ngẫu nhiên để chống trùng lặp (VD: 123e4567..._scan.jpg)
        String uniqueFileName = UUID.randomUUID().toString() + "_" + filePart.filename();
        Path destinationFile = targetFolder.resolve(uniqueFileName).normalize().toAbsolutePath();

        // Lưu file vật lý xuống ổ cứng (Reactive)
        return filePart.transferTo(destinationFile)
                // Trả về đường dẫn tương đối để lưu vào Database
                .then(Mono.just(dateFolder + "/" + uniqueFileName));
    }

    public Mono<Resource> loadFileAsResource(String relativePath) {
        return Mono.fromCallable(() -> {
            try {
                Path filePath = this.rootLocation.resolve(relativePath).normalize();
                Resource resource = new UrlResource(filePath.toUri());

                if (resource.exists() || resource.isReadable()) {
                    return resource;
                } else {
                    throw new RuntimeException("Không tìm thấy file hoặc không thể đọc: " + relativePath);
                }
            } catch (Exception e) {
                throw new RuntimeException("Lỗi tải file: " + relativePath, e);
            }
        });
    }
}