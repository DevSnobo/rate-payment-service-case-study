package net.snobo.rest.dto;

import java.util.List;

public record PaymentPlan(Long claimNumber, Double totalClaimAmount, Double outstandingBalance, Double amountPaid,
                          Status status, List<Installment> installments) {

}
