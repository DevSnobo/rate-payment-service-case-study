package net.snobo.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import net.snobo.db.entity.Claim;
import net.snobo.db.entity.Payment;
import net.snobo.db.entity.PaymentPlan;
import net.snobo.db.entity.Refund;
import net.snobo.db.repository.ClaimRepository;
import net.snobo.db.repository.PaymentRepository;
import net.snobo.db.repository.RefundRepository;
import net.snobo.domain.ClaimNumbers;
import net.snobo.rest.dto.CreateRefundRequest;
import net.snobo.rest.dto.PaymentPlanResponse;
import net.snobo.rest.dto.PaymentResponse;
import net.snobo.rest.dto.RecordRefundResponse;
import net.snobo.rest.dto.RefundResponse;
import net.snobo.service.exception.ClaimNotFoundException;
import net.snobo.service.exception.DuplicateRefundReferenceException;
import net.snobo.service.exception.InsufficientCreditException;
import net.snobo.service.exception.InvalidClaimRequestException;
import net.snobo.service.exception.InvalidRefundRequestException;
import net.snobo.service.exception.PaymentNotFoundException;
import net.snobo.service.exception.PaymentPlanNotFoundException;
import net.snobo.service.exception.RefundConflictException;

@Service
public class RefundService {

    private final ClaimRepository claimRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final Clock clock;

    public RefundService(ClaimRepository claimRepository, PaymentRepository paymentRepository,
                         RefundRepository refundRepository, Clock clock) {
        this.claimRepository = claimRepository;
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.clock = clock;
    }

    @Transactional
    public RecordRefundResponse recordRefund(long claimNumber, String paymentId,
                                              CreateRefundRequest request) {
        long validClaimNumber = validateClaimNumber(claimNumber);
        if (paymentId == null || paymentId.isBlank()) {
            throw new InvalidRefundRequestException("paymentId is required");
        }
        if (request == null) {
            throw new InvalidRefundRequestException("request body is required");
        }

        Claim claim = claimRepository.findByClaimNumberForUpdate(validClaimNumber)
                .orElseThrow(() -> new ClaimNotFoundException(validClaimNumber));
        PaymentPlan plan = claim.getPaymentPlan();
        if (plan == null) {
            throw new PaymentPlanNotFoundException(validClaimNumber);
        }

        Payment payment = paymentRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(validClaimNumber, paymentId));
        if (!belongsToPlan(payment, plan)) {
            throw new PaymentNotFoundException(validClaimNumber, paymentId);
        }

        LocalDate today = LocalDate.now(clock);
        if (request.refundedOn() == null) {
            throw new InvalidRefundRequestException("refundedOn is required");
        }
        if (request.refundedOn().isAfter(today)) {
            throw new InvalidRefundRequestException("refundedOn must not be in the future");
        }

        Refund refund;
        try {
            refund = Refund.create(request.amount(), request.refundedOn(), request.reference(),
                    Instant.now(clock));
        } catch (IllegalArgumentException exception) {
            throw new InvalidRefundRequestException(exception.getMessage(), exception);
        }

        BigDecimal availableCredit = payment.unallocatedAmount();
        if (refund.getAmount().compareTo(availableCredit) > 0) {
            throw new InsufficientCreditException(availableCredit);
        }
        if (refund.getReference() != null
                && refundRepository.existsByPaymentIdAndReference(payment.getId(), refund.getReference())) {
            throw new DuplicateRefundReferenceException(payment.getPaymentId(), refund.getReference());
        }

        payment.addRefund(refund);

        try {
            refundRepository.saveAndFlush(refund);
        } catch (DataIntegrityViolationException exception) {
            if (refund.getReference() != null) {
                throw new DuplicateRefundReferenceException(payment.getPaymentId(), refund.getReference());
            }
            throw new RefundConflictException("Refund could not be recorded due to a data conflict");
        }

        return new RecordRefundResponse(
                RefundResponse.from(refund),
                PaymentResponse.from(payment),
                PaymentPlanResponse.from(plan, clock));
    }

    @Transactional(readOnly = true)
    public List<RefundResponse> getRefunds(long claimNumber) {
        long validClaimNumber = validateClaimNumber(claimNumber);
        Claim claim = claimRepository.findByClaimNumber(validClaimNumber)
                .orElseThrow(() -> new ClaimNotFoundException(validClaimNumber));
        PaymentPlan plan = claim.getPaymentPlan();
        if (plan == null) {
            throw new PaymentPlanNotFoundException(validClaimNumber);
        }
        return refundRepository.findByPaymentPaymentPlanIdOrderByRefundedOnAscIdAsc(plan.getId()).stream()
                .map(RefundResponse::from)
                .toList();
    }

    private boolean belongsToPlan(Payment payment, PaymentPlan plan) {
        PaymentPlan paymentPlan = payment.getPaymentPlan();
        if (paymentPlan == null) {
            return false;
        }
        if (paymentPlan == plan) {
            return true;
        }
        return plan.getId() != null && plan.getId().equals(paymentPlan.getId());
    }

    private long validateClaimNumber(long claimNumber) {
        try {
            return ClaimNumbers.require(claimNumber);
        } catch (IllegalArgumentException exception) {
            throw new InvalidClaimRequestException(exception.getMessage(), exception);
        }
    }
}
