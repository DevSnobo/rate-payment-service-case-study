package net.snobo.rest.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import net.snobo.db.entity.Installment;

public record InstallmentResponse(int installmentNumber, BigDecimal scheduledAmount, BigDecimal amountPaid,
                                  LocalDate dueDate) {

    public static InstallmentResponse from(Installment installment) {
        return new InstallmentResponse(
                installment.getInstallmentNumber(),
                installment.getScheduledAmount(),
                installment.getAmountPaid(),
                installment.getDueDate());
    }
}
