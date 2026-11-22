package net.snobo.service.exception;

import net.snobo.domain.ClaimNumbers;

public class PaymentPlanAlreadyExistsException extends RuntimeException {

    public PaymentPlanAlreadyExistsException(long claimNumber) {
        super("Payment plan already exists for claim " + ClaimNumbers.format(claimNumber));
    }
}
