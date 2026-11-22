package net.snobo.service.exception;

import net.snobo.domain.ClaimNumbers;

public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(long claimNumber, String paymentId) {
        super("No payment " + paymentId + " for claim " + ClaimNumbers.format(claimNumber));
    }
}
