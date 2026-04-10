package com.quickcommerce.product.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

/**
 * Converts between the JSON string stored in the DB {@code images} column
 * and the {@code List<String>} used in DTOs / API contracts.
 */
public final class ImageJsonUtils {

    private static final Logger log = LoggerFactory.getLogger(ImageJsonUtils.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> LIST_TYPE = new TypeReference<>() {};

    private ImageJsonUtils() {}

    public static List<String> parseImages(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(json, LIST_TYPE);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse images JSON, returning empty list. Value: '{}', error: {}",
                    json, e.getMessage());
            return Collections.emptyList();
        }
    }

    public static String toJson(List<String> images) {
        if (images == null || images.isEmpty()) {
            return "[]";
        }
        try {
            return MAPPER.writeValueAsString(images);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}
