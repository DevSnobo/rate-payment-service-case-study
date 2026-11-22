package net.snobo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import net.snobo.db.entity.Claim;
import net.snobo.db.entity.Installment;
import net.snobo.db.entity.Payment;
import net.snobo.db.entity.PaymentPlan;
import net.snobo.db.entity.TestInstallments;
import net.snobo.db.repository.ClaimRepository;
import net.snobo.db.repository.PaymentRepository;
import net.snobo.domain.Status;
import net.snobo.rest.dto.CreatePaymentRequest;
import net.snobo.rest.dto.PaymentAllocationResponse;
import net.snobo.rest.dto.RecordPaymentResponse;
import net.snobo.service.exception.ClaimNotFoundException;
import net.snobo.service.exception.DuplicatePaymentReferenceException;
import net.snobo.service.exception.InvalidClaimRequestException;
import net.snobo.service.exception.InvalidPaymentRequestException;
import net.snobo.service.exception.PaymentPlanNotFoundException;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-15T10:00:00Z"), ZoneId.of("Europe/Berlin"));
    private static final LocalDate PAID_ON = LocalDate.of(2026, 3, 14);

    @Mock
    private ClaimRepository claimRepository;

    @Mock
    private PaymentRepository paymentRepository;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(claimRepository, paymentRepository, CLOCK);
    }

    @Test
    void recordsPartialPaymentOnOldestInstallment() {
        Claim claim = claimWithPlan(installment(1, "100.00", "2026-04-01"), installment(2, "200.00", "2026-05-01"));
        given(claimRepository.findByClaimNumberForUpdate(4711L)).willReturn(Optional.of(claim));

        RecordPaymentResponse response = paymentService.recordPayment(4711L, payment("80.00", null));

        assertThat(response.payment().amount()).isEqualByComparingTo("80.00");
        assertThat(response.payment().allocatedAmount()).isEqualByComparingTo("80.00");
        assertThat(response.payment().unallocatedAmount()).isEqualByComparingTo("0.00");
        assertThat(response.payment().allocations()).hasSize(1);
        PaymentAllocationResponse allocation = response.payment().allocations().get(0);
        assertThat(allocation.installmentNumber()).isEqualTo(1);
        assertThat(allocation.amount()).isEqualByComparingTo("80.00");
        assertThat(response.paymentPlan().amountPaid()).isEqualByComparingTo("80.00");
        assertThat(response.paymentPlan().outstandingBalance()).isEqualByComparingTo("220.00");
        assertThat(response.paymentPlan().creditAmount()).isEqualByComparingTo("0.00");
        assertThat(response.paymentPlan().status()).isEqualTo(Status.ACTIVE);
    }

    private static Claim claimWithPlan(Installment... installments) {
        BigDecimal total = java.util.Arrays.stream(installments).map(Installment::getScheduledAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Claim claim = Claim.create(4711L, total, "Ada Lovelace", LocalDate.of(2026, 1, 15));
        PaymentPlan plan = claim.createPaymentPlan();
        for (Installment installment : installments) {
            plan.addInstallment(installment);
        }
        plan.validateAgainstClaim();
        return claim;
    }

    private static Installment installment(int number, String amount, String dueDate) {
        return Installment.create(number, new BigDecimal(amount), LocalDate.parse(dueDate));
    }

    private static CreatePaymentRequest payment(String amount, String reference) {
        return new CreatePaymentRequest(new BigDecimal(amount), PAID_ON, reference);
    }

    @Test
    void spreadsPaymentAcrossInstallments() {
        Claim claim = claimWithPlan(installment(1, "100.00", "2026-04-01"), installment(2, "200.00", "2026-05-01"));
        given(claimRepository.findByClaimNumberForUpdate(4711L)).willReturn(Optional.of(claim));

        RecordPaymentResponse response = paymentService.recordPayment(4711L, payment("150.00", null));

        assertThat(response.payment().allocations()).hasSize(2);
        assertThat(response.payment().allocations()).extracting(PaymentAllocationResponse::installmentNumber)
                .containsExactly(1, 2);
        assertThat(response.payment().allocations().get(0).amount()).isEqualByComparingTo("100.00");
        assertThat(response.payment().allocations().get(1).amount()).isEqualByComparingTo("50.00");
        assertThat(response.paymentPlan().amountPaid()).isEqualByComparingTo("150.00");
        assertThat(response.paymentPlan().outstandingBalance()).isEqualByComparingTo("150.00");
    }

    @Test
    void recordsOverpaymentAsCredit() {
        Claim claim = claimWithPlan(installment(1, "100.00", "2026-04-01"), installment(2, "200.00", "2026-05-01"));
        given(claimRepository.findByClaimNumberForUpdate(4711L)).willReturn(Optional.of(claim));

        RecordPaymentResponse response = paymentService.recordPayment(4711L, payment("350.00", null));

        assertThat(response.payment().allocatedAmount()).isEqualByComparingTo("300.00");
        assertThat(response.payment().unallocatedAmount()).isEqualByComparingTo("50.00");
        assertThat(response.paymentPlan().amountPaid()).isEqualByComparingTo("300.00");
        assertThat(response.paymentPlan().outstandingBalance()).isEqualByComparingTo("0.00");
        assertThat(response.paymentPlan().creditAmount()).isEqualByComparingTo("50.00");
        assertThat(response.paymentPlan().status()).isEqualTo(Status.COMPLETED);
    }

    @Test
    void acceptsPaymentOnCompletedPlanAsCredit() {
        Claim claim = claimWithPlan(
                TestInstallments.paid(1, new BigDecimal("100.00"), new BigDecimal("100.00"), LocalDate.of(2026, 4, 1)),
                TestInstallments.paid(2, new BigDecimal("200.00"), new BigDecimal("200.00"), LocalDate.of(2026, 5, 1)));
        given(claimRepository.findByClaimNumberForUpdate(4711L)).willReturn(Optional.of(claim));

        RecordPaymentResponse response = paymentService.recordPayment(4711L, payment("50.00", null));

        assertThat(response.payment().allocations()).isEmpty();
        assertThat(response.payment().unallocatedAmount()).isEqualByComparingTo("50.00");
        assertThat(response.paymentPlan().outstandingBalance()).isEqualByComparingTo("0.00");
        assertThat(response.paymentPlan().creditAmount()).isEqualByComparingTo("50.00");
        assertThat(response.paymentPlan().status()).isEqualTo(Status.COMPLETED);
    }

    @Test
    void recordsReferenceAndTimestamp() {
        Claim claim = claimWithPlan(installment(1, "100.00", "2026-04-01"));
        given(claimRepository.findByClaimNumberForUpdate(4711L)).willReturn(Optional.of(claim));

        RecordPaymentResponse response = paymentService.recordPayment(4711L, payment("20.00", " bank-1 "));

        assertThat(response.payment().reference()).isEqualTo("bank-1");
        assertThat(response.payment().recordedAt()).isEqualTo(Instant.parse("2026-03-15T10:00:00Z"));
        assertThat(response.payment().paymentId()).isNotBlank();
    }

    @Test
    void rejectsFuturePaidOn() {
        Claim claim = claimWithPlan(installment(1, "100.00", "2026-04-01"));
        given(claimRepository.findByClaimNumberForUpdate(4711L)).willReturn(Optional.of(claim));

        CreatePaymentRequest request =
                new CreatePaymentRequest(new BigDecimal("20.00"), LocalDate.of(2026, 3, 16), null);
        assertThatThrownBy(() -> paymentService.recordPayment(4711L, request)).isInstanceOf(
                InvalidPaymentRequestException.class).hasMessage("paidOn must not be in the future");
    }

    @Test
    void rejectsNullPaidOn() {
        Claim claim = claimWithPlan(installment(1, "100.00", "2026-04-01"));
        given(claimRepository.findByClaimNumberForUpdate(4711L)).willReturn(Optional.of(claim));

        CreatePaymentRequest request = new CreatePaymentRequest(new BigDecimal("20.00"), null, null);
        assertThatThrownBy(() -> paymentService.recordPayment(4711L, request)).isInstanceOf(
                InvalidPaymentRequestException.class).hasMessage("paidOn is required");
    }

    @Test
    void rejectsInvalidAmount() {
        Claim claim = claimWithPlan(installment(1, "100.00", "2026-04-01"));
        given(claimRepository.findByClaimNumberForUpdate(4711L)).willReturn(Optional.of(claim));

        CreatePaymentRequest payment = payment("0.00", null);
        assertThatThrownBy(() -> paymentService.recordPayment(4711L, payment)).isInstanceOf(
                InvalidPaymentRequestException.class).hasMessage("amount must be positive");
    }

    @Test
    void rejectsAmountWithMoreThanTwoDecimals() {
        Claim claim = claimWithPlan(installment(1, "100.00", "2026-04-01"));
        given(claimRepository.findByClaimNumberForUpdate(4711L)).willReturn(Optional.of(claim));

        CreatePaymentRequest payment = payment("20.001", null);
        assertThatThrownBy(() -> paymentService.recordPayment(4711L, payment)).isInstanceOf(
                InvalidPaymentRequestException.class).hasMessage("amount must have at most 2 decimal places");
    }

    @Test
    void rejectsUnknownClaim() {
        given(claimRepository.findByClaimNumberForUpdate(999L)).willReturn(Optional.empty());

        CreatePaymentRequest payment = payment("20.00", null);
        assertThatThrownBy(() -> paymentService.recordPayment(999L, payment)).isInstanceOf(ClaimNotFoundException.class)
                .hasMessage("No claim 0000000999");
    }

    @Test
    void rejectsClaimWithoutPlan() {
        Claim claim = Claim.create(4711L, new BigDecimal("100.00"), "Ada Lovelace", LocalDate.of(2026, 1, 15));
        given(claimRepository.findByClaimNumberForUpdate(4711L)).willReturn(Optional.of(claim));

        CreatePaymentRequest payment = payment("20.00", null);
        assertThatThrownBy(() -> paymentService.recordPayment(4711L, payment)).isInstanceOf(
                PaymentPlanNotFoundException.class).hasMessage("No payment plan for claim 0000004711");
    }

    @Test
    void rejectsInvalidClaimNumber() {
        CreatePaymentRequest payment = payment("20.00", null);
        assertThatThrownBy(() -> paymentService.recordPayment(0L, payment)).isInstanceOf(
                InvalidClaimRequestException.class).hasMessage("claimNumber must be positive");
    }

    @Test
    void rejectsDuplicateReference() {
        Claim claim = claimWithPlan(installment(1, "100.00", "2026-04-01"));
        given(claimRepository.findByClaimNumberForUpdate(4711L)).willReturn(Optional.of(claim));
        given(paymentRepository.existsByPaymentPlanIdAndReference(any(), eq("bank-1"))).willReturn(true);

        CreatePaymentRequest payment = payment("20.00", "bank-1");
        assertThatThrownBy(() -> paymentService.recordPayment(4711L, payment)).isInstanceOf(
                DuplicatePaymentReferenceException.class).hasMessageContaining("bank-1");
    }

    @Test
    void mapsUniqueConstraintViolationToDuplicateReference() {
        Claim claim = claimWithPlan(installment(1, "100.00", "2026-04-01"));
        given(claimRepository.findByClaimNumberForUpdate(4711L)).willReturn(Optional.of(claim));
        given(paymentRepository.saveAndFlush(any(Payment.class))).willThrow(
                new DataIntegrityViolationException("unique constraint"));

        CreatePaymentRequest payment = payment("20.00", "bank-1");
        assertThatThrownBy(() -> paymentService.recordPayment(4711L, payment)).isInstanceOf(
                DuplicatePaymentReferenceException.class).hasMessageContaining("bank-1");
    }

    @Test
    void returnsPaymentHistory() {
        Claim claim = claimWithPlan(installment(1, "100.00", "2026-04-01"));
        Payment payment =
                Payment.create(new BigDecimal("20.00"), PAID_ON, "bank-1", Instant.parse("2026-03-15T10:00:00Z"));
        given(claimRepository.findByClaimNumber(4711L)).willReturn(Optional.of(claim));
        given(paymentRepository.findByPaymentPlanIdOrderByPaidOnAscIdAsc(any())).willReturn(List.of(payment));

        var history = paymentService.getPayments(4711L);

        assertThat(history).hasSize(1);
        assertThat(history.getFirst().amount()).isEqualByComparingTo("20.00");
        assertThat(history.getFirst().reference()).isEqualTo("bank-1");
    }

    @Test
    void historyRejectsClaimWithoutPlan() {
        Claim claim = Claim.create(4711L, new BigDecimal("100.00"), "Ada Lovelace", LocalDate.of(2026, 1, 15));
        given(claimRepository.findByClaimNumber(4711L)).willReturn(Optional.of(claim));

        assertThatThrownBy(() -> paymentService.getPayments(4711L)).isInstanceOf(PaymentPlanNotFoundException.class)
                .hasMessage("No payment plan for claim 0000004711");
    }
}
