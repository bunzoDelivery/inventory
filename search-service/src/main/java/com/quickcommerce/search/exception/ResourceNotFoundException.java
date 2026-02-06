package com.quickcommerce.search.exception;

/**
 * Exception thrown when a requested resource is not found
 */
public class ResourceNotFoundException extends SearchException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
