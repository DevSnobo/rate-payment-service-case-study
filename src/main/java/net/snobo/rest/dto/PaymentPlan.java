package net.snobo.rest.dto;

import java.math.BigDecimal;
import java.util.List;

import net.snobo.domain.Status;

public record PaymentPlan(Long claimNumber, BigDecimal totalClaimAmount, BigDecimal outstandingBalance,
                          BigDecimal amountPaid, Status status, List<Installment> installments) {

}
