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
class ClaimRepositoryTest {

    @Autowired
    private ClaimRepository claims;

    @Autowired
    private TestEntityManager em;

    @Test
    void findByClaimNumberReturnsClaim() {
        claims.saveAndFlush(Claim.create(4711L, new BigDecimal("300.00"), "Ada Lovelace",
                LocalDate.of(2026, 1, 15)));
        em.clear();

        Claim claim = claims.findByClaimNumber(4711L).orElseThrow();

        assertThat(claim.getClaimNumber()).isEqualTo(4711L);
        assertThat(claim.getTotalAmount()).isEqualByComparingTo("300.00");
        assertThat(claim.getDebtorName()).isEqualTo("Ada Lovelace");
    }

    @Test
    void findByClaimNumberReturnsEmptyForUnknownNumber() {
        assertThat(claims.findByClaimNumber(9999L)).isEmpty();
    }

    @Test
    void existsByClaimNumberReflectsStoredClaims() {
        claims.saveAndFlush(Claim.create(4711L, new BigDecimal("300.00"), "Ada Lovelace",
                LocalDate.of(2026, 1, 15)));

        assertThat(claims.existsByClaimNumber(4711L)).isTrue();
        assertThat(claims.existsByClaimNumber(9999L)).isFalse();
    }

    @Test
    void findByClaimNumberForUpdateReturnsClaimWithPlan() {
        Claim claim = Claim.create(4711L, new BigDecimal("300.00"), "Ada Lovelace",
                LocalDate.of(2026, 1, 15));
        PaymentPlan plan = claim.createPaymentPlan();
        plan.addInstallment(Installment.create(1, new BigDecimal("100.00"), LocalDate.of(2026, 2, 1)));
        plan.addInstallment(Installment.create(2, new BigDecimal("200.00"), LocalDate.of(2026, 3, 1)));
        claims.saveAndFlush(claim);
        em.clear();

        Claim locked = claims.findByClaimNumberForUpdate(4711L).orElseThrow();

        assertThat(locked.getClaimNumber()).isEqualTo(4711L);
        assertThat(locked.getPaymentPlan()).isNotNull();
        assertThat(locked.getPaymentPlan().getInstallments()).hasSize(2);
    }

    @Test
    void findByClaimNumberFetchesPlanAndInstallments() {
        Claim claim = Claim.create(4711L, new BigDecimal("300.00"), "Ada Lovelace",
                LocalDate.of(2026, 1, 15));
        PaymentPlan plan = claim.createPaymentPlan();
        plan.addInstallment(Installment.create(1, new BigDecimal("100.00"), LocalDate.of(2026, 2, 1)));
        plan.addInstallment(Installment.create(2, new BigDecimal("200.00"), LocalDate.of(2026, 3, 1)));
        claims.saveAndFlush(claim);
        em.clear();

        Claim loaded = claims.findByClaimNumber(4711L).orElseThrow();

        assertThat(loaded.getPaymentPlan()).isNotNull();
        assertThat(loaded.getPaymentPlan().getClaim().getClaimNumber()).isEqualTo(4711L);
        assertThat(loaded.getPaymentPlan().getInstallments())
                .extracting(Installment::getInstallmentNumber)
                .containsExactly(1, 2);
    }
}
