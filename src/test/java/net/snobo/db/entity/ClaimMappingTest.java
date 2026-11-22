package net.snobo.db.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import net.snobo.db.repository.ClaimRepository;

@DataJpaTest
@ActiveProfiles("test")
class ClaimMappingTest {

    @Autowired
    private ClaimRepository claims;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void persistsClaimWithPaymentPlanAndInstallments() {
        Claim claim = claimWithThreeInstallments();
        claims.save(claim);
        entityManager.flush();
        entityManager.clear();

        Claim reloaded = claims.findByClaimNumber(4711L).orElseThrow();

        assertThat(reloaded.getClaimNumber()).isEqualTo(4711L);
        assertThat(reloaded.getTotalAmount()).isEqualByComparingTo("300.00");
        assertThat(reloaded.getTotalAmount().scale()).isEqualTo(2);
        assertThat(reloaded.getDebtorName()).isEqualTo("Ada Lovelace");
        assertThat(reloaded.getCreatedOn()).isEqualTo(LocalDate.of(2026, 1, 15));

        PaymentPlan plan = reloaded.getPaymentPlan();
        assertThat(plan).isNotNull();
        assertThat(plan.getClaim()).isNotNull();
        assertThat(plan.getClaim().getClaimNumber()).isEqualTo(4711L);

        List<Installment> installments = plan.getInstallments();
        assertThat(installments).hasSize(3);
        assertThat(installments).extracting(Installment::getInstallmentNumber).containsExactly(1, 2, 3);
        assertThat(installments).allSatisfy(installment -> {
            assertThat(installment.getScheduledAmount()).isEqualByComparingTo("100.00");
            assertThat(installment.getScheduledAmount().scale()).isEqualTo(2);
            assertThat(installment.getAmountPaid()).isEqualByComparingTo("0.00");
            assertThat(installment.getAmountPaid().scale()).isEqualTo(2);
        });
        assertThat(plan.outstandingBalance()).isEqualByComparingTo("300.00");
    }

    @Test
    void rejectsDuplicateClaimNumber() {
        claims.saveAndFlush(Claim.create(4711L, new BigDecimal("100.00"), "Ada", LocalDate.of(2026, 1, 1)));

        Claim duplicate = Claim.create(4711L, new BigDecimal("200.00"), "Grace", LocalDate.of(2026, 1, 2));

        assertThatThrownBy(() -> claims.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateInstallmentNumberOnSamePlan() {
        Claim claim = Claim.create(4712L, new BigDecimal("200.00"), "Ada", LocalDate.of(2026, 1, 1));
        PaymentPlan plan = claim.createPaymentPlan();
        plan.addInstallment(Installment.create(1, new BigDecimal("100.00"), LocalDate.of(2026, 2, 1)));
        plan.addInstallment(Installment.create(1, new BigDecimal("100.00"), LocalDate.of(2026, 3, 1)));

        assertThatThrownBy(() -> claims.saveAndFlush(claim))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsNegativeTotalAtDatabaseLevel() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into claims (claim_number, total_amount, debtor_name, created_on) values (?, ?, ?, ?)",
                9001L, new BigDecimal("-1.00"), "Ada", Date.valueOf(LocalDate.of(2026, 1, 1))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsNullRequiredColumnAtDatabaseLevel() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into claims (claim_number, total_amount, debtor_name, created_on) values (?, ?, ?, ?)",
                9002L, new BigDecimal("10.00"), (Object) null, Date.valueOf(LocalDate.of(2026, 1, 1))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByClaimNumberReturnsEmptyForUnknownNumber() {
        assertThat(claims.findByClaimNumber(9_999L)).isEmpty();
    }

    private static Claim claimWithThreeInstallments() {
        Claim claim = Claim.create(4711L, new BigDecimal("300.00"), "Ada Lovelace", LocalDate.of(2026, 1, 15));
        PaymentPlan plan = claim.createPaymentPlan();
        plan.addInstallment(Installment.create(1, new BigDecimal("100.00"), LocalDate.of(2026, 2, 15)));
        plan.addInstallment(Installment.create(2, new BigDecimal("100.00"), LocalDate.of(2026, 3, 15)));
        plan.addInstallment(Installment.create(3, new BigDecimal("100.00"), LocalDate.of(2026, 4, 15)));
        return claim;
    }
}
