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
import net.snobo.db.entity.Refund;
import net.snobo.db.repository.ClaimRepository;
import net.snobo.db.repository.PaymentRepository;
import net.snobo.db.repository.RefundRepository;
import net.snobo.rest.dto.CreateRefundRequest;
import net.snobo.rest.dto.RecordRefundResponse;
import net.snobo.service.exception.ClaimNotFoundException;
import net.snobo.service.exception.DuplicateRefundReferenceException;
import net.snobo.service.exception.InsufficientCreditException;
import net.snobo.service.exception.InvalidClaimRequestException;
import net.snobo.service.exception.InvalidRefundRequestException;
import net.snobo.service.exception.PaymentNotFoundException;
import net.snobo.service.exception.PaymentPlanNotFoundException;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-25T10:00:00Z"), ZoneId.of("Europe/Berlin"));
    private static final LocalDate REFUNDED_ON = LocalDate.of(2026, 3, 20);
    private static final Instant RECORDED_AT = Instant.parse("2026-03-25T10:00:00Z");

    @Mock
    private ClaimRepository claimRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private RefundRepository refundRepository;

    private RefundService refundService;

    @BeforeEach
    void setUp() {
        refundService = new RefundService(claimRepository, paymentRepository, refundRepository, CLOCK);
    }

    @Test
    void recordsPartialRefund() {
        PlanFixture fixture = overpaidPlan();
        given(claimRepository.findByClaimNumberForUpdate(4711L)).willReturn(Optional.of(fixture.claim()));
        given(paymentRepository.findByPaymentId(fixture.payment().getPaymentId())).willReturn(
                Optional.of(fixture.payment()));

        RecordRefundResponse response =
                refundService.recordRefund(4711L, fixture.payment().getPaymentId(), refund("20.00", null));

        assertThat(response.refund().refundId()).isNotBlank();
        assertThat(response.refund().paymentId()).isEqualTo(fixture.payment().getPaymentId());
        assertThat(response.refund().amount()).isEqualByComparingTo("20.00");
        assertThat(response.refund().refundedOn()).isEqualTo(REFUNDED_ON);
        assertThat(response.payment().refundedAmount()).isEqualByComparingTo("20.00");
        assertThat(response.payment().unallocatedAmount()).isEqualByComparingTo("30.00");
        assertThat(response.payment().refunds()).hasSize(1);
        assertThat(response.paymentPlan().creditAmount()).isEqualByComparingTo("30.00");
        assertThat(response.paymentPlan().refundedAmount()).isEqualByComparingTo("20.00");
    }

    private static PlanFixture overpaidPlan() {
        Claim claim = Claim.create(4711L, new BigDecimal("100.00"), "Ada Lovelace", LocalDate.of(2026, 1, 15));
        PaymentPlan plan = claim.createPaymentPlan();
        plan.addInstallment(Installment.create(1, new BigDecimal("100.00"), LocalDate.of(2026, 4, 1)));
        plan.validateAgainstClaim();
        Payment payment = Payment.create(new BigDecimal("150.00"), LocalDate.of(2026, 3, 14), null,
                Instant.parse("2026-03-15T10:00:00Z"));
        plan.addPayment(payment);
        plan.allocate(payment);
        return new PlanFixture(claim, payment);
    }

    private static CreateRefundRequest refund(String amount, String reference) {
        return new CreateRefundRequest(new BigDecimal(amount), REFUNDED_ON, reference);
    }

    @Test
    void recordsMultipleRefundsUntilCreditIsExhausted() {
        PlanFixture fixture = overpaidPlan();
        given(claimRepository.findByClaimNumberForUpdate(4711L)).willReturn(Optional.of(fixture.claim()));
        String paymentId = fixture.payment().getPaymentId();
        given(paymentRepository.findByPaymentId(paymentId)).willReturn(
                Optional.of(fixture.payment()));

        refundService.recordRefund(4711L, paymentId, refund("30.00", null));
        RecordRefundResponse response =
                refundService.recordRefund(4711L, paymentId, refund("20.00", null));

        assertThat(response.payment().refundedAmount()).isEqualByComparingTo("50.00");
        assertThat(response.payment().unallocatedAmount()).isEqualByComparingTo("0.00");
        assertThat(response.paymentPlan().creditAmount()).isEqualByComparingTo("0.00");
        assertThat(response.paymentPlan().refundedAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    void rejectsRefundGreaterThanCredit() {
        PlanFixture fixture = overpaidPlan();
        given(claimRepository.findByClaimNumberForUpdate(4711L)).willReturn(Optional.of(fixture.claim()));
        String paymentId = fixture.payment().getPaymentId();
        given(paymentRepository.findByPaymentId(paymentId)).willReturn(
                Optional.of(fixture.payment()));

        CreateRefundRequest refund = refund("50.01", null);
        assertThatThrownBy(
                () -> refundService.recordRefund(4711L, paymentId, refund)).isInstanceOf(
                InsufficientCreditException.class).hasMessage("Refund exceeds available credit 50.00");
    }

    @Test
    void rejectsRefundWhenPaymentHasNoCredit() {
        Claim claim = Claim.create(4711L, new BigDecimal("100.00"), "Ada Lovelace", LocalDate.of(2026, 1, 15));
        PaymentPlan plan = claim.createPaymentPlan();
        plan.addInstallment(Installment.create(1, new BigDecimal("100.00"), LocalDate.of(2026, 4, 1)));
        plan.validateAgainstClaim();
        Payment payment = Payment.create(new BigDecimal("100.00"), LocalDate.of(2026, 3, 14), null, RECORDED_AT);
        plan.addPayment(payment);
        plan.allocate(payment);
        given(claimRepository.findByClaimNumberForUpdate(4711L)).willReturn(Optional.of(claim));
        String paymentId = payment.getPaymentId();
        given(paymentRepository.findByPaymentId(paymentId)).willReturn(Optional.of(payment));

        CreateRefundRequest refund = refund("10.00", null);
        assertThatThrownBy(() -> refundService.recordRefund(4711L, paymentId, refund)).isInstanceOf(
                InsufficientCreditException.class).hasMessage("Refund exceeds available credit 0.00");
    }

    @Test
    void rejectsUnknownClaim() {
        given(claimRepository.findByClaimNumberForUpdate(999L)).willReturn(Optional.empty());

        CreateRefundRequest refund = refund("10.00", null);
        assertThatThrownBy(() -> refundService.recordRefund(999L, "payment-1", refund)).isInstanceOf(
                ClaimNotFoundException.class).hasMessage("No claim 0000000999");
    }

    @Test
    void rejectsClaimWithoutPlan() {
        Claim claim = Claim.create(4711L, new BigDecimal("100.00"), "Ada Lovelace", LocalDate.of(2026, 1, 15));
        given(claimRepository.findByClaimNumberForUpdate(4711L)).willReturn(Optional.of(claim));

        CreateRefundRequest refund = refund("10.00", null);
        assertThatThrownBy(() -> refundService.recordRefund(4711L, "payment-1", refund)).isInstanceOf(
                PaymentPlanNotFoundException.class).hasMessage("No payment plan for claim 0000004711");
    }

    @Test
    void rejectsUnknownPayment() {
        PlanFixture fixture = overpaidPlan();
        given(claimRepository.findByClaimNumberForUpdate(4711L)).willReturn(Optional.of(fixture.claim()));
        given(paymentRepository.findByPaymentId("unknown")).willReturn(Optional.empty());

        CreateRefundRequest refund = refund("10.00", null);
        assertThatThrownBy(() -> refundService.recordRefund(4711L, "unknown", refund)).isInstanceOf(
                PaymentNotFoundException.class).hasMessage("No payment unknown for claim 0000004711");
    }

    @Test
    void rejectsPaymentFromAnotherPlan() {
        PlanFixture fixture = overpaidPlan();
        Claim otherClaim = Claim.create(4712L, new BigDecimal("100.00"), "Grace Hopper", LocalDate.of(2026, 1, 15));
        PaymentPlan otherPlan = otherClaim.createPaymentPlan();
        otherPlan.addInstallment(Installment.create(1, new BigDecimal("100.00"), LocalDate.of(2026, 4, 1)));
        otherPlan.validateAgainstClaim();
        Payment otherPayment = Payment.create(new BigDecimal("150.00"), LocalDate.of(2026, 3, 14), null, RECORDED_AT);
        otherPlan.addPayment(otherPayment);
        otherPlan.allocate(otherPayment);

        given(claimRepository.findByClaimNumberForUpdate(4711L)).willReturn(Optional.of(fixture.claim()));
        String paymentId = otherPayment.getPaymentId();
        given(paymentRepository.findByPaymentId(paymentId)).willReturn(Optional.of(otherPayment));

        CreateRefundRequest refund = refund("10.00", null);
        assertThatThrownBy(() -> refundService.recordRefund(4711L, paymentId, refund)).isInstanceOf(
                PaymentNotFoundException.class);
    }

    @Test
    void rejectsInvalidRefundAmount() {
        PlanFixture fixture = overpaidPlan();
        given(claimRepository.findByClaimNumberForUpdate(4711L)).willReturn(Optional.of(fixture.claim()));
        String paymentId = fixture.payment().getPaymentId();
        given(paymentRepository.findByPaymentId(paymentId)).willReturn(
                Optional.of(fixture.payment()));

        CreateRefundRequest refund = refund("0.00", null);
        assertThatThrownBy(
                () -> refundService.recordRefund(4711L, paymentId, refund)).isInstanceOf(
                InvalidRefundRequestException.class).hasMessage("amount must be positive");
    }

    @Test
    void rejectsAmountWithMoreThanTwoDecimals() {
        PlanFixture fixture = overpaidPlan();
        given(claimRepository.findByClaimNumberForUpdate(4711L)).willReturn(Optional.of(fixture.claim()));
        String paymentId = fixture.payment().getPaymentId();
        given(paymentRepository.findByPaymentId(paymentId)).willReturn(
                Optional.of(fixture.payment()));

        CreateRefundRequest refund = refund("10.001", null);
        assertThatThrownBy(
                () -> refundService.recordRefund(4711L, paymentId, refund)).isInstanceOf(
                InvalidRefundRequestException.class).hasMessage("amount must have at most 2 decimal places");
    }

    @Test
    void rejectsNullRefundedOn() {
        PlanFixture fixture = overpaidPlan();
        given(claimRepository.findByClaimNumberForUpdate(4711L)).willReturn(Optional.of(fixture.claim()));
        String paymentId = fixture.payment().getPaymentId();
        given(paymentRepository.findByPaymentId(paymentId)).willReturn(
                Optional.of(fixture.payment()));

        CreateRefundRequest request = new CreateRefundRequest(new BigDecimal("10.00"), null, null);
        assertThatThrownBy(
                () -> refundService.recordRefund(4711L, paymentId, request)).isInstanceOf(
                InvalidRefundRequestException.class).hasMessage("refundedOn is required");
    }

    @Test
    void rejectsFutureRefundedOn() {
        PlanFixture fixture = overpaidPlan();
        given(claimRepository.findByClaimNumberForUpdate(4711L)).willReturn(Optional.of(fixture.claim()));
        String paymentId = fixture.payment().getPaymentId();
        given(paymentRepository.findByPaymentId(paymentId)).willReturn(
                Optional.of(fixture.payment()));

        CreateRefundRequest request = new CreateRefundRequest(new BigDecimal("10.00"), LocalDate.of(2026, 3, 26), null);
        assertThatThrownBy(
                () -> refundService.recordRefund(4711L, paymentId, request)).isInstanceOf(
                InvalidRefundRequestException.class).hasMessage("refundedOn must not be in the future");
    }

    @Test
    void rejectsDuplicateReference() {
        PlanFixture fixture = overpaidPlan();
        given(claimRepository.findByClaimNumberForUpdate(4711L)).willReturn(Optional.of(fixture.claim()));
        String paymentId = fixture.payment().getPaymentId();
        given(paymentRepository.findByPaymentId(paymentId)).willReturn(
                Optional.of(fixture.payment()));
        given(refundRepository.existsByPaymentIdAndReference(any(), eq("refund-1"))).willReturn(true);

        CreateRefundRequest refund = refund("10.00", "refund-1");
        assertThatThrownBy(
                () -> refundService.recordRefund(4711L, paymentId, refund)).isInstanceOf(
                DuplicateRefundReferenceException.class).hasMessageContaining("refund-1");
    }

    @Test
    void mapsUniqueConstraintViolationToDuplicateReference() {
        PlanFixture fixture = overpaidPlan();
        given(claimRepository.findByClaimNumberForUpdate(4711L)).willReturn(Optional.of(fixture.claim()));
        String paymentId = fixture.payment().getPaymentId();
        given(paymentRepository.findByPaymentId(paymentId)).willReturn(
                Optional.of(fixture.payment()));
        given(refundRepository.saveAndFlush(any(Refund.class))).willThrow(
                new DataIntegrityViolationException("unique constraint"));

        CreateRefundRequest refund = refund("10.00", "refund-1");
        assertThatThrownBy(
                () -> refundService.recordRefund(4711L, paymentId, refund)).isInstanceOf(
                DuplicateRefundReferenceException.class);
    }

    @Test
    void rejectsInvalidClaimNumber() {
        CreateRefundRequest refund = refund("10.00", null);
        assertThatThrownBy(() -> refundService.recordRefund(0L, "payment-1", refund)).isInstanceOf(
                InvalidClaimRequestException.class).hasMessage("claimNumber must be positive");
    }

    @Test
    void returnsRefundHistory() {
        PlanFixture fixture = overpaidPlan();
        Refund refund = Refund.create(new BigDecimal("10.00"), REFUNDED_ON, "refund-1", RECORDED_AT);
        fixture.payment().addRefund(refund);
        given(claimRepository.findByClaimNumber(4711L)).willReturn(Optional.of(fixture.claim()));
        given(refundRepository.findByPaymentPaymentPlanIdOrderByRefundedOnAscIdAsc(any())).willReturn(List.of(refund));

        var history = refundService.getRefunds(4711L);

        assertThat(history).hasSize(1);
        assertThat(history.getFirst().amount()).isEqualByComparingTo("10.00");
        assertThat(history.getFirst().reference()).isEqualTo("refund-1");
    }

    @Test
    void historyRejectsClaimWithoutPlan() {
        Claim claim = Claim.create(4711L, new BigDecimal("100.00"), "Ada Lovelace", LocalDate.of(2026, 1, 15));
        given(claimRepository.findByClaimNumber(4711L)).willReturn(Optional.of(claim));

        assertThatThrownBy(() -> refundService.getRefunds(4711L)).isInstanceOf(PaymentPlanNotFoundException.class)
                .hasMessage("No payment plan for claim 0000004711");
    }

    private record PlanFixture(Claim claim, Payment payment) {

    }
}
