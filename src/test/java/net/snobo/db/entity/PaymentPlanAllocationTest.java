package net.snobo.db.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class PaymentPlanAllocationTest {

    private static final Instant RECORDED_AT = Instant.parse("2026-03-15T10:00:00Z");
    private static final LocalDate PAID_ON = LocalDate.of(2026, 3, 14);
    private static final LocalDate REFUNDED_ON = LocalDate.of(2026, 3, 20);

    @Test
    void allocatesOldestDueDateFirst() {
        PaymentPlan plan = planWithTotal("200.00",
                Installment.create(2, new BigDecimal("100.00"), LocalDate.of(2026, 2, 1)),
                Installment.create(1, new BigDecimal("100.00"), LocalDate.of(2026, 3, 1)));

        Payment payment = pay(plan, "100.00");

        assertThat(installment(plan, 2).getAmountPaid()).isEqualByComparingTo("100.00");
        assertThat(installment(plan, 1).getAmountPaid()).isEqualByComparingTo("0.00");
        assertThat(payment.allocatedAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void usesInstallmentNumberWhenDueDatesMatch() {
        PaymentPlan plan = planWithTotal("200.00",
                Installment.create(2, new BigDecimal("100.00"), LocalDate.of(2026, 2, 1)),
                Installment.create(1, new BigDecimal("100.00"), LocalDate.of(2026, 2, 1)));

        pay(plan, "100.00");

        assertThat(installment(plan, 1).getAmountPaid()).isEqualByComparingTo("100.00");
        assertThat(installment(plan, 2).getAmountPaid()).isEqualByComparingTo("0.00");
    }

    @Test
    void supportsPartialPayment() {
        PaymentPlan plan = planWithTotal("100.00",
                Installment.create(1, new BigDecimal("100.00"), LocalDate.of(2026, 2, 1)));

        Payment payment = pay(plan, "40.00");

        assertThat(installment(plan, 1).getAmountPaid()).isEqualByComparingTo("40.00");
        assertThat(installment(plan, 1).outstandingAmount()).isEqualByComparingTo("60.00");
        assertThat(plan.amountPaid()).isEqualByComparingTo("40.00");
        assertThat(plan.outstandingBalance()).isEqualByComparingTo("60.00");
        assertThat(payment.unallocatedAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void spreadsPaymentAcrossSeveralInstallments() {
        PaymentPlan plan = planWithTotal("300.00",
                Installment.create(1, new BigDecimal("100.00"), LocalDate.of(2026, 2, 1)),
                Installment.create(2, new BigDecimal("200.00"), LocalDate.of(2026, 3, 1)));

        Payment payment = pay(plan, "150.00");

        assertThat(installment(plan, 1).getAmountPaid()).isEqualByComparingTo("100.00");
        assertThat(installment(plan, 2).getAmountPaid()).isEqualByComparingTo("50.00");
        assertThat(plan.amountPaid()).isEqualByComparingTo("150.00");
        assertThat(plan.outstandingBalance()).isEqualByComparingTo("150.00");
        assertThat(payment.getAllocations()).hasSize(2);
        assertThat(payment.allocatedAmount()).isEqualByComparingTo("150.00");
    }

    @Test
    void recordsOverpaymentAsCredit() {
        PaymentPlan plan = planWithTotal("300.00",
                Installment.create(1, new BigDecimal("100.00"), LocalDate.of(2026, 2, 1)),
                Installment.create(2, new BigDecimal("200.00"), LocalDate.of(2026, 3, 1)));

        Payment payment = pay(plan, "350.00");

        assertThat(installment(plan, 1).getAmountPaid()).isEqualByComparingTo("100.00");
        assertThat(installment(plan, 2).getAmountPaid()).isEqualByComparingTo("200.00");
        assertThat(plan.amountPaid()).isEqualByComparingTo("300.00");
        assertThat(plan.outstandingBalance()).isEqualByComparingTo("0.00");
        assertThat(plan.creditBalance()).isEqualByComparingTo("50.00");
        assertThat(payment.allocatedAmount()).isEqualByComparingTo("300.00");
        assertThat(payment.unallocatedAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    void paymentOnCompletedPlanBecomesCredit() {
        PaymentPlan plan = planWithTotal("100.00",
                TestInstallments.paid(1, new BigDecimal("100.00"), new BigDecimal("100.00"),
                        LocalDate.of(2026, 2, 1)));

        Payment payment = pay(plan, "50.00");

        assertThat(payment.getAllocations()).isEmpty();
        assertThat(payment.unallocatedAmount()).isEqualByComparingTo("50.00");
        assertThat(plan.amountPaid()).isEqualByComparingTo("100.00");
        assertThat(plan.outstandingBalance()).isEqualByComparingTo("0.00");
        assertThat(plan.creditBalance()).isEqualByComparingTo("50.00");
    }

    @Test
    void creditBalanceSumsAllPayments() {
        PaymentPlan plan = planWithTotal("100.00",
                Installment.create(1, new BigDecimal("100.00"), LocalDate.of(2026, 2, 1)));

        pay(plan, "120.00");
        pay(plan, "30.00");

        assertThat(plan.amountPaid()).isEqualByComparingTo("100.00");
        assertThat(plan.outstandingBalance()).isEqualByComparingTo("0.00");
        assertThat(plan.creditBalance()).isEqualByComparingTo("50.00");
    }

    @Test
    void allocateRejectsPaymentThatIsNotAttached() {
        PaymentPlan plan = planWithTotal("100.00",
                Installment.create(1, new BigDecimal("100.00"), LocalDate.of(2026, 2, 1)));
        Payment payment = Payment.create(new BigDecimal("80.00"), PAID_ON, null, RECORDED_AT);

        assertThatThrownBy(() -> plan.allocate(payment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("payment is not attached to this plan");
    }

    @Test
    void refundsReducePlanCreditAndIncreaseRefundedAmount() {
        PaymentPlan plan = planWithTotal("100.00",
                Installment.create(1, new BigDecimal("100.00"), LocalDate.of(2026, 2, 1)));
        Payment payment = pay(plan, "150.00");

        payment.addRefund(Refund.create(new BigDecimal("20.00"), REFUNDED_ON, null, RECORDED_AT));

        assertThat(plan.amountPaid()).isEqualByComparingTo("100.00");
        assertThat(plan.outstandingBalance()).isEqualByComparingTo("0.00");
        assertThat(plan.creditBalance()).isEqualByComparingTo("30.00");
        assertThat(plan.refundedAmount()).isEqualByComparingTo("20.00");
    }

    @Test
    void planRefundedAmountSumsPayments() {
        PaymentPlan plan = planWithTotal("100.00",
                Installment.create(1, new BigDecimal("100.00"), LocalDate.of(2026, 2, 1)));
        Payment first = pay(plan, "120.00");
        Payment second = pay(plan, "30.00");

        first.addRefund(Refund.create(new BigDecimal("10.00"), REFUNDED_ON, null, RECORDED_AT));
        second.addRefund(Refund.create(new BigDecimal("5.00"), REFUNDED_ON, null, RECORDED_AT));

        assertThat(plan.refundedAmount()).isEqualByComparingTo("15.00");
        assertThat(plan.creditBalance()).isEqualByComparingTo("35.00");
    }

    private static PaymentPlan planWithTotal(String total, Installment... installments) {
        Claim claim = Claim.create(4711L, new BigDecimal(total), "Ada Lovelace",
                LocalDate.of(2026, 1, 1));
        PaymentPlan plan = claim.createPaymentPlan();
        for (Installment installment : installments) {
            plan.addInstallment(installment);
        }
        plan.validateAgainstClaim();
        return plan;
    }

    private static Payment pay(PaymentPlan plan, String amount) {
        Payment payment = Payment.create(new BigDecimal(amount), PAID_ON, null, RECORDED_AT);
        plan.addPayment(payment);
        plan.allocate(payment);
        return payment;
    }

    private static Installment installment(PaymentPlan plan, int number) {
        return plan.getInstallments().stream()
                .filter(installment -> installment.getInstallmentNumber() == number)
                .findFirst()
                .orElseThrow();
    }
}
