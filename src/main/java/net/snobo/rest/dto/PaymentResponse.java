package net.snobo.rest.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import net.snobo.db.entity.Payment;

public record PaymentResponse(String paymentId, BigDecimal amount, LocalDate paidOn, Instant recordedAt,
                              String reference, BigDecimal allocatedAmount, BigDecimal unallocatedAmount,
                              List<PaymentAllocationResponse> allocations) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getPaymentId(),
                payment.getAmount(),
                payment.getPaidOn(),
                payment.getRecordedAt(),
                payment.getReference(),
                payment.allocatedAmount(),
                payment.unallocatedAmount(),
                payment.getAllocations().stream()
                        .sorted(Comparator.comparingInt(allocation ->
                                allocation.getInstallment().getInstallmentNumber()))
                        .map(PaymentAllocationResponse::from)
                        .toList());
    }
}
