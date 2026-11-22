package net.snobo.service.exception;

public class InvalidClaimRequestException extends RuntimeException {

    public InvalidClaimRequestException(String message) {
        super(message);
    }

    public InvalidClaimRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
