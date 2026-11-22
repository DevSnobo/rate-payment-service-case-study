package net.snobo.service;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import net.snobo.db.entity.Claim;
import net.snobo.db.entity.PaymentPlan;
import net.snobo.db.repository.ClaimRepository;
import net.snobo.domain.ClaimNumbers;
import net.snobo.rest.dto.PaymentPlanResponse;
import net.snobo.service.exception.ClaimNotFoundException;
import net.snobo.service.exception.InvalidClaimRequestException;
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
        long validClaimNumber;
        try {
            validClaimNumber = ClaimNumbers.require(claimNumber);
        } catch (IllegalArgumentException exception) {
            throw new InvalidClaimRequestException(exception.getMessage(), exception);
        }

        Claim claim = claimRepository.findByClaimNumber(validClaimNumber)
                .orElseThrow(() -> new ClaimNotFoundException(validClaimNumber));
        PaymentPlan paymentPlan = claim.getPaymentPlan();
        if (paymentPlan == null) {
            throw new PaymentPlanNotFoundException(validClaimNumber);
        }
        return PaymentPlanResponse.from(paymentPlan, clock);
    }
}
