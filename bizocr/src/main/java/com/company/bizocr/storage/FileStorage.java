package com.company.bizocr.storage;

import org.springframework.core.io.Resource;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

public interface FileStorage {
    /**
     * Lưu file vật lý, trả về storage_key tương đối (VD: "2026-10-06/uuid_name.jpg").
     */
    Mono<String> save(FilePart filePart);

    /**
     * Đọc file từ storage_key tương đối.
     */
    Mono<Resource> load(String storageKey);
}
