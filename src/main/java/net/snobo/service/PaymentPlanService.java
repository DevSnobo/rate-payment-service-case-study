package net.snobo.service;

import java.util.Optional;
import org.springframework.stereotype.Service;

import net.snobo.repository.PaymentPlanRepository;
import net.snobo.rest.dto.PaymentPlan;

@Service
public class PaymentPlanService {

    private PaymentPlanRepository repository;

    public PaymentPlanService(PaymentPlanRepository repository) {
        this.repository = repository;
    }

    public PaymentPlan getPaymentPlan(Long id) {
        return repository.findById(id).orElse(null);
    }
}
