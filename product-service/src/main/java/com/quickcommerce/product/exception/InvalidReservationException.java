package com.quickcommerce.product.exception;

/**
 * Exception thrown when reservation operation is invalid
 */
public class InvalidReservationException extends RuntimeException {

    public InvalidReservationException(String message) {
        super(message);
    }

    public InvalidReservationException(String message, Throwable cause) {
        super(message, cause);
    }
}
