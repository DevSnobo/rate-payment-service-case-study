package net.snobo.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ScheduledInstallment(int installmentNumber, BigDecimal amount, LocalDate dueDate) {
}
