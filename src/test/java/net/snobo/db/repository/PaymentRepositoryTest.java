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

@DataJpaTest
@ActiveProfiles("test")
class PaymentRepositoryTest {

    @Autowired
    private PaymentRepository payments;

    @Autowired
    private ClaimRepository claims;

    @Autowired
    private TestEntityManager em;

    @Test
    void savesPaymentWithAllocationsAndLoadsItByPublicId() {
        Claim claim = claimWithPlan();
        PaymentPlan plan = claim.getPaymentPlan();
        Payment payment = Payment.create(new BigDecimal("150.00"), LocalDate.of(2026, 3, 1),
                "bank-1", Instant.parse("2026-03-15T10:00:00Z"));
        plan.addPayment(payment);
        plan.allocate(payment);
        claims.saveAndFlush(claim);
        em.clear();

        Payment loaded = payments.findByPaymentId(payment.getPaymentId()).orElseThrow();

        assertThat(loaded.getAmount()).isEqualByComparingTo("150.00");
        assertThat(loaded.getReference()).isEqualTo("bank-1");
        assertThat(loaded.getAllocations()).hasSize(2);
        assertThat(loaded.allocatedAmount()).isEqualByComparingTo("150.00");
        assertThat(loaded.unallocatedAmount()).isEqualByComparingTo("0.00");
        assertThat(loaded.getPaymentPlan().getClaim().getClaimNumber()).isEqualTo(4711L);
    }

    @Test
    void findsPaymentsForPlanInPaidOnOrder() {
        Claim claim = claimWithPlan();
        PaymentPlan plan = claim.getPaymentPlan();
        Payment later = Payment.create(new BigDecimal("50.00"), LocalDate.of(2026, 3, 10),
                null, Instant.parse("2026-03-15T10:00:00Z"));
        Payment earlier = Payment.create(new BigDecimal("50.00"), LocalDate.of(2026, 3, 1),
                null, Instant.parse("2026-03-15T10:00:00Z"));
        plan.addPayment(later);
        plan.allocate(later);
        plan.addPayment(earlier);
        plan.allocate(earlier);
        claims.saveAndFlush(claim);
        Long planId = plan.getId();
        em.clear();

        List<Payment> loaded = payments.findByPaymentPlanIdOrderByPaidOnAscIdAsc(planId);

        assertThat(loaded).extracting(Payment::getPaidOn)
                .containsExactly(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 10));
    }

    @Test
    void rejectsDuplicateReferenceForSamePlan() {
        Claim claim = claimWithPlan();
        PaymentPlan plan = claim.getPaymentPlan();
        Payment first = Payment.create(new BigDecimal("50.00"), LocalDate.of(2026, 3, 1),
                "same-reference", Instant.parse("2026-03-15T10:00:00Z"));
        plan.addPayment(first);
        plan.allocate(first);
        claims.saveAndFlush(claim);

        Payment duplicate = Payment.create(new BigDecimal("60.00"), LocalDate.of(2026, 3, 2),
                "same-reference", Instant.parse("2026-03-15T11:00:00Z"));
        plan.addPayment(duplicate);

        assertThatThrownBy(() -> payments.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void readsPlanCreditFromPersistedPayments() {
        Claim claim = claimWithPlan();
        PaymentPlan plan = claim.getPaymentPlan();
        Payment overpayment = Payment.create(new BigDecimal("500.00"), LocalDate.of(2026, 3, 1),
                null, Instant.parse("2026-03-15T10:00:00Z"));
        plan.addPayment(overpayment);
        plan.allocate(overpayment);
        claims.saveAndFlush(claim);
        em.clear();

        Claim loaded = claims.findByClaimNumber(4711L).orElseThrow();

        assertThat(loaded.getPaymentPlan().amountPaid()).isEqualByComparingTo("300.00");
        assertThat(loaded.getPaymentPlan().outstandingBalance()).isEqualByComparingTo("0.00");
        assertThat(loaded.getPaymentPlan().creditBalance()).isEqualByComparingTo("200.00");
    }

    private Claim claimWithPlan() {
        Claim claim = Claim.create(4711L, new BigDecimal("300.00"), "Ada Lovelace",
                LocalDate.of(2026, 1, 15));
        PaymentPlan plan = claim.createPaymentPlan();
        plan.addInstallment(Installment.create(1, new BigDecimal("100.00"),
                LocalDate.of(2026, 2, 1)));
        plan.addInstallment(Installment.create(2, new BigDecimal("200.00"),
                LocalDate.of(2026, 3, 1)));
        plan.validateAgainstClaim();
        return claim;
    }
}
