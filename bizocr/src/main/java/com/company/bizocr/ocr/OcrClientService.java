package com.company.bizocr.ocr;

import com.company.bizocr.model.dto.OcrResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
@Service
public class OcrClientService {

    private static final String OCR_ENDPOINT = "/api/v1/ocr";
    private static final Duration OCR_TIMEOUT = Duration.ofSeconds(180);

    private final WebClient ocrWebClient;

    // Sử dụng @Qualifier để chọn đích danh Bean ocrWebClient và bổ sung defaultHeader X-Internal-Token
    public OcrClientService(
            @Qualifier("ocrWebClient") WebClient ocrWebClient,
            @Value("${bizocr.security.internal-token:${INTERNAL_TOKEN:dev-internal-token}}") String internalToken) {

        this.ocrWebClient = ocrWebClient.mutate()
                .defaultHeader("X-Internal-Token", internalToken)
                .build();
    }

    public Mono<OcrResult> extractText(FilePart filePart) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();

        builder.part("file", filePart)
                .filename(filePart.filename())
                .contentType(MediaType.APPLICATION_OCTET_STREAM);

        log.info("Bắt đầu gọi OCR service từ FilePart, file={}",
                filePart.filename());

        return callOcr(builder, filePart.filename());
    }

    public Mono<OcrResult> scanDocument(Resource resource) {
        String filename = resource.getFilename() != null
                ? resource.getFilename()
                : "document";

        MultipartBodyBuilder builder = new MultipartBodyBuilder();

        builder.part("file", resource)
                .filename(filename)
                .contentType(MediaType.APPLICATION_OCTET_STREAM);

        log.info("Bắt đầu gọi OCR service từ Resource, file={}", filename);

        return callOcr(builder, filename);
    }

    private Mono<OcrResult> callOcr(
            MultipartBodyBuilder builder,
            String filename
    ) {
        return ocrWebClient
                .post()
                .uri(OCR_ENDPOINT)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .accept(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .onStatus(
                        status -> status.isError(),
                        response -> response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> {
                                    log.error(
                                            "OCR service trả HTTP {}, file={}, body={}",
                                            response.statusCode(),
                                            filename,
                                            body
                                    );

                                    return Mono.error(
                                            new IllegalStateException(
                                                    "OCR service trả HTTP "
                                                            + response.statusCode()
                                                            + (body.isBlank() ? "" : ": " + body)
                                            )
                                    );
                                })
                )
                .bodyToMono(OcrResult.class)
                .switchIfEmpty(Mono.error(
                        new IllegalStateException(
                                "OCR service trả response body rỗng"
                        )
                ))
                .timeout(OCR_TIMEOUT)
                .doOnSuccess(result ->
                        log.info(
                                "OCR service trả kết quả thành công, file={}, rawTextLength={}, "
                                        + "contourAreaRatio={}, aspectRatioAfterCrop={}, "
                                        + "qualityMode={}, qualityPassed={}, qualityReasons={}",
                                filename,
                                result.getRawText() == null
                                        ? 0
                                        : result.getRawText().length(),
                                result.getContourAreaRatio(),
                                result.getAspectRatioAfterCrop(),
                                result.getQualityMode(),
                                result.getQualityPassed(),
                                result.getQualityReasons()
                        )
                )
                .doOnError(error ->
                        log.error(
                                "Gọi OCR service thất bại, file={}",
                                filename,
                                error
                        )
                );
    }
}