package net.snobo.db.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class ClaimTest {

    private static final LocalDate CREATED_ON = LocalDate.of(2026, 1, 15);

    @Test
    void createBuildsClaimWithRequiredFields() {
        Claim claim = Claim.create(4711L, new BigDecimal("300.00"), "Ada Lovelace", CREATED_ON);

        assertThat(claim.getClaimNumber()).isEqualTo(4711L);
        assertThat(claim.getTotalAmount()).isEqualByComparingTo("300.00");
        assertThat(claim.getTotalAmount().scale()).isEqualTo(2);
        assertThat(claim.getDebtorName()).isEqualTo("Ada Lovelace");
        assertThat(claim.getCreatedOn()).isEqualTo(CREATED_ON);
        assertThat(claim.getPaymentPlan()).isNull();
    }

    @Test
    void createRejectsZeroClaimNumber() {
        assertThatThrownBy(() -> Claim.create(0L, new BigDecimal("300.00"), "Ada", CREATED_ON))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("claimNumber must be positive");
    }

    @Test
    void createRejectsClaimNumberAboveTenDigits() {
        assertThatThrownBy(() -> Claim.create(10_000_000_000L, new BigDecimal("300.00"), "Ada", CREATED_ON))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("claimNumber must have at most 10 digits");
    }

    @Test
    void createRejectsNullTotalAmount() {
        assertThatThrownBy(() -> Claim.create(4711L, null, "Ada", CREATED_ON))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("totalAmount is required");
    }

    @Test
    void createRejectsNonPositiveTotalAmount() {
        assertThatThrownBy(() -> Claim.create(4711L, new BigDecimal("0.00"), "Ada", CREATED_ON))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("totalAmount must be positive");
    }

    @Test
    void createRejectsBlankDebtorName() {
        assertThatThrownBy(() -> Claim.create(4711L, new BigDecimal("300.00"), "   ", CREATED_ON))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("debtorName is required");
    }

    @Test
    void createRejectsNullCreatedOn() {
        assertThatThrownBy(() -> Claim.create(4711L, new BigDecimal("300.00"), "Ada", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("createdOn is required");
    }

    @Test
    void createPaymentPlanLinksBothSides() {
        Claim claim = Claim.create(4711L, new BigDecimal("300.00"), "Ada", CREATED_ON);

        PaymentPlan plan = claim.createPaymentPlan();

        assertThat(claim.getPaymentPlan()).isSameAs(plan);
        assertThat(plan.getClaim()).isSameAs(claim);
    }

    @Test
    void createPaymentPlanRejectsSecondPlan() {
        Claim claim = Claim.create(4711L, new BigDecimal("300.00"), "Ada", CREATED_ON);
        claim.createPaymentPlan();

        assertThatThrownBy(claim::createPaymentPlan)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("claim already has a payment plan");
    }
}
