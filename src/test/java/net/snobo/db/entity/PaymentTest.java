package net.snobo.db.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class PaymentTest {

    private static final Instant RECORDED_AT = Instant.parse("2026-03-15T10:00:00Z");
    private static final LocalDate PAID_ON = LocalDate.of(2026, 3, 14);
    private static final LocalDate REFUNDED_ON = LocalDate.of(2026, 3, 20);

    @Test
    void createBuildsPayment() {
        Payment payment = Payment.create(new BigDecimal("80.00"), PAID_ON, "  bank-1  ", RECORDED_AT);

        assertThat(payment.getPaymentId()).isNotBlank().hasSize(36);
        assertThat(payment.getAmount()).isEqualByComparingTo("80.00");
        assertThat(payment.getAmount().scale()).isEqualTo(2);
        assertThat(payment.getPaidOn()).isEqualTo(PAID_ON);
        assertThat(payment.getRecordedAt()).isEqualTo(RECORDED_AT);
        assertThat(payment.getReference()).isEqualTo("bank-1");
        assertThat(payment.allocatedAmount()).isEqualByComparingTo("0.00");
        assertThat(payment.unallocatedAmount()).isEqualByComparingTo("80.00");
    }

    @Test
    void createRejectsNullAmount() {
        assertThatThrownBy(() -> Payment.create(null, PAID_ON, null, RECORDED_AT)).isInstanceOf(
                IllegalArgumentException.class).hasMessage("amount is required");
    }

    @Test
    void createRejectsNonPositiveAmount() {
        BigDecimal amount = new BigDecimal("0.00");
        assertThatThrownBy(() -> Payment.create(amount, PAID_ON, null, RECORDED_AT)).isInstanceOf(
                IllegalArgumentException.class).hasMessage("amount must be positive");
    }

    @Test
    void createRejectsAmountNeedingRounding() {
        BigDecimal amount = new BigDecimal("80.001");
        assertThatThrownBy(() -> Payment.create(amount, PAID_ON, null, RECORDED_AT)).isInstanceOf(
                IllegalArgumentException.class).hasMessage("amount must have at most 2 decimal places");
    }

    @Test
    void createRejectsNullPaidOn() {
        BigDecimal amount = new BigDecimal("80.00");
        assertThatThrownBy(() -> Payment.create(amount, null, null, RECORDED_AT)).isInstanceOf(
                IllegalArgumentException.class).hasMessage("paidOn is required");
    }

    @Test
    void createRejectsNullRecordedAt() {
        BigDecimal amount = new BigDecimal("80.00");
        assertThatThrownBy(() -> Payment.create(amount, PAID_ON, null, null)).isInstanceOf(
                IllegalArgumentException.class).hasMessage("recordedAt is required");
    }

    @Test
    void createNormalizesBlankReferenceToNull() {
        Payment payment = Payment.create(new BigDecimal("80.00"), PAID_ON, "   ", RECORDED_AT);

        assertThat(payment.getReference()).isNull();
    }

    @Test
    void createRejectsTooLongReference() {
        String reference = "x".repeat(256);

        BigDecimal amount = new BigDecimal("80.00");
        assertThatThrownBy(() -> Payment.create(amount, PAID_ON, reference, RECORDED_AT)).isInstanceOf(
                IllegalArgumentException.class).hasMessage("reference must be at most 255 characters");
    }

    @Test
    void addAllocationTracksAllocatedAndUnallocatedAmounts() {
        Payment payment = Payment.create(new BigDecimal("80.00"), PAID_ON, null, RECORDED_AT);
        Installment installment = Installment.create(1, new BigDecimal("100.00"), LocalDate.of(2026, 4, 1));

        payment.addAllocation(installment, new BigDecimal("30.00"));

        assertThat(payment.getAllocations()).hasSize(1);
        assertThat(payment.allocatedAmount()).isEqualByComparingTo("30.00");
        assertThat(payment.unallocatedAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    void addRefundReducesUnallocatedAmount() {
        Payment payment = Payment.create(new BigDecimal("80.00"), PAID_ON, null, RECORDED_AT);
        Refund refund = Refund.create(new BigDecimal("30.00"), REFUNDED_ON, null, RECORDED_AT);

        payment.addRefund(refund);

        assertThat(payment.getRefunds()).hasSize(1);
        assertThat(payment.refundedAmount()).isEqualByComparingTo("30.00");
        assertThat(payment.unallocatedAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    void addMultipleRefundsUntilCreditIsExhausted() {
        Payment payment = Payment.create(new BigDecimal("80.00"), PAID_ON, null, RECORDED_AT);

        payment.addRefund(Refund.create(new BigDecimal("30.00"), REFUNDED_ON, null, RECORDED_AT));
        payment.addRefund(Refund.create(new BigDecimal("50.00"), REFUNDED_ON, null, RECORDED_AT));

        assertThat(payment.getRefunds()).hasSize(2);
        assertThat(payment.refundedAmount()).isEqualByComparingTo("80.00");
        assertThat(payment.unallocatedAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void addRefundRejectsMoreThanAvailableCredit() {
        Payment payment = Payment.create(new BigDecimal("80.00"), PAID_ON, null, RECORDED_AT);
        Refund refund = Refund.create(new BigDecimal("80.01"), REFUNDED_ON, null, RECORDED_AT);

        assertThatThrownBy(() -> payment.addRefund(refund)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("refund exceeds available credit");
    }

    @Test
    void allocationsAndRefundsShareTheSameCredit() {
        Payment payment = Payment.create(new BigDecimal("100.00"), PAID_ON, null, RECORDED_AT);
        Installment installment = Installment.create(1, new BigDecimal("100.00"), LocalDate.of(2026, 4, 1));
        payment.addAllocation(installment, new BigDecimal("70.00"));

        payment.addRefund(Refund.create(new BigDecimal("20.00"), REFUNDED_ON, null, RECORDED_AT));

        assertThat(payment.allocatedAmount()).isEqualByComparingTo("70.00");
        assertThat(payment.refundedAmount()).isEqualByComparingTo("20.00");
        assertThat(payment.unallocatedAmount()).isEqualByComparingTo("10.00");
    }
}
