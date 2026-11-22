package net.snobo.service.exception;

public class DuplicateRefundReferenceException extends RuntimeException {

    public DuplicateRefundReferenceException(String paymentId, String reference) {
        super("Refund reference '" + reference + "' already exists for payment " + paymentId);
    }
}
