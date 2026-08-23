package com.sirwellington.target.rest;

/**
 * Thrown when a REST operation fails for an internal reason.
 */
public class OperationFailedException extends RuntimeException {
    public OperationFailedException(String message) {
        super(message);
    }

    public OperationFailedException(String message, Throwable cause) {
        super(message, cause);
    }

    public OperationFailedException(Throwable cause) {
        super(cause);
    }
}
