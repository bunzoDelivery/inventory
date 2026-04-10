package com.quickcommerce.product.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for Cloudflare R2 / MinIO object storage.
 * Prefix: r2
 */
@Data
@Component
@ConfigurationProperties(prefix = "r2")
public class R2StorageProperties {

    private String accessKeyId;
    private String secretAccessKey;
    private String endpoint;
    private String bucket;
    private String region = "auto";
}
