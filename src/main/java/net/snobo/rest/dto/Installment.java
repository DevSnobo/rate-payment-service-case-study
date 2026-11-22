package net.snobo.rest.dto;

import java.time.LocalDateTime;

public record Installment(Long installmentNumber, Double amountPaid, LocalDateTime dueDate) {

    public Installment(net.snobo.db.entity.Installment entity) {
        this(entity.getInstallmentNumber(), entity.getAmountPaid(), entity.getDueDate());
    }

}
