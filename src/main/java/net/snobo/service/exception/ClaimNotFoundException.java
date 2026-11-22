package net.snobo.service.exception;

import net.snobo.domain.ClaimNumbers;

public class ClaimNotFoundException extends RuntimeException {

    public ClaimNotFoundException(long claimNumber) {
        super("No claim " + ClaimNumbers.format(claimNumber));
    }
}
