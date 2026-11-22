package net.snobo.rest.dto;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;

import net.snobo.db.entity.Claim;
import net.snobo.db.entity.PaymentPlan;
import net.snobo.domain.ClaimNumbers;
import net.snobo.domain.Status;

public record PaymentPlanResponse(String claimNumber, BigDecimal totalClaimAmount, BigDecimal amountPaid,
                                  BigDecimal outstandingBalance, Status status,
                                  List<InstallmentResponse> installments) {

    public static PaymentPlanResponse from(PaymentPlan plan, Clock clock) {
        Claim claim = plan.getClaim();
        return new PaymentPlanResponse(
                ClaimNumbers.format(claim.getClaimNumber()),
                claim.getTotalAmount(),
                plan.amountPaid(),
                plan.outstandingBalance(),
                plan.statusAt(clock),
                plan.getInstallments().stream().map(InstallmentResponse::from).toList());
    }
}
