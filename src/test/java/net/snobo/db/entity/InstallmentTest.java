package net.snobo.db.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class InstallmentTest {

    private static final LocalDate DUE_DATE = LocalDate.of(2026, 3, 1);

    @Test
    void createInitializesPaidAmountToZero() {
        Installment installment = Installment.create(1, new BigDecimal("100.00"), DUE_DATE);

        assertThat(installment.getInstallmentNumber()).isEqualTo(1);
        assertThat(installment.getScheduledAmount()).isEqualByComparingTo("100.00");
        assertThat(installment.getAmountPaid()).isEqualByComparingTo("0.00");
        assertThat(installment.getAmountPaid().scale()).isEqualTo(2);
        assertThat(installment.getDueDate()).isEqualTo(DUE_DATE);
    }

    @Test
    void createRejectsNonPositiveInstallmentNumber() {
        assertThatThrownBy(() -> Installment.create(0, new BigDecimal("100.00"), DUE_DATE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("installmentNumber must be positive");
    }

    @Test
    void createRejectsNonPositiveScheduledAmount() {
        assertThatThrownBy(() -> Installment.create(1, new BigDecimal("0.00"), DUE_DATE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("scheduledAmount must be positive");
    }

    @Test
    void createRejectsNullDueDate() {
        assertThatThrownBy(() -> Installment.create(1, new BigDecimal("100.00"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("dueDate is required");
    }

    @Test
    void outstandingAmountSubtractsPaidFromScheduled() {
        Installment installment = Installment.create(1, new BigDecimal("100.00"), DUE_DATE);
        installment.setAmountPaid(new BigDecimal("40.00"));

        assertThat(installment.outstandingAmount()).isEqualByComparingTo("60.00");
    }

    @Test
    void setAmountPaidRejectsNegativeAmount() {
        Installment installment = Installment.create(1, new BigDecimal("100.00"), DUE_DATE);

        assertThatThrownBy(() -> installment.setAmountPaid(new BigDecimal("-1.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("amountPaid must not be negative");
    }

    @Test
    void setAmountPaidRejectsOverpayment() {
        Installment installment = Installment.create(1, new BigDecimal("100.00"), DUE_DATE);

        assertThatThrownBy(() -> installment.setAmountPaid(new BigDecimal("100.01")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("amountPaid must not exceed scheduledAmount");
    }
}
