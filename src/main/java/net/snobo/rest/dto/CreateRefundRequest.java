package net.snobo.rest.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateRefundRequest(BigDecimal amount, LocalDate refundedOn, String reference) {
}
