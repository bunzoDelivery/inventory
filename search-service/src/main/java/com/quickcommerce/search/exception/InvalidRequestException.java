package com.quickcommerce.search.exception;

/**
 * Exception thrown when request validation fails
 */
public class InvalidRequestException extends SearchException {
    public InvalidRequestException(String message) {
        super(message);
    }
}
