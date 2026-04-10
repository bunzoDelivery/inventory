package com.quickcommerce.product.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Configures the S3AsyncClient for Cloudflare R2 (prod) or MinIO (local dev).
 * Both are S3-compatible, so the same client works for either.
 */
@Configuration
@RequiredArgsConstructor
public class R2StorageConfig {

    private final R2StorageProperties properties;

    @Bean
    public S3AsyncClient s3AsyncClient() {
        validateProperties();
        URI endpointUri = parseEndpoint(properties.getEndpoint());
        return S3AsyncClient.builder()
                .endpointOverride(endpointUri)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(
                                properties.getAccessKeyId(),
                                properties.getSecretAccessKey())))
                .region(Region.of(properties.getRegion()))
                .forcePathStyle(true)
                .build();
    }

    private void validateProperties() {
        if (!StringUtils.hasText(properties.getEndpoint())) {
            throw new IllegalStateException("R2 storage endpoint is not configured (r2.endpoint)");
        }
        if (!StringUtils.hasText(properties.getAccessKeyId())) {
            throw new IllegalStateException("R2 access key ID is not configured (r2.access-key-id)");
        }
        if (!StringUtils.hasText(properties.getSecretAccessKey())) {
            throw new IllegalStateException("R2 secret access key is not configured (r2.secret-access-key)");
        }
        if (!StringUtils.hasText(properties.getBucket())) {
            throw new IllegalStateException("R2 bucket name is not configured (r2.bucket)");
        }
        if (!StringUtils.hasText(properties.getRegion())) {
            throw new IllegalStateException("R2 region is not configured (r2.region)");
        }
    }

    private URI parseEndpoint(String endpoint) {
        try {
            return new URI(endpoint);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(
                    "Invalid R2 endpoint URL: '" + endpoint + "' — " + e.getMessage(), e);
        }
    }
}
