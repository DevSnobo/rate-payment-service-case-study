package net.snobo.service;

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
import net.snobo.db.repository.ClaimRepository;
import net.snobo.db.repository.PaymentRepository;
import net.snobo.domain.ClaimNumbers;
import net.snobo.rest.dto.CreatePaymentRequest;
import net.snobo.rest.dto.PaymentPlanResponse;
import net.snobo.rest.dto.PaymentResponse;
import net.snobo.rest.dto.RecordPaymentResponse;
import net.snobo.service.exception.ClaimNotFoundException;
import net.snobo.service.exception.DuplicatePaymentReferenceException;
import net.snobo.service.exception.InvalidClaimRequestException;
import net.snobo.service.exception.InvalidPaymentRequestException;
import net.snobo.service.exception.PaymentConflictException;
import net.snobo.service.exception.PaymentPlanNotFoundException;

@Service
public class PaymentService {

    private final ClaimRepository claimRepository;
    private final PaymentRepository paymentRepository;
    private final Clock clock;

    public PaymentService(ClaimRepository claimRepository, PaymentRepository paymentRepository, Clock clock) {
        this.claimRepository = claimRepository;
        this.paymentRepository = paymentRepository;
        this.clock = clock;
    }

    @Transactional
    public RecordPaymentResponse recordPayment(long claimNumber, CreatePaymentRequest request) {
        long validClaimNumber = validateClaimNumber(claimNumber);
        if (request == null) {
            throw new InvalidPaymentRequestException("request body is required");
        }

        Claim claim = claimRepository.findByClaimNumberForUpdate(validClaimNumber)
                .orElseThrow(() -> new ClaimNotFoundException(validClaimNumber));
        PaymentPlan plan = claim.getPaymentPlan();
        if (plan == null) {
            throw new PaymentPlanNotFoundException(validClaimNumber);
        }

        LocalDate today = LocalDate.now(clock);
        if (request.paidOn() == null) {
            throw new InvalidPaymentRequestException("paidOn is required");
        }
        if (request.paidOn().isAfter(today)) {
            throw new InvalidPaymentRequestException("paidOn must not be in the future");
        }

        Payment payment;
        try {
            payment = Payment.create(request.amount(), request.paidOn(), request.reference(),
                    Instant.now(clock));
        } catch (IllegalArgumentException exception) {
            throw new InvalidPaymentRequestException(exception.getMessage(), exception);
        }

        if (payment.getReference() != null
                && paymentRepository.existsByPaymentPlanIdAndReference(plan.getId(), payment.getReference())) {
            throw new DuplicatePaymentReferenceException(validClaimNumber, payment.getReference());
        }

        plan.addPayment(payment);
        try {
            plan.allocate(payment);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new InvalidPaymentRequestException(exception.getMessage(), exception);
        }

        try {
            paymentRepository.saveAndFlush(payment);
        } catch (DataIntegrityViolationException exception) {
            if (payment.getReference() != null) {
                throw new DuplicatePaymentReferenceException(validClaimNumber, payment.getReference());
            }
            throw new PaymentConflictException("Payment could not be recorded due to a data conflict");
        }

        return new RecordPaymentResponse(
                PaymentResponse.from(payment),
                PaymentPlanResponse.from(plan, clock));
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPayments(long claimNumber) {
        long validClaimNumber = validateClaimNumber(claimNumber);
        Claim claim = claimRepository.findByClaimNumber(validClaimNumber)
                .orElseThrow(() -> new ClaimNotFoundException(validClaimNumber));
        PaymentPlan plan = claim.getPaymentPlan();
        if (plan == null) {
            throw new PaymentPlanNotFoundException(validClaimNumber);
        }
        return paymentRepository.findByPaymentPlanIdOrderByPaidOnAscIdAsc(plan.getId()).stream()
                .map(PaymentResponse::from)
                .toList();
    }

    private long validateClaimNumber(long claimNumber) {
        try {
            return ClaimNumbers.require(claimNumber);
        } catch (IllegalArgumentException exception) {
            throw new InvalidClaimRequestException(exception.getMessage(), exception);
        }
    }
}
