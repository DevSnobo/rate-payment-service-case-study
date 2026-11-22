package net.snobo.service.exception;

import net.snobo.domain.ClaimNumbers;

public class DuplicatePaymentReferenceException extends RuntimeException {

    public DuplicatePaymentReferenceException(long claimNumber, String reference) {
        super("Payment reference '" + reference + "' already exists for claim "
                + ClaimNumbers.format(claimNumber));
    }
}
