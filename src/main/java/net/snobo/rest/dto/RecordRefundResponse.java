package net.snobo.rest.dto;

public record RecordRefundResponse(RefundResponse refund, PaymentResponse payment,
                                   PaymentPlanResponse paymentPlan) {
}
