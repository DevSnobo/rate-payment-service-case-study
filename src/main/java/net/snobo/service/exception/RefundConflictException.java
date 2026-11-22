package net.snobo.service.exception;

public class RefundConflictException extends RuntimeException {

    public RefundConflictException(String message) {
        super(message);
    }
}
