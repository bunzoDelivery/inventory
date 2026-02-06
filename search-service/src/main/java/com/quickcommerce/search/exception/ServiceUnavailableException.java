package com.quickcommerce.search.exception;

/**
 * Exception thrown when external service is unavailable
 */
public class ServiceUnavailableException extends SearchException {
    public ServiceUnavailableException(String message) {
        super(message);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
