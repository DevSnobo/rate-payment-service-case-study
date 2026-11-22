package net.snobo.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import net.snobo.rest.dto.PaymentPlan;
import net.snobo.service.PaymentPlanService;

@RestController
@RequestMapping("/api/payments/plan")
public class PaymentPlanController {

    private final PaymentPlanService service;

    public PaymentPlanController(PaymentPlanService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    public PaymentPlan getPaymentPlan(@PathVariable("id") Long id) {
        return service.getPaymentPlan(id);
    }

}
