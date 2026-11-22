package net.snobo.service.exception;

public class InvalidRefundRequestException extends RuntimeException {

    public InvalidRefundRequestException(String message) {
        super(message);
    }

    public InvalidRefundRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
