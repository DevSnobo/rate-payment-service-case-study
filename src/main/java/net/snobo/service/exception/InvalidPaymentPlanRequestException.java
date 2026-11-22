package net.snobo.service.exception;

public class InvalidPaymentPlanRequestException extends RuntimeException {

    public InvalidPaymentPlanRequestException(String message) {
        super(message);
    }

    public InvalidPaymentPlanRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
