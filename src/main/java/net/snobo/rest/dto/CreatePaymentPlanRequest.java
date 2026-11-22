package net.snobo.rest.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreatePaymentPlanRequest(Integer installmentCount, BigDecimal installmentAmount,
                                       LocalDate firstDueDate) {
}
