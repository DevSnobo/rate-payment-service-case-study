package net.snobo.service;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import net.snobo.db.repository.PaymentPlanRepository;
import net.snobo.rest.dto.Installment;
import net.snobo.rest.dto.PaymentPlan;

@Service
public class PaymentPlanService {

    private final PaymentPlanRepository repository;

    public PaymentPlanService(PaymentPlanRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public PaymentPlan getPaymentPlan(Long id) {
        Optional<net.snobo.db.entity.PaymentPlan> entityOptional = repository.findById(id);
        if (entityOptional.isPresent()) {
            var entity = entityOptional.get();
            return new PaymentPlan(entity.getClaimNumber(), entity.getTotalClaimAmount(),
                    entity.getOutstandingBalance(), entity.getAmountPaid(), entity.getStatus(),
                    entity.getInstallments().stream().map(Installment::new).toList());
        } else {
            return null;
        }
    }
}
