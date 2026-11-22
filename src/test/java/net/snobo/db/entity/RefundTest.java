package net.snobo.db.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class RefundTest {

    private static final Instant RECORDED_AT = Instant.parse("2026-03-20T10:00:00Z");
    private static final LocalDate REFUNDED_ON = LocalDate.of(2026, 3, 20);

    @Test
    void createBuildsRefund() {
        Refund refund = Refund.create(new BigDecimal("50.00"), REFUNDED_ON, "  refund-1  ", RECORDED_AT);

        assertThat(refund.getRefundId()).isNotBlank().hasSize(36);
        assertThat(refund.getAmount()).isEqualByComparingTo("50.00");
        assertThat(refund.getAmount().scale()).isEqualTo(2);
        assertThat(refund.getRefundedOn()).isEqualTo(REFUNDED_ON);
        assertThat(refund.getRecordedAt()).isEqualTo(RECORDED_AT);
        assertThat(refund.getReference()).isEqualTo("refund-1");
    }

    @Test
    void createRejectsNullAmount() {
        assertThatThrownBy(() -> Refund.create(null, REFUNDED_ON, null, RECORDED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("amount is required");
    }

    @Test
    void createRejectsNonPositiveAmount() {
        assertThatThrownBy(() -> Refund.create(new BigDecimal("0.00"), REFUNDED_ON, null, RECORDED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("amount must be positive");
    }

    @Test
    void createRejectsAmountNeedingRounding() {
        assertThatThrownBy(() -> Refund.create(new BigDecimal("50.001"), REFUNDED_ON, null, RECORDED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("amount must have at most 2 decimal places");
    }

    @Test
    void createRejectsNullRefundedOn() {
        assertThatThrownBy(() -> Refund.create(new BigDecimal("50.00"), null, null, RECORDED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("refundedOn is required");
    }

    @Test
    void createRejectsNullRecordedAt() {
        assertThatThrownBy(() -> Refund.create(new BigDecimal("50.00"), REFUNDED_ON, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("recordedAt is required");
    }

    @Test
    void createNormalizesBlankReferenceToNull() {
        Refund refund = Refund.create(new BigDecimal("50.00"), REFUNDED_ON, "   ", RECORDED_AT);

        assertThat(refund.getReference()).isNull();
    }

    @Test
    void createRejectsTooLongReference() {
        String reference = "x".repeat(256);

        assertThatThrownBy(() -> Refund.create(new BigDecimal("50.00"), REFUNDED_ON, reference, RECORDED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("reference must be at most 255 characters");
    }
}
