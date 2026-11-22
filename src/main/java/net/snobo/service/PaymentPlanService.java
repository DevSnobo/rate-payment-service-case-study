package net.snobo.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import net.snobo.db.entity.Claim;
import net.snobo.db.entity.Installment;
import net.snobo.db.entity.PaymentPlan;
import net.snobo.db.repository.ClaimRepository;
import net.snobo.domain.ClaimNumbers;
import net.snobo.domain.InstallmentSchedule;
import net.snobo.domain.ScheduledInstallment;
import net.snobo.rest.dto.CreatePaymentPlanRequest;
import net.snobo.rest.dto.PaymentPlanResponse;
import net.snobo.service.exception.ClaimNotFoundException;
import net.snobo.service.exception.InvalidClaimRequestException;
import net.snobo.service.exception.InvalidPaymentPlanRequestException;
import net.snobo.service.exception.PaymentPlanAlreadyExistsException;
import net.snobo.service.exception.PaymentPlanNotFoundException;

@Service
public class PaymentPlanService {

    private final ClaimRepository claimRepository;
    private final Clock clock;

    public PaymentPlanService(ClaimRepository claimRepository, Clock clock) {
        this.claimRepository = claimRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PaymentPlanResponse getPaymentPlanForClaim(long claimNumber) {
        long validClaimNumber = validateClaimNumber(claimNumber);
        Claim claim = claimRepository.findByClaimNumber(validClaimNumber)
                .orElseThrow(() -> new ClaimNotFoundException(validClaimNumber));
        PaymentPlan paymentPlan = claim.getPaymentPlan();
        if (paymentPlan == null) {
            throw new PaymentPlanNotFoundException(validClaimNumber);
        }
        return PaymentPlanResponse.from(paymentPlan, clock);
    }

    @Transactional
    public PaymentPlanResponse createPaymentPlan(long claimNumber, CreatePaymentPlanRequest request) {
        long validClaimNumber = validateClaimNumber(claimNumber);
        if (request == null) {
            throw new InvalidPaymentPlanRequestException("request body is required");
        }
        boolean hasCount = request.installmentCount() != null;
        boolean hasAmount = request.installmentAmount() != null;
        if (hasCount == hasAmount) {
            throw new InvalidPaymentPlanRequestException(
                    "provide exactly one of installmentCount or installmentAmount");
        }

        Claim claim = claimRepository.findByClaimNumber(validClaimNumber)
                .orElseThrow(() -> new ClaimNotFoundException(validClaimNumber));
        if (claim.getPaymentPlan() != null) {
            throw new PaymentPlanAlreadyExistsException(validClaimNumber);
        }

        LocalDate today = LocalDate.now(clock);
        LocalDate firstDueDate = request.firstDueDate() == null
                ? today.withDayOfMonth(1).plusMonths(1)
                : request.firstDueDate();
        if (firstDueDate.isBefore(today)) {
            throw new InvalidPaymentPlanRequestException("firstDueDate must not be in the past");
        }

        List<ScheduledInstallment> schedule;
        try {
            schedule = hasCount
                    ? InstallmentSchedule.fromCount(claim.getTotalAmount(),
                            request.installmentCount(), firstDueDate)
                    : InstallmentSchedule.fromAmount(claim.getTotalAmount(),
                            request.installmentAmount(), firstDueDate);
        } catch (IllegalArgumentException exception) {
            throw new InvalidPaymentPlanRequestException(exception.getMessage(), exception);
        }

        PaymentPlan plan = claim.createPaymentPlan();
        for (ScheduledInstallment scheduled : schedule) {
            plan.addInstallment(Installment.create(scheduled.installmentNumber(),
                    scheduled.amount(), scheduled.dueDate()));
        }
        try {
            plan.validateAgainstClaim();
        } catch (IllegalStateException exception) {
            throw new InvalidPaymentPlanRequestException(exception.getMessage(), exception);
        }

        try {
            claimRepository.saveAndFlush(claim);
        } catch (DataIntegrityViolationException exception) {
            throw new PaymentPlanAlreadyExistsException(validClaimNumber);
        }
        return PaymentPlanResponse.from(plan, clock);
    }

    private long validateClaimNumber(long claimNumber) {
        try {
            return ClaimNumbers.require(claimNumber);
        } catch (IllegalArgumentException exception) {
            throw new InvalidClaimRequestException(exception.getMessage(), exception);
        }
    }
}
