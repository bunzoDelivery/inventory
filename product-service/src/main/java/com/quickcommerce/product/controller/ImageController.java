package com.quickcommerce.product.controller;

import com.quickcommerce.product.dto.ImageUploadResponse;
import com.quickcommerce.product.service.ImageUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * REST controller for image upload to Cloudflare R2 / MinIO.
 * Returns an r2Key that callers pass into the product sync or create APIs.
 */
@RestController
@RequestMapping("/api/v1/images")
@RequiredArgsConstructor
@Tag(name = "Images", description = "Product image upload APIs")
public class ImageController {

    private final ImageUploadService imageUploadService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload a product image",
            description = """
                    Uploads a JPEG or PNG image to object storage.
                    Returns an r2Key to be included in the product's images array.
                    Max file size: 5MB. Validated via magic bytes (not file extension).
                    """
    )
    public Mono<ImageUploadResponse> uploadImage(@RequestPart("image") FilePart image) {
        return imageUploadService.upload(image)
                .map(ImageUploadResponse::new);
    }
}
