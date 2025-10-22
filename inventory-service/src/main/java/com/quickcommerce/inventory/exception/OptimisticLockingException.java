package com.quickcommerce.inventory.exception;

/**
 * Exception thrown when optimistic locking fails due to concurrent modifications
 */
public class OptimisticLockingException extends RuntimeException {

    public OptimisticLockingException(String message) {
        super(message);
    }

    public OptimisticLockingException(String message, Throwable cause) {
        super(message, cause);
    }
}
