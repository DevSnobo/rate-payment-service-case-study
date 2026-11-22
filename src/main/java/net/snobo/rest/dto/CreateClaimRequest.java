package net.snobo.rest.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateClaimRequest(Long claimNumber, BigDecimal totalAmount, String debtorName, LocalDate createdOn) {
}
