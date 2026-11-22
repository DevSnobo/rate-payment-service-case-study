package net.snobo.rest;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import net.snobo.rest.dto.ClaimResponse;
import net.snobo.rest.dto.CreateClaimRequest;
import net.snobo.rest.dto.PaymentPlanResponse;
import net.snobo.service.ClaimService;
import net.snobo.service.PaymentPlanService;

@RestController
@RequestMapping("/api/claims")
public class ClaimManagementController {

    private final ClaimService claimService;
    private final PaymentPlanService paymentPlanService;

    public ClaimManagementController(ClaimService claimService, PaymentPlanService paymentPlanService) {
        this.claimService = claimService;
        this.paymentPlanService = paymentPlanService;
    }

    @PostMapping
    public ResponseEntity<ClaimResponse> createClaim(@RequestBody CreateClaimRequest request) {
        ClaimResponse response = claimService.createClaim(request);
        URI location = URI.create("/api/claims/" + response.claimNumber());
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{claimNumber}")
    public ClaimResponse getClaim(@PathVariable long claimNumber) {
        return claimService.getClaim(claimNumber);
    }

    @GetMapping("/{claimNumber}/payment-plan")
    public PaymentPlanResponse getPaymentPlan(@PathVariable long claimNumber) {
        return paymentPlanService.getPaymentPlanForClaim(claimNumber);
    }
}
