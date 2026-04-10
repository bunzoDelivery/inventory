package com.quickcommerce.product.service;

import com.quickcommerce.product.config.R2StorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.UUID;

/**
 * Handles image validation and upload to S3-compatible storage (Cloudflare R2 / MinIO).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImageUploadService {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

    private final S3AsyncClient s3AsyncClient;
    private final R2StorageProperties storageProperties;

    /**
     * Validates and uploads an image file, returning the r2Key.
     *
     * @param filePart the uploaded file
     * @return Mono emitting the r2Key (e.g. "products/{uuid}/original.jpg")
     */
    public Mono<String> upload(FilePart filePart) {
        if (filePart == null) {
            return Mono.error(new IllegalArgumentException("Image file is required"));
        }

        return DataBufferUtils.join(filePart.content())
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Image file is empty")))
                .flatMap(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);

                    if (bytes.length == 0) {
                        return Mono.error(new IllegalArgumentException("Image file is empty"));
                    }

                    if (bytes.length > MAX_FILE_SIZE) {
                        return Mono.error(new IllegalArgumentException(
                                "File size exceeds 5MB limit"));
                    }

                    String extension = detectImageType(bytes);
                    if (extension == null) {
                        return Mono.error(new IllegalArgumentException(
                                "Only JPEG or PNG allowed"));
                    }

                    String r2Key = "products/" + UUID.randomUUID() + "/original." + extension;
                    String contentType = "jpg".equals(extension) ? "image/jpeg" : "image/png";

                    PutObjectRequest putRequest = PutObjectRequest.builder()
                            .bucket(storageProperties.getBucket())
                            .key(r2Key)
                            .contentType(contentType)
                            .contentLength((long) bytes.length)
                            .build();

                    log.info("Uploading image: {} ({} bytes, {})", r2Key, bytes.length, contentType);

                    return Mono.fromFuture(() ->
                            s3AsyncClient.putObject(putRequest, AsyncRequestBody.fromBytes(bytes)))
                            .thenReturn(r2Key)
                            .doOnSuccess(key -> log.info("Upload complete: {}", key))
                            .doOnError(e -> log.error("Upload failed for {}: {}", r2Key, e.getMessage()));
                });
    }

    /**
     * Validates magic bytes and returns the file extension, or null if invalid.
     * JPEG: first 2 bytes FF D8
     * PNG:  first 8 bytes 89 50 4E 47 0D 0A 1A 0A
     */
    private String detectImageType(byte[] bytes) {
        if (bytes.length < 8) {
            return null;
        }
        if (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8) {
            return "jpg";
        }
        if (bytes[0] == (byte) 0x89 && bytes[1] == (byte) 0x50
                && bytes[2] == (byte) 0x4E && bytes[3] == (byte) 0x47
                && bytes[4] == (byte) 0x0D && bytes[5] == (byte) 0x0A
                && bytes[6] == (byte) 0x1A && bytes[7] == (byte) 0x0A) {
            return "png";
        }
        return null;
    }
}
