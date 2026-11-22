package net.snobo.rest.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import net.snobo.domain.ClaimNumbers;

public record Claim(String claimNumber, BigDecimal totalAmount, LocalDate createdOn, String debtorName,
                    PaymentPlan paymentPlan) {

    public Claim(net.snobo.db.entity.Claim claim) {
        this(ClaimNumbers.format(claim.getClaimNumber()), claim.getTotalAmount(), claim.getCreatedOn(),
                claim.getDebtorName(), null);
    }

}
