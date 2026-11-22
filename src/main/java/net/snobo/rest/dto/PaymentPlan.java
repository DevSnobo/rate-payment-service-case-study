package net.snobo.rest.dto;

import java.math.BigDecimal;
import java.util.List;

import net.snobo.domain.Status;

public record PaymentPlan(Claim claim, BigDecimal totalClaimAmount, Status status,
                          List<Installment> installments) {

}
