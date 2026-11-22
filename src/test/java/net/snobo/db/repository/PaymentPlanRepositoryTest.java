package net.snobo.db.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import net.snobo.db.entity.Claim;
import net.snobo.db.entity.Installment;
import net.snobo.db.entity.PaymentPlan;

@DataJpaTest
@ActiveProfiles("test")
class PaymentPlanRepositoryTest {

    @Autowired
    private PaymentPlanRepository plans;

    @Autowired
    private ClaimRepository claims;

    @Autowired
    private TestEntityManager em;

    @Test
    void findByIdReturnsPlanWithClaimAndOrderedInstallments() {
        Claim claim = Claim.create(4711L, new BigDecimal("300.00"), "Ada Lovelace",
                LocalDate.of(2026, 1, 15));
        PaymentPlan plan = claim.createPaymentPlan();
        plan.addInstallment(Installment.create(1, new BigDecimal("100.00"), LocalDate.of(2026, 2, 1)));
        plan.addInstallment(Installment.create(2, new BigDecimal("100.00"), LocalDate.of(2026, 3, 1)));
        plan.addInstallment(Installment.create(3, new BigDecimal("100.00"), LocalDate.of(2026, 4, 1)));
        claims.saveAndFlush(claim);
        Long planId = plan.getId();
        em.clear();

        PaymentPlan loaded = plans.findById(planId).orElseThrow();

        assertThat(loaded.getClaim().getClaimNumber()).isEqualTo(4711L);
        assertThat(loaded.getInstallments())
                .extracting(Installment::getInstallmentNumber)
                .containsExactly(1, 2, 3);
        assertThat(loaded.outstandingBalance()).isEqualByComparingTo("300.00");
    }

    @Test
    void findByIdReturnsEmptyForUnknownPlan() {
        assertThat(plans.findById(9999L)).isEmpty();
    }
}
