package net.snobo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import net.snobo.db.entity.Claim;
import net.snobo.db.entity.Installment;
import net.snobo.db.entity.PaymentPlan;
import net.snobo.db.entity.TestInstallments;
import net.snobo.db.repository.ClaimRepository;
import net.snobo.domain.Status;
import net.snobo.rest.dto.InstallmentResponse;
import net.snobo.rest.dto.PaymentPlanResponse;
import net.snobo.service.exception.ClaimNotFoundException;
import net.snobo.service.exception.InvalidClaimRequestException;
import net.snobo.service.exception.PaymentPlanNotFoundException;

@ExtendWith(MockitoExtension.class)
class PaymentPlanServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-15T10:00:00Z"),
            ZoneId.of("Europe/Berlin"));

    @Mock
    private ClaimRepository claimRepository;

    private PaymentPlanService paymentPlanService;

    @BeforeEach
    void setUp() {
        paymentPlanService = new PaymentPlanService(claimRepository, CLOCK);
    }

    @Test
    void returnsFlatResponseForActivePlan() {
        Claim claim = claimWithPlan(
                TestInstallments.paid(1, new BigDecimal("100.00"), new BigDecimal("100.00"),
                        LocalDate.of(2026, 3, 1)),
                Installment.create(2, new BigDecimal("200.00"), LocalDate.of(2026, 5, 1)));
        given(claimRepository.findByClaimNumber(4711L)).willReturn(Optional.of(claim));

        PaymentPlanResponse response = paymentPlanService.getPaymentPlanForClaim(4711L);

        assertThat(response.claimNumber()).isEqualTo("0000004711");
        assertThat(response.totalClaimAmount()).isEqualByComparingTo("300.00");
        assertThat(response.totalClaimAmount().scale()).isEqualTo(2);
        assertThat(response.amountPaid()).isEqualByComparingTo("100.00");
        assertThat(response.outstandingBalance()).isEqualByComparingTo("200.00");
        assertThat(response.status()).isEqualTo(Status.ACTIVE);
        assertThat(response.installments()).hasSize(2);
        assertThat(response.installments()).extracting(InstallmentResponse::installmentNumber)
                .containsExactly(1, 2);

        InstallmentResponse first = response.installments().get(0);
        assertThat(first.scheduledAmount()).isEqualByComparingTo("100.00");
        assertThat(first.amountPaid()).isEqualByComparingTo("100.00");
        assertThat(first.dueDate()).isEqualTo(LocalDate.of(2026, 3, 1));

        InstallmentResponse second = response.installments().get(1);
        assertThat(second.scheduledAmount()).isEqualByComparingTo("200.00");
        assertThat(second.amountPaid()).isEqualByComparingTo("0.00");
        assertThat(second.dueDate()).isEqualTo(LocalDate.of(2026, 5, 1));
    }

    @Test
    void reportsOverdueWhenPastDueInstallmentIsUnpaid() {
        Claim claim = claimWithPlan(
                TestInstallments.paid(1, new BigDecimal("100.00"), new BigDecimal("100.00"),
                        LocalDate.of(2026, 3, 1)),
                Installment.create(2, new BigDecimal("200.00"), LocalDate.of(2026, 3, 10)));
        given(claimRepository.findByClaimNumber(4711L)).willReturn(Optional.of(claim));

        PaymentPlanResponse response = paymentPlanService.getPaymentPlanForClaim(4711L);

        assertThat(response.status()).isEqualTo(Status.OVERDUE);
        assertThat(response.amountPaid()).isEqualByComparingTo("100.00");
        assertThat(response.outstandingBalance()).isEqualByComparingTo("200.00");
    }

    @Test
    void reportsCompletedWhenAllInstallmentsArePaid() {
        Claim claim = claimWithPlan(
                TestInstallments.paid(1, new BigDecimal("100.00"), new BigDecimal("100.00"),
                        LocalDate.of(2026, 3, 1)),
                TestInstallments.paid(2, new BigDecimal("200.00"), new BigDecimal("200.00"),
                        LocalDate.of(2026, 3, 10)));
        given(claimRepository.findByClaimNumber(4711L)).willReturn(Optional.of(claim));

        PaymentPlanResponse response = paymentPlanService.getPaymentPlanForClaim(4711L);

        assertThat(response.status()).isEqualTo(Status.COMPLETED);
        assertThat(response.amountPaid()).isEqualByComparingTo("300.00");
        assertThat(response.outstandingBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void rejectsUnknownClaim() {
        given(claimRepository.findByClaimNumber(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentPlanService.getPaymentPlanForClaim(999L))
                .isInstanceOf(ClaimNotFoundException.class)
                .hasMessage("No claim 0000000999");
    }

    @Test
    void rejectsClaimWithoutPaymentPlan() {
        Claim claim = Claim.create(4711L, new BigDecimal("300.00"), "Ada Lovelace",
                LocalDate.of(2026, 1, 15));
        given(claimRepository.findByClaimNumber(4711L)).willReturn(Optional.of(claim));

        assertThatThrownBy(() -> paymentPlanService.getPaymentPlanForClaim(4711L))
                .isInstanceOf(PaymentPlanNotFoundException.class)
                .hasMessage("No payment plan for claim 0000004711");
    }

    @Test
    void rejectsInvalidClaimNumber() {
        assertThatThrownBy(() -> paymentPlanService.getPaymentPlanForClaim(0L))
                .isInstanceOf(InvalidClaimRequestException.class)
                .hasMessage("claimNumber must be positive");
    }

    private static Claim claimWithPlan(Installment... installments) {
        Claim claim = Claim.create(4711L, new BigDecimal("300.00"), "Ada Lovelace",
                LocalDate.of(2026, 1, 15));
        PaymentPlan plan = claim.createPaymentPlan();
        for (Installment installment : installments) {
            plan.addInstallment(installment);
        }
        plan.validateAgainstClaim();
        return claim;
    }
}
