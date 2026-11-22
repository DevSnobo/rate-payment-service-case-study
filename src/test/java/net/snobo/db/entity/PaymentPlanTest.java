package net.snobo.db.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class PaymentPlanTest {

    @Test
    void calculatesAmountPaidAndOutstandingBalance() {
        PaymentPlan plan = planWithTotal("300.00",
                installment(1, "100.00", "2026-03-01", "40.00"),
                installment(2, "200.00", "2026-04-01", "0.00"));

        assertThat(plan.amountPaid()).isEqualByComparingTo("40.00");
        assertThat(plan.amountPaid().scale()).isEqualTo(2);
        assertThat(plan.outstandingBalance()).isEqualByComparingTo("260.00");
        assertThat(plan.outstandingBalance().scale()).isEqualTo(2);
    }

    @Test
    void validateAgainstClaimAcceptsMatchingInstallments() {
        PaymentPlan plan = planWithTotal("300.00",
                installment(1, "100.00", "2026-03-01", "0.00"),
                installment(2, "200.00", "2026-04-01", "0.00"));

        plan.validateAgainstClaim();

        assertThat(plan.getInstallments()).hasSize(2);
    }

    @Test
    void validateAgainstClaimRejectsEmptyPlan() {
        PaymentPlan plan = planWithTotal("300.00");

        assertThatThrownBy(plan::validateAgainstClaim)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("payment plan must have at least one installment");
    }

    @Test
    void validateAgainstClaimRejectsWrongTotal() {
        PaymentPlan plan = planWithTotal("300.00",
                installment(1, "100.00", "2026-03-01", "0.00"));

        assertThatThrownBy(plan::validateAgainstClaim)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not match claim total");
    }

    @Test
    void validateAgainstClaimRejectsDuplicateInstallmentNumbers() {
        PaymentPlan plan = planWithTotal("200.00",
                installment(1, "100.00", "2026-03-01", "0.00"),
                installment(1, "100.00", "2026-04-01", "0.00"));

        assertThatThrownBy(plan::validateAgainstClaim)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("duplicate installment number 1");
    }

    @Test
    void addInstallmentRejectsNull() {
        PaymentPlan plan = planWithTotal("300.00");

        assertThatThrownBy(() -> plan.addInstallment(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("installment is required");
    }

    private static PaymentPlan planWithTotal(String total, Installment... installments) {
        Claim claim = Claim.create(4711L, new BigDecimal(total), "Ada Lovelace", LocalDate.of(2026, 1, 1));
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
