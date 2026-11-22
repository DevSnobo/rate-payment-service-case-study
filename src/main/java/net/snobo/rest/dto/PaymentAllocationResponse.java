package net.snobo.rest.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import net.snobo.db.entity.PaymentAllocation;

public record PaymentAllocationResponse(int installmentNumber, BigDecimal amount, LocalDate dueDate) {

    public static PaymentAllocationResponse from(PaymentAllocation allocation) {
        return new PaymentAllocationResponse(
                allocation.getInstallment().getInstallmentNumber(),
                allocation.getAmount(),
                allocation.getInstallment().getDueDate());
    }
}
