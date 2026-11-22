package net.snobo.service.exception;

import net.snobo.domain.ClaimNumbers;

public class PaymentPlanNotFoundException extends RuntimeException {

    public PaymentPlanNotFoundException(long claimNumber) {
        super("No payment plan for claim " + ClaimNumbers.format(claimNumber));
    }
}
