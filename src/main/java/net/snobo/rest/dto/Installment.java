package net.snobo.rest.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Installment(Long installmentNumber, BigDecimal amountPaid, BigDecimal scheduledAmount,
                          LocalDate dueDate) {

    public Installment(net.snobo.db.entity.Installment entity) {
        this(entity.getInstallmentNumber(), entity.getAmountPaid(), entity.getScheduledAmount(), entity.getDueDate());
    }
}
