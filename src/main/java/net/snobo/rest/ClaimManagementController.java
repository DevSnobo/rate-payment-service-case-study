package net.snobo.rest;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import net.snobo.rest.dto.ClaimResponse;
import net.snobo.rest.dto.CreateClaimRequest;
import net.snobo.rest.dto.CreatePaymentPlanRequest;
import net.snobo.rest.dto.CreatePaymentRequest;
import net.snobo.rest.dto.CreateRefundRequest;
import net.snobo.rest.dto.PaymentPlanResponse;
import net.snobo.rest.dto.PaymentResponse;
import net.snobo.rest.dto.RecordPaymentResponse;
import net.snobo.rest.dto.RecordRefundResponse;
import net.snobo.rest.dto.RefundResponse;
import net.snobo.service.ClaimService;
import net.snobo.service.PaymentPlanService;
import net.snobo.service.PaymentService;
import net.snobo.service.RefundService;

@RestController
@RequestMapping("/api/claims")
public class ClaimManagementController {

    private final ClaimService claimService;
    private final PaymentPlanService paymentPlanService;
    private final PaymentService paymentService;
    private final RefundService refundService;

    public ClaimManagementController(ClaimService claimService, PaymentPlanService paymentPlanService,
                                     PaymentService paymentService, RefundService refundService) {
        this.claimService = claimService;
        this.paymentPlanService = paymentPlanService;
        this.paymentService = paymentService;
        this.refundService = refundService;
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

    @PostMapping("/{claimNumber}/payment-plan")
    public ResponseEntity<PaymentPlanResponse> createPaymentPlan(@PathVariable long claimNumber,
                                                                 @RequestBody CreatePaymentPlanRequest request) {
        PaymentPlanResponse response = paymentPlanService.createPaymentPlan(claimNumber, request);
        URI location = URI.create("/api/claims/" + response.claimNumber() + "/payment-plan");
        return ResponseEntity.created(location).body(response);
    }

    @PostMapping("/{claimNumber}/payment-plan/payments")
    public ResponseEntity<RecordPaymentResponse> recordPayment(@PathVariable long claimNumber,
                                                               @RequestBody CreatePaymentRequest request) {
        RecordPaymentResponse response = paymentService.recordPayment(claimNumber, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{claimNumber}/payment-plan/payments")
    public List<PaymentResponse> getPayments(@PathVariable long claimNumber) {
        return paymentService.getPayments(claimNumber);
    }

    @PostMapping("/{claimNumber}/payment-plan/payments/{paymentId}/refunds")
    public ResponseEntity<RecordRefundResponse> recordRefund(@PathVariable long claimNumber,
                                                             @PathVariable String paymentId,
                                                             @RequestBody CreateRefundRequest request) {
        RecordRefundResponse response = refundService.recordRefund(claimNumber, paymentId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{claimNumber}/payment-plan/refunds")
    public List<RefundResponse> getRefunds(@PathVariable long claimNumber) {
        return refundService.getRefunds(claimNumber);
    }

    @GetMapping("/{claimNumber}/payment-plan")
    public PaymentPlanResponse getPaymentPlan(@PathVariable long claimNumber) {
        return paymentPlanService.getPaymentPlanForClaim(claimNumber);
    }
}
