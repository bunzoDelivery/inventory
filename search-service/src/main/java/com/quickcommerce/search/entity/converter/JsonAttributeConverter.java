package com.quickcommerce.search.entity.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * Converter for storing JSON structures in database columns
 * Maps Object/List/Map <-> JSON String in DB
 */
@Slf4j
@Converter
public class JsonAttributeConverter implements AttributeConverter<Object, String> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(Object attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            log.error("Could not convert object to JSON string", e);
            throw new IllegalArgumentException("Error converting to JSON", e);
        }
    }

    @Override
    public Object convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        try {
            return objectMapper.readValue(dbData, Object.class);
        } catch (IOException e) {
            log.error("Could not convert JSON string to object", e);
            throw new IllegalArgumentException("Error converting from JSON", e);
        }
    }
}
