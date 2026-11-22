package net.snobo.service.exception;

import net.snobo.domain.ClaimNumbers;

public class DuplicateClaimException extends RuntimeException {

    public DuplicateClaimException(long claimNumber) {
        super("Claim " + ClaimNumbers.format(claimNumber) + " already exists");
    }
}
