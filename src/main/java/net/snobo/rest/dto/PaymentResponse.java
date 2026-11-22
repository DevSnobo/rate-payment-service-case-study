package net.snobo.rest.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import net.snobo.db.entity.Payment;

public record PaymentResponse(String paymentId, BigDecimal amount, LocalDate paidOn, Instant recordedAt,
                              String reference, BigDecimal allocatedAmount, BigDecimal refundedAmount,
                              BigDecimal unallocatedAmount, List<PaymentAllocationResponse> allocations,
                              List<RefundResponse> refunds) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getPaymentId(),
                payment.getAmount(),
                payment.getPaidOn(),
                payment.getRecordedAt(),
                payment.getReference(),
                payment.allocatedAmount(),
                payment.refundedAmount(),
                payment.unallocatedAmount(),
                payment.getAllocations().stream()
                        .sorted(Comparator.comparingInt(allocation ->
                                allocation.getInstallment().getInstallmentNumber()))
                        .map(PaymentAllocationResponse::from)
                        .toList(),
                payment.getRefunds().stream()
                        .map(RefundResponse::from)
                        .toList());
    }
}
