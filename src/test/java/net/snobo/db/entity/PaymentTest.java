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
        assertThatThrownBy(() -> Payment.create(null, PAID_ON, null, RECORDED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("amount is required");
    }

    @Test
    void createRejectsNonPositiveAmount() {
        assertThatThrownBy(() -> Payment.create(new BigDecimal("0.00"), PAID_ON, null, RECORDED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("amount must be positive");
    }

    @Test
    void createRejectsAmountNeedingRounding() {
        assertThatThrownBy(() -> Payment.create(new BigDecimal("80.001"), PAID_ON, null, RECORDED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("amount must have at most 2 decimal places");
    }

    @Test
    void createRejectsNullPaidOn() {
        assertThatThrownBy(() -> Payment.create(new BigDecimal("80.00"), null, null, RECORDED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("paidOn is required");
    }

    @Test
    void createRejectsNullRecordedAt() {
        assertThatThrownBy(() -> Payment.create(new BigDecimal("80.00"), PAID_ON, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("recordedAt is required");
    }

    @Test
    void createNormalizesBlankReferenceToNull() {
        Payment payment = Payment.create(new BigDecimal("80.00"), PAID_ON, "   ", RECORDED_AT);

        assertThat(payment.getReference()).isNull();
    }

    @Test
    void createRejectsTooLongReference() {
        String reference = "x".repeat(256);

        assertThatThrownBy(() -> Payment.create(new BigDecimal("80.00"), PAID_ON, reference, RECORDED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("reference must be at most 255 characters");
    }

    @Test
    void addAllocationTracksAllocatedAndUnallocatedAmounts() {
        Payment payment = Payment.create(new BigDecimal("80.00"), PAID_ON, null, RECORDED_AT);
        Installment installment = Installment.create(1, new BigDecimal("100.00"),
                LocalDate.of(2026, 4, 1));

        payment.addAllocation(installment, new BigDecimal("30.00"));

        assertThat(payment.getAllocations()).hasSize(1);
        assertThat(payment.allocatedAmount()).isEqualByComparingTo("30.00");
        assertThat(payment.unallocatedAmount()).isEqualByComparingTo("50.00");
    }
}
