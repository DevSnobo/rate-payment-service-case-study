package net.snobo.db.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import net.snobo.domain.Status;

class PaymentPlanStatusTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Test
    void isActiveBeforeDueDate() {
        PaymentPlan plan = planWith("100.00", installment(1, "100.00", "2026-03-20", "0.00"));

        assertThat(plan.statusAt(clockAt("2026-03-15T10:00:00Z"))).isEqualTo(Status.ACTIVE);
    }

    @Test
    void isActiveOnDueDate() {
        PaymentPlan plan = planWith("100.00", installment(1, "100.00", "2026-03-15", "0.00"));

        assertThat(plan.statusAt(clockAt("2026-03-15T10:00:00Z"))).isEqualTo(Status.ACTIVE);
    }

    @Test
    void isOverdueTheDayAfterDueDate() {
        PaymentPlan plan = planWith("100.00", installment(1, "100.00", "2026-03-15", "0.00"));

        assertThat(plan.statusAt(clockAt("2026-03-16T10:00:00Z"))).isEqualTo(Status.OVERDUE);
    }

    @Test
    void isOverdueWhenPastDueInstallmentIsPartiallyPaid() {
        PaymentPlan plan = planWith("300.00",
                installment(1, "100.00", "2026-03-01", "40.00"),
                installment(2, "200.00", "2026-04-01", "0.00"));

        assertThat(plan.statusAt(clockAt("2026-03-15T10:00:00Z"))).isEqualTo(Status.OVERDUE);
    }

    @Test
    void isCompletedWhenOutstandingBalanceIsZeroEvenAfterDueDate() {
        PaymentPlan plan = planWith("100.00", installment(1, "100.00", "2026-03-01", "100.00"));

        assertThat(plan.outstandingBalance()).isEqualByComparingTo("0.00");
        assertThat(plan.statusAt(clockAt("2026-03-15T10:00:00Z"))).isEqualTo(Status.COMPLETED);
    }

    @Test
    void isCompletedWhenAllInstallmentsArePaidEvenIfPastDue() {
        PaymentPlan plan = planWith("300.00",
                installment(1, "100.00", "2026-03-01", "100.00"),
                installment(2, "200.00", "2026-03-10", "200.00"));

        assertThat(plan.outstandingBalance()).isEqualByComparingTo("0.00");
        assertThat(plan.statusAt(clockAt("2026-04-01T10:00:00Z"))).isEqualTo(Status.COMPLETED);
    }

    @Test
    void rejectsPlanWithoutInstallments() {
        PaymentPlan plan = planWith("100.00");

        assertThatThrownBy(() -> plan.statusAt(clockAt("2026-03-15T10:00:00Z")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsPlanWithoutClaim() {
        PaymentPlan plan = new PaymentPlan();
        plan.addInstallment(Installment.create(1, new BigDecimal("100.00"), LocalDate.of(2026, 3, 20)));

        assertThatThrownBy(() -> plan.statusAt(clockAt("2026-03-15T10:00:00Z")))
                .isInstanceOf(IllegalStateException.class);
    }

    private static Clock clockAt(String instant) {
        return Clock.fixed(Instant.parse(instant), BERLIN);
    }

    private static PaymentPlan planWith(String totalAmount, Installment... installments) {
        Claim claim = Claim.create(4711L, new BigDecimal(totalAmount), "Ada Lovelace", LocalDate.of(2026, 1, 1));
        PaymentPlan plan = claim.createPaymentPlan();
        for (Installment installment : installments) {
            plan.addInstallment(installment);
        }
        return plan;
    }

    private static Installment installment(int number, String scheduledAmount, String dueDate, String amountPaid) {
        Installment installment = Installment.create(number, new BigDecimal(scheduledAmount), LocalDate.parse(dueDate));
        installment.setAmountPaid(new BigDecimal(amountPaid));
        return installment;
    }
}
