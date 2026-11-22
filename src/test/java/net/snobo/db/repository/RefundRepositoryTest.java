package net.snobo.db.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import net.snobo.db.entity.Claim;
import net.snobo.db.entity.Installment;
import net.snobo.db.entity.Payment;
import net.snobo.db.entity.PaymentPlan;
import net.snobo.db.entity.Refund;

@DataJpaTest
@ActiveProfiles("test")
class RefundRepositoryTest {

    private static final Instant RECORDED_AT = Instant.parse("2026-03-20T10:00:00Z");

    @Autowired
    private RefundRepository refunds;

    @Autowired
    private ClaimRepository claims;

    @Autowired
    private TestEntityManager em;

    @Test
    void savesRefundAndReducesCredit() {
        Claim claim = claimWithPayment("150.00");
        Payment payment = claim.getPaymentPlan().getPayments().get(0);
        Refund refund = Refund.create(new BigDecimal("20.00"), LocalDate.of(2026, 3, 20),
                "refund-1", RECORDED_AT);
        payment.addRefund(refund);
        claims.saveAndFlush(claim);
        em.clear();

        Refund loaded = refunds.findByRefundId(refund.getRefundId()).orElseThrow();

        assertThat(loaded.getAmount()).isEqualByComparingTo("20.00");
        assertThat(loaded.getReference()).isEqualTo("refund-1");
        assertThat(loaded.getPayment().getPaymentId()).isEqualTo(payment.getPaymentId());

        Claim reloaded = claims.findByClaimNumber(4716L).orElseThrow();
        assertThat(reloaded.getPaymentPlan().creditBalance()).isEqualByComparingTo("30.00");
        assertThat(reloaded.getPaymentPlan().refundedAmount()).isEqualByComparingTo("20.00");
        assertThat(reloaded.getPaymentPlan().getPayments().get(0).unallocatedAmount())
                .isEqualByComparingTo("30.00");
    }

    @Test
    void findsRefundsForPlanInRefundedOnOrder() {
        Claim claim = claimWithPayment("150.00");
        Payment payment = claim.getPaymentPlan().getPayments().get(0);
        Refund later = Refund.create(new BigDecimal("10.00"), LocalDate.of(2026, 3, 25),
                null, RECORDED_AT);
        Refund earlier = Refund.create(new BigDecimal("10.00"), LocalDate.of(2026, 3, 20),
                null, RECORDED_AT);
        payment.addRefund(later);
        payment.addRefund(earlier);
        claims.saveAndFlush(claim);
        Long planId = claim.getPaymentPlan().getId();
        em.clear();

        List<Refund> loaded = refunds.findByPaymentPaymentPlanIdOrderByRefundedOnAscIdAsc(planId);

        assertThat(loaded).extracting(Refund::getRefundedOn)
                .containsExactly(LocalDate.of(2026, 3, 20), LocalDate.of(2026, 3, 25));
    }

    @Test
    void rejectsDuplicateReferenceForSamePayment() {
        Claim claim = claimWithPayment("150.00");
        Payment payment = claim.getPaymentPlan().getPayments().get(0);
        Refund first = Refund.create(new BigDecimal("10.00"), LocalDate.of(2026, 3, 20),
                "same-refund", RECORDED_AT);
        payment.addRefund(first);
        claims.saveAndFlush(claim);

        Refund duplicate = Refund.create(new BigDecimal("5.00"), LocalDate.of(2026, 3, 21),
                "same-refund", RECORDED_AT);
        payment.addRefund(duplicate);

        assertThatThrownBy(() -> refunds.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Claim claimWithPayment(String paymentAmount) {
        Claim claim = Claim.create(4716L, new BigDecimal("100.00"), "Ada Lovelace",
                LocalDate.of(2026, 1, 15));
        PaymentPlan plan = claim.createPaymentPlan();
        plan.addInstallment(Installment.create(1, new BigDecimal("100.00"),
                LocalDate.of(2026, 4, 1)));
        plan.validateAgainstClaim();
        Payment payment = Payment.create(new BigDecimal(paymentAmount), LocalDate.of(2026, 3, 14),
                null, RECORDED_AT);
        plan.addPayment(payment);
        plan.allocate(payment);
        return claim;
    }
}
