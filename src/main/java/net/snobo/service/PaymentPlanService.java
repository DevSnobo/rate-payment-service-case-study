package net.snobo.service;

import java.time.Clock;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import net.snobo.db.repository.PaymentPlanRepository;
import net.snobo.rest.dto.Claim;
import net.snobo.rest.dto.Installment;
import net.snobo.rest.dto.PaymentPlan;

@Service
public class PaymentPlanService {

    private final PaymentPlanRepository repository;
    private final Clock clock;

    public PaymentPlanService(PaymentPlanRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public PaymentPlan getPaymentPlan(Long id) {
        Optional<net.snobo.db.entity.PaymentPlan> entityOptional = repository.findById(id);
        if (entityOptional.isPresent()) {
            var entity = entityOptional.get();
            return new PaymentPlan(new Claim(entity.getClaim()), entity.getClaim().getTotalAmount(),
                    entity.statusAt(clock),
                    entity.getInstallments().stream().map(Installment::new).toList());
        } else {
            return null;
        }
    }
}
