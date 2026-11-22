package net.snobo.rest.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import net.snobo.db.entity.Refund;

public record RefundResponse(String refundId, String paymentId, BigDecimal amount, LocalDate refundedOn,
                             Instant recordedAt, String reference) {

    public static RefundResponse from(Refund refund) {
        return new RefundResponse(
                refund.getRefundId(),
                refund.getPayment().getPaymentId(),
                refund.getAmount(),
                refund.getRefundedOn(),
                refund.getRecordedAt(),
                refund.getReference());
    }
}
