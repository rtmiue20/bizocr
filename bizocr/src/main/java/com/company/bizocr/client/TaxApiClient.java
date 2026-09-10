package com.company.bizocr.client;

import com.company.bizocr.model.dto.response.TaxLookupResponse;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Tra cứu thông tin doanh nghiệp theo Mã số thuế (MST) qua VietQR.IO Business Lookup API.
 *
 * Nguồn: API công khai, miễn phí, do CASSO vận hành trong hệ sinh thái VietQR (liên kết NAPAS).
 * Docs: https://www.vietqr.io/en/danh-sach-api/tax-id-lookup/
 * Endpoint: GET https://api.vietqr.io/v2/business/{taxCode}
 *
 * LƯU Ý:
 * - API này CHỈ tra được theo taxCode, KHÔNG hỗ trợ tìm theo tên công ty.
 *   Nếu taxCode rỗng, bỏ qua tra cứu (companyName không dùng được với nguồn này).
 * - Có giới hạn rate: trả HTTP 429 khi vượt quota — xử lý fallback graceful, không throw lỗi lên trên.
 */
@Slf4j
@Component
public class TaxApiClient {

    private static final String VIETQR_BASE_URL = "https://api.vietqr.io";
    private static final String SUCCESS_CODE = "00";

    private final WebClient vietQrWebClient = WebClient.builder()
            .baseUrl(VIETQR_BASE_URL)
            .build();

    public Mono<TaxLookupResponse> lookup(String taxCode, String companyName) {
        if (taxCode == null || taxCode.isBlank()) {
            log.info("[TaxApiClient] Không có taxCode, bỏ qua tra cứu VietQR (companyName='{}' không dùng được với nguồn này).", companyName);
            return Mono.empty();
        }

        String cleanTaxCode = taxCode.trim();

        return vietQrWebClient
                .get()
                .uri("/v2/business/{taxCode}", cleanTaxCode)
                .retrieve()
                .onStatus(status -> status.value() == 429, response -> {
                    log.warn("[TaxApiClient] VietQR API rate-limit (429) khi tra MST={}", cleanTaxCode);
                    return Mono.empty();
                })
                .onStatus(HttpStatusCode::isError, response -> {
                    log.warn("[TaxApiClient] VietQR API lỗi HTTP khi tra MST={}, status={}", cleanTaxCode, response.statusCode());
                    return Mono.empty();
                })
                .bodyToMono(VietQrResponse.class)
                .timeout(Duration.ofSeconds(5))
                .flatMap(this::toTaxLookupResponse)
                // Nếu sập kết nối, timeout, hoặc parse lỗi thì tự động bỏ qua tra cứu (graceful fallback)
                .onErrorResume(e -> {
                    log.warn("[TaxApiClient] Bỏ qua tra cứu MST={} do lỗi kết nối VietQR API: {}", cleanTaxCode, e.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<TaxLookupResponse> toTaxLookupResponse(VietQrResponse response) {
        if (response == null || response.getData() == null || !SUCCESS_CODE.equals(response.getCode())) {
            log.info("[TaxApiClient] VietQR không tìm thấy doanh nghiệp, code={}, desc={}",
                    response != null ? response.getCode() : null,
                    response != null ? response.getDesc() : null);
            return Mono.empty();
        }

        VietQrBusinessData data = response.getData();
        return Mono.just(TaxLookupResponse.builder()
                .taxCode(data.getId())
                .legalName(data.getName())
                .address(data.getAddress())
                .status("FOUND")
                .source("VIETQR")
                .build());
    }

    // --- DTO nội bộ khớp cấu trúc JSON trả về của VietQR API, chỉ dùng riêng trong class này ---

    @Data
    private static class VietQrResponse {
        private String code;
        private String desc;
        private VietQrBusinessData data;
    }

    @Data
    private static class VietQrBusinessData {
        private String id;                 // taxCode chuẩn hoá từ VietQR
        private String name;                // tên pháp lý đầy đủ
        private String internationalName;   // tên quốc tế (nếu có)
        private String shortName;           // tên viết tắt
        private String address;             // địa chỉ đăng ký
    }
}