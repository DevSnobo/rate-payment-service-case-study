package net.snobo.rest;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import net.snobo.rest.dto.Claim;
import net.snobo.rest.dto.PaymentPlan;
import net.snobo.service.ClaimService;
import net.snobo.service.PaymentPlanService;

@RestController
@RequestMapping("/api/claims")
public class ClaimManagementController {

    private final ClaimService claimService;
    private final PaymentPlanService service;

    public ClaimManagementController(ClaimService claimService, PaymentPlanService service) {
        this.claimService = claimService;
        this.service = service;
    }

    @GetMapping({"/{customer_id}"})
    public List<Claim> getClaims(@PathVariable("customer_id") String customerId) {
        return claimService.getClaims(customerId);
    }

    @GetMapping("/{customer_id}/plan/{id}")
    public PaymentPlan getPaymentPlan(@PathVariable("customer_id") String customerId, @PathVariable("id") Long id) {
        //TODO: error handling
        return service.getPaymentPlan(id);
    }

}
