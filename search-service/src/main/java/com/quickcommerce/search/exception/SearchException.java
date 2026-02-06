package com.quickcommerce.search.exception;

/**
 * Base exception for search service errors
 */
public class SearchException extends RuntimeException {
    public SearchException(String message) {
        super(message);
    }

    public SearchException(String message, Throwable cause) {
        super(message, cause);
    }
}
