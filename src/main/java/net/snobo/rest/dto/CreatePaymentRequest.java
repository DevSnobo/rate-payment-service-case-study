package net.snobo.rest.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreatePaymentRequest(BigDecimal amount, LocalDate paidOn, String reference) {
}
