package net.snobo.rest.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import net.snobo.db.entity.Claim;
import net.snobo.domain.ClaimNumbers;

public record ClaimResponse(String claimNumber, BigDecimal totalAmount, String debtorName, LocalDate createdOn) {

    public static ClaimResponse from(Claim claim) {
        return new ClaimResponse(
                ClaimNumbers.format(claim.getClaimNumber()),
                claim.getTotalAmount(),
                claim.getDebtorName(),
                claim.getCreatedOn());
    }
}
