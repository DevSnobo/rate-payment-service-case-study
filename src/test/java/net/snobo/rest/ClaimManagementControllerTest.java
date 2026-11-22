package net.snobo.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import net.snobo.domain.Status;
import net.snobo.rest.dto.ClaimResponse;
import net.snobo.rest.dto.CreateClaimRequest;
import net.snobo.rest.dto.CreatePaymentPlanRequest;
import net.snobo.rest.dto.CreatePaymentRequest;
import net.snobo.rest.dto.CreateRefundRequest;
import net.snobo.rest.dto.InstallmentResponse;
import net.snobo.rest.dto.PaymentAllocationResponse;
import net.snobo.rest.dto.PaymentPlanResponse;
import net.snobo.rest.dto.PaymentResponse;
import net.snobo.rest.dto.RecordPaymentResponse;
import net.snobo.rest.dto.RecordRefundResponse;
import net.snobo.rest.dto.RefundResponse;
import net.snobo.service.ClaimService;
import net.snobo.service.PaymentPlanService;
import net.snobo.service.PaymentService;
import net.snobo.service.RefundService;
import net.snobo.service.exception.ClaimNotFoundException;
import net.snobo.service.exception.DuplicateClaimException;
import net.snobo.service.exception.DuplicatePaymentReferenceException;
import net.snobo.service.exception.DuplicateRefundReferenceException;
import net.snobo.service.exception.InsufficientCreditException;
import net.snobo.service.exception.InvalidClaimRequestException;
import net.snobo.service.exception.InvalidPaymentPlanRequestException;
import net.snobo.service.exception.InvalidPaymentRequestException;
import net.snobo.service.exception.InvalidRefundRequestException;
import net.snobo.service.exception.PaymentConflictException;
import net.snobo.service.exception.PaymentNotFoundException;
import net.snobo.service.exception.PaymentPlanAlreadyExistsException;
import net.snobo.service.exception.PaymentPlanNotFoundException;

@WebMvcTest(controllers = ClaimManagementController.class)
class ClaimManagementControllerTest {

    private static final String VALID_CLAIM_JSON = """
            {"claimNumber":4711,"totalAmount":300.00,"debtorName":"Ada Lovelace","createdOn":"2026-01-15"}
            """;
    private static final String INVALID_REQUEST = "Invalid request";

    @MockitoBean
    private ClaimService claimService;

    @MockitoBean
    private PaymentPlanService paymentPlanService;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private RefundService refundService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsPaymentPlanForPaddedClaimNumber() throws Exception {
        PaymentPlanResponse response = new PaymentPlanResponse(
                "0000004711",
                new BigDecimal("300.00"),
                new BigDecimal("100.00"),
                new BigDecimal("200.00"),
                new BigDecimal("0.00"),
                new BigDecimal("0.00"),
                Status.ACTIVE,
                List.of(
                        new InstallmentResponse(1, new BigDecimal("100.00"), new BigDecimal("100.00"),
                                LocalDate.of(2026, 3, 1)),
                        new InstallmentResponse(2, new BigDecimal("200.00"), new BigDecimal("0.00"),
                                LocalDate.of(2026, 5, 1))));
        given(paymentPlanService.getPaymentPlanForClaim(4711L)).willReturn(response);

        mockMvc.perform(get("/api/claims/0000004711/payment-plan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimNumber").value("0000004711"))
                .andExpect(jsonPath("$.totalClaimAmount").value(300.00))
                .andExpect(jsonPath("$.amountPaid").value(100.00))
                .andExpect(jsonPath("$.outstandingBalance").value(200.00))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.installments.length()").value(2))
                .andExpect(jsonPath("$.installments[0].installmentNumber").value(1))
                .andExpect(jsonPath("$.installments[0].scheduledAmount").value(100.00))
                .andExpect(jsonPath("$.installments[0].amountPaid").value(100.00))
                .andExpect(jsonPath("$.installments[0].dueDate").value("2026-03-01"))
                .andExpect(jsonPath("$.installments[1].installmentNumber").value(2))
                .andExpect(jsonPath("$.installments[1].scheduledAmount").value(200.00))
                .andExpect(jsonPath("$.installments[1].amountPaid").value(0.00))
                .andExpect(jsonPath("$.installments[1].dueDate").value("2026-05-01"))
                .andExpect(content().string(containsString("\"totalClaimAmount\":300.00")))
                .andExpect(content().string(containsString("\"amountPaid\":100.00")))
                .andExpect(content().string(containsString("\"outstandingBalance\":200.00")));

        verify(paymentPlanService).getPaymentPlanForClaim(4711L);
    }

    @Test
    void returnsClaimNotFoundProblemForUnknownClaim() throws Exception {
        given(paymentPlanService.getPaymentPlanForClaim(4711L))
                .willThrow(new ClaimNotFoundException(4711L));

        mockMvc.perform(get("/api/claims/0000004711/payment-plan"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Claim not found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("No claim 0000004711"))
                .andExpect(jsonPath("$.instance").value("/api/claims/0000004711/payment-plan"));
    }

    @Test
    void returnsPaymentPlanNotFoundProblemForClaimWithoutPlan() throws Exception {
        given(paymentPlanService.getPaymentPlanForClaim(4711L))
                .willThrow(new PaymentPlanNotFoundException(4711L));

        mockMvc.perform(get("/api/claims/0000004711/payment-plan"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Payment plan not found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("No payment plan for claim 0000004711"))
                .andExpect(jsonPath("$.instance").value("/api/claims/0000004711/payment-plan"));
    }

    @Test
    void returnsBadRequestForMalformedClaimNumber() throws Exception {
        mockMvc.perform(get("/api/claims/abc/payment-plan"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("claimNumber must be a valid number"))
                .andExpect(jsonPath("$.instance").value("/api/claims/abc/payment-plan"));
    }

    @Test
    void returnsClaimForPaddedClaimNumber() throws Exception {
        given(claimService.getClaim(4711L)).willReturn(new ClaimResponse("0000004711",
                new BigDecimal("300.00"), "Ada Lovelace", LocalDate.of(2026, 1, 15)));

        mockMvc.perform(get("/api/claims/0000004711"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimNumber").value("0000004711"))
                .andExpect(jsonPath("$.totalAmount").value(300.00))
                .andExpect(jsonPath("$.debtorName").value("Ada Lovelace"))
                .andExpect(jsonPath("$.createdOn").value("2026-01-15"))
                .andExpect(content().string(containsString("\"totalAmount\":300.00")));

        verify(claimService).getClaim(4711L);
    }

    @Test
    void returnsClaimNotFoundProblemForUnknownClaimLookup() throws Exception {
        given(claimService.getClaim(4711L)).willThrow(new ClaimNotFoundException(4711L));

        mockMvc.perform(get("/api/claims/0000004711"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Claim not found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("No claim 0000004711"))
                .andExpect(jsonPath("$.instance").value("/api/claims/0000004711"));
    }

    @Test
    void returnsBadRequestForMalformedClaimNumberOnClaimLookup() throws Exception {
        mockMvc.perform(get("/api/claims/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.detail").value("claimNumber must be a valid number"))
                .andExpect(jsonPath("$.instance").value("/api/claims/abc"));
    }

    @Test
    void createsClaimAndReturnsLocation() throws Exception {
        given(claimService.createClaim(any(CreateClaimRequest.class)))
                .willReturn(new ClaimResponse("0000004711", new BigDecimal("300.00"), "Ada Lovelace",
                        LocalDate.of(2026, 1, 15)));

        mockMvc.perform(post("/api/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CLAIM_JSON))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/claims/0000004711"))
                .andExpect(jsonPath("$.claimNumber").value("0000004711"))
                .andExpect(jsonPath("$.totalAmount").value(300.00))
                .andExpect(jsonPath("$.debtorName").value("Ada Lovelace"))
                .andExpect(jsonPath("$.createdOn").value("2026-01-15"))
                .andExpect(content().string(containsString("\"totalAmount\":300.00")));

        ArgumentCaptor<CreateClaimRequest> captor = ArgumentCaptor.forClass(CreateClaimRequest.class);
        verify(claimService).createClaim(captor.capture());
        CreateClaimRequest captured = captor.getValue();
        assertThat(captured.claimNumber()).isEqualTo(4711L);
        assertThat(captured.totalAmount()).isEqualByComparingTo("300.00");
        assertThat(captured.debtorName()).isEqualTo("Ada Lovelace");
        assertThat(captured.createdOn()).isEqualTo(LocalDate.of(2026, 1, 15));
    }

    @Test
    void returnsConflictForDuplicateClaim() throws Exception {
        given(claimService.createClaim(any(CreateClaimRequest.class)))
                .willThrow(new DuplicateClaimException(4711L));

        mockMvc.perform(post("/api/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CLAIM_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Claim already exists"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Claim 0000004711 already exists"))
                .andExpect(jsonPath("$.instance").value("/api/claims"));
    }

    @Test
    void returnsBadRequestForMalformedJson() throws Exception {
        mockMvc.perform(post("/api/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"claimNumber\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request body"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Request body is missing or malformed"));
    }

    @Test
    void returnsBadRequestForInvalidClaimFields() throws Exception {
        given(claimService.createClaim(any(CreateClaimRequest.class)))
                .willThrow(new InvalidClaimRequestException("claimNumber must be positive"));

        mockMvc.perform(post("/api/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CLAIM_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("claimNumber must be positive"));
    }

    @Test
    void createsPaymentPlanFromCount() throws Exception {
        given(paymentPlanService.createPaymentPlan(anyLong(), any(CreatePaymentPlanRequest.class)))
                .willReturn(paymentPlanResponse());

        mockMvc.perform(post("/api/claims/0000004711/payment-plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"installmentCount":9,"firstDueDate":"2026-12-01"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/claims/0000004711/payment-plan"))
                .andExpect(jsonPath("$.claimNumber").value("0000004711"))
                .andExpect(jsonPath("$.totalClaimAmount").value(500.00))
                .andExpect(jsonPath("$.amountPaid").value(0.00))
                .andExpect(jsonPath("$.outstandingBalance").value(500.00))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.installments.length()").value(2));

        ArgumentCaptor<CreatePaymentPlanRequest> captor =
                ArgumentCaptor.forClass(CreatePaymentPlanRequest.class);
        verify(paymentPlanService).createPaymentPlan(eq(4711L), captor.capture());
        assertThat(captor.getValue().installmentCount()).isEqualTo(9);
        assertThat(captor.getValue().installmentAmount()).isNull();
        assertThat(captor.getValue().firstDueDate()).isEqualTo(LocalDate.of(2026, 12, 1));
    }

    @Test
    void createsPaymentPlanFromAmount() throws Exception {
        given(paymentPlanService.createPaymentPlan(anyLong(), any(CreatePaymentPlanRequest.class)))
                .willReturn(paymentPlanResponse());

        mockMvc.perform(post("/api/claims/0000004711/payment-plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"installmentAmount":80.00,"firstDueDate":"2026-12-01"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/claims/0000004711/payment-plan"))
                .andExpect(jsonPath("$.installments.length()").value(2));

        ArgumentCaptor<CreatePaymentPlanRequest> captor =
                ArgumentCaptor.forClass(CreatePaymentPlanRequest.class);
        verify(paymentPlanService).createPaymentPlan(eq(4711L), captor.capture());
        assertThat(captor.getValue().installmentCount()).isNull();
        assertThat(captor.getValue().installmentAmount()).isEqualByComparingTo("80.00");
    }

    @Test
    void returnsNotFoundWhenCreatingPlanForUnknownClaim() throws Exception {
        given(paymentPlanService.createPaymentPlan(anyLong(), any(CreatePaymentPlanRequest.class)))
                .willThrow(new ClaimNotFoundException(4711L));

        mockMvc.perform(post("/api/claims/0000004711/payment-plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"installmentCount\":9}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Claim not found"))
                .andExpect(jsonPath("$.detail").value("No claim 0000004711"));
    }

    @Test
    void returnsConflictWhenPlanAlreadyExists() throws Exception {
        given(paymentPlanService.createPaymentPlan(anyLong(), any(CreatePaymentPlanRequest.class)))
                .willThrow(new PaymentPlanAlreadyExistsException(4711L));

        mockMvc.perform(post("/api/claims/0000004711/payment-plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"installmentCount\":9}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Payment plan already exists"))
                .andExpect(jsonPath("$.detail").value("Payment plan already exists for claim 0000004711"));
    }

    @Test
    void returnsBadRequestForInvalidPaymentPlanFields() throws Exception {
        given(paymentPlanService.createPaymentPlan(anyLong(), any(CreatePaymentPlanRequest.class)))
                .willThrow(new InvalidPaymentPlanRequestException(
                        "installmentCount must be between 1 and 120"));

        mockMvc.perform(post("/api/claims/0000004711/payment-plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"installmentCount\":121}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.detail").value("installmentCount must be between 1 and 120"));
    }

    @Test
    void returnsBadRequestForMalformedPaymentPlanJson() throws Exception {
        mockMvc.perform(post("/api/claims/0000004711/payment-plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"installmentCount\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request body"));
    }

    @Test
    void returnsBadRequestForMalformedClaimNumberOnCreatePlan() throws Exception {
        mockMvc.perform(post("/api/claims/abc/payment-plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"installmentCount\":9}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.detail").value("claimNumber must be a valid number"));
    }

    @Test
    void recordsPayment() throws Exception {
        given(paymentService.recordPayment(anyLong(), any(CreatePaymentRequest.class)))
                .willReturn(recordPaymentResponse());

        mockMvc.perform(post("/api/claims/0000004711/payment-plan/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":150.00,"paidOn":"2026-03-14","reference":"bank-1"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.payment.paymentId").value("payment-1"))
                .andExpect(jsonPath("$.payment.amount").value(150.00))
                .andExpect(jsonPath("$.payment.paidOn").value("2026-03-14"))
                .andExpect(jsonPath("$.payment.reference").value("bank-1"))
                .andExpect(jsonPath("$.payment.allocatedAmount").value(150.00))
                .andExpect(jsonPath("$.payment.unallocatedAmount").value(0.00))
                .andExpect(jsonPath("$.payment.allocations.length()").value(2))
                .andExpect(jsonPath("$.payment.allocations[0].installmentNumber").value(1))
                .andExpect(jsonPath("$.payment.allocations[0].amount").value(100.00))
                .andExpect(jsonPath("$.payment.allocations[1].installmentNumber").value(2))
                .andExpect(jsonPath("$.payment.allocations[1].amount").value(50.00))
                .andExpect(jsonPath("$.paymentPlan.claimNumber").value("0000004711"))
                .andExpect(jsonPath("$.paymentPlan.creditAmount").value(0.00));

        ArgumentCaptor<CreatePaymentRequest> captor = ArgumentCaptor.forClass(CreatePaymentRequest.class);
        verify(paymentService).recordPayment(eq(4711L), captor.capture());
        assertThat(captor.getValue().amount()).isEqualByComparingTo("150.00");
        assertThat(captor.getValue().paidOn()).isEqualTo(LocalDate.of(2026, 3, 14));
        assertThat(captor.getValue().reference()).isEqualTo("bank-1");
    }

    @Test
    void returnsPaymentHistory() throws Exception {
        given(paymentService.getPayments(4711L)).willReturn(List.of(paymentResponse()));

        mockMvc.perform(get("/api/claims/0000004711/payment-plan/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].paymentId").value("payment-1"))
                .andExpect(jsonPath("$[0].amount").value(150.00))
                .andExpect(jsonPath("$[0].allocations.length()").value(2));

        verify(paymentService).getPayments(4711L);
    }

    @Test
    void returnsNotFoundWhenRecordingPaymentForUnknownClaim() throws Exception {
        given(paymentService.recordPayment(anyLong(), any(CreatePaymentRequest.class)))
                .willThrow(new ClaimNotFoundException(4711L));

        mockMvc.perform(post("/api/claims/0000004711/payment-plan/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":10.00,\"paidOn\":\"2026-03-14\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Claim not found"));
    }

    @Test
    void returnsNotFoundWhenRecordingPaymentWithoutPlan() throws Exception {
        given(paymentService.recordPayment(anyLong(), any(CreatePaymentRequest.class)))
                .willThrow(new PaymentPlanNotFoundException(4711L));

        mockMvc.perform(post("/api/claims/0000004711/payment-plan/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":10.00,\"paidOn\":\"2026-03-14\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Payment plan not found"));
    }

    @Test
    void returnsConflictForDuplicatePaymentReference() throws Exception {
        given(paymentService.recordPayment(anyLong(), any(CreatePaymentRequest.class)))
                .willThrow(new DuplicatePaymentReferenceException(4711L, "bank-1"));

        mockMvc.perform(post("/api/claims/0000004711/payment-plan/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":10.00,\"paidOn\":\"2026-03-14\",\"reference\":\"bank-1\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Payment reference already exists"))
                .andExpect(jsonPath("$.detail").value(
                        "Payment reference 'bank-1' already exists for claim 0000004711"));
    }

    @Test
    void returnsBadRequestForInvalidPayment() throws Exception {
        given(paymentService.recordPayment(anyLong(), any(CreatePaymentRequest.class)))
                .willThrow(new InvalidPaymentRequestException("paidOn must not be in the future"));

        mockMvc.perform(post("/api/claims/0000004711/payment-plan/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":10.00,\"paidOn\":\"2026-03-20\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid payment request"))
                .andExpect(jsonPath("$.detail").value("paidOn must not be in the future"));
    }

    @Test
    void returnsConflictForPaymentConflict() throws Exception {
        given(paymentService.recordPayment(anyLong(), any(CreatePaymentRequest.class)))
                .willThrow(new PaymentConflictException("Payment could not be recorded due to a data conflict"));

        mockMvc.perform(post("/api/claims/0000004711/payment-plan/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":10.00,\"paidOn\":\"2026-03-14\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Payment conflict"));
    }

    @Test
    void returnsBadRequestForMalformedPaymentJson() throws Exception {
        mockMvc.perform(post("/api/claims/0000004711/payment-plan/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request body"));
    }

    @Test
    void returnsBadRequestForMalformedClaimNumberOnPayment() throws Exception {
        mockMvc.perform(post("/api/claims/abc/payment-plan/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":10.00,\"paidOn\":\"2026-03-14\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.detail").value("claimNumber must be a valid number"));
    }

    @Test
    void returnsNotFoundForPaymentHistoryWithoutPlan() throws Exception {
        given(paymentService.getPayments(4711L)).willThrow(new PaymentPlanNotFoundException(4711L));

        mockMvc.perform(get("/api/claims/0000004711/payment-plan/payments"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Payment plan not found"));
    }

    @Test
    void recordsRefund() throws Exception {
        given(refundService.recordRefund(anyLong(), anyString(), any(CreateRefundRequest.class)))
                .willReturn(recordRefundResponse());

        mockMvc.perform(post("/api/claims/0000004711/payment-plan/payments/payment-1/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":20.00,"refundedOn":"2026-03-20","reference":"refund-1"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.refund.refundId").value("refund-1"))
                .andExpect(jsonPath("$.refund.paymentId").value("payment-1"))
                .andExpect(jsonPath("$.refund.amount").value(20.00))
                .andExpect(jsonPath("$.refund.refundedOn").value("2026-03-20"))
                .andExpect(jsonPath("$.refund.reference").value("refund-1"))
                .andExpect(jsonPath("$.payment.refundedAmount").value(20.00))
                .andExpect(jsonPath("$.payment.unallocatedAmount").value(30.00))
                .andExpect(jsonPath("$.payment.refunds.length()").value(1))
                .andExpect(jsonPath("$.paymentPlan.creditAmount").value(30.00))
                .andExpect(jsonPath("$.paymentPlan.refundedAmount").value(20.00));

        verify(refundService).recordRefund(eq(4711L), eq("payment-1"),
                any(CreateRefundRequest.class));
    }

    @Test
    void returnsRefundHistory() throws Exception {
        given(refundService.getRefunds(4711L)).willReturn(List.of(refundResponse()));

        mockMvc.perform(get("/api/claims/0000004711/payment-plan/refunds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].refundId").value("refund-1"))
                .andExpect(jsonPath("$[0].amount").value(20.00))
                .andExpect(jsonPath("$[0].refundedOn").value("2026-03-20"));

        verify(refundService).getRefunds(4711L);
    }

    @Test
    void returnsNotFoundWhenRecordingRefundForUnknownPayment() throws Exception {
        given(refundService.recordRefund(anyLong(), anyString(), any(CreateRefundRequest.class)))
                .willThrow(new PaymentNotFoundException(4711L, "payment-1"));

        mockMvc.perform(post("/api/claims/0000004711/payment-plan/payments/payment-1/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":10.00,\"refundedOn\":\"2026-03-20\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Payment not found"));
    }

    @Test
    void returnsConflictForInsufficientCredit() throws Exception {
        given(refundService.recordRefund(anyLong(), anyString(), any(CreateRefundRequest.class)))
                .willThrow(new InsufficientCreditException(new BigDecimal("50.00")));

        mockMvc.perform(post("/api/claims/0000004711/payment-plan/payments/payment-1/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":60.00,\"refundedOn\":\"2026-03-20\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Insufficient credit"))
                .andExpect(jsonPath("$.detail").value("Refund exceeds available credit 50.00"));
    }

    @Test
    void returnsConflictForDuplicateRefundReference() throws Exception {
        given(refundService.recordRefund(anyLong(), anyString(), any(CreateRefundRequest.class)))
                .willThrow(new DuplicateRefundReferenceException("payment-1", "refund-1"));

        mockMvc.perform(post("/api/claims/0000004711/payment-plan/payments/payment-1/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":10.00,\"refundedOn\":\"2026-03-20\",\"reference\":\"refund-1\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Refund reference already exists"))
                .andExpect(jsonPath("$.detail").value(
                        "Refund reference 'refund-1' already exists for payment payment-1"));
    }

    @Test
    void returnsBadRequestForInvalidRefund() throws Exception {
        given(refundService.recordRefund(anyLong(), anyString(), any(CreateRefundRequest.class)))
                .willThrow(new InvalidRefundRequestException("refundedOn must not be in the future"));

        mockMvc.perform(post("/api/claims/0000004711/payment-plan/payments/payment-1/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":10.00,\"refundedOn\":\"2026-03-26\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid refund request"))
                .andExpect(jsonPath("$.detail").value("refundedOn must not be in the future"));
    }

    @Test
    void returnsBadRequestForMalformedRefundJson() throws Exception {
        mockMvc.perform(post("/api/claims/0000004711/payment-plan/payments/payment-1/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request body"));
    }

    @Test
    void returnsBadRequestForMalformedClaimNumberOnRefund() throws Exception {
        mockMvc.perform(post("/api/claims/abc/payment-plan/payments/payment-1/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":10.00,\"refundedOn\":\"2026-03-20\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.detail").value("claimNumber must be a valid number"));
    }

    @Test
    void returnsNotFoundForRefundHistoryWithoutPlan() throws Exception {
        given(refundService.getRefunds(4711L)).willThrow(new PaymentPlanNotFoundException(4711L));

        mockMvc.perform(get("/api/claims/0000004711/payment-plan/refunds"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Payment plan not found"));
    }

    @Test
    void hidesUnexpectedErrorMessage() throws Exception {
        given(claimService.createClaim(any(CreateClaimRequest.class)))
                .willThrow(new RuntimeException("secret"));

        mockMvc.perform(post("/api/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CLAIM_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Internal server error"))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
                .andExpect(content().string(not(containsString("secret"))));
    }

    private static RecordRefundResponse recordRefundResponse() {
        return new RecordRefundResponse(refundResponse(), refundedPaymentResponse(),
                refundedPlanResponse());
    }

    private static RefundResponse refundResponse() {
        return new RefundResponse("refund-1", "payment-1", new BigDecimal("20.00"),
                LocalDate.of(2026, 3, 20), Instant.parse("2026-03-25T10:00:00Z"),
                "refund-1");
    }

    private static PaymentResponse refundedPaymentResponse() {
        return new PaymentResponse("payment-1", new BigDecimal("150.00"),
                LocalDate.of(2026, 3, 14), Instant.parse("2026-03-15T10:00:00Z"),
                "bank-1", new BigDecimal("100.00"), new BigDecimal("20.00"),
                new BigDecimal("30.00"),
                List.of(new PaymentAllocationResponse(1, new BigDecimal("100.00"),
                        LocalDate.of(2026, 4, 1))),
                List.of(refundResponse()));
    }

    private static PaymentPlanResponse refundedPlanResponse() {
        return new PaymentPlanResponse("0000004711", new BigDecimal("100.00"),
                new BigDecimal("100.00"), new BigDecimal("0.00"), new BigDecimal("30.00"),
                new BigDecimal("20.00"), Status.COMPLETED,
                List.of(new InstallmentResponse(1, new BigDecimal("100.00"),
                        new BigDecimal("100.00"), LocalDate.of(2026, 4, 1))));
    }

    private static RecordPaymentResponse recordPaymentResponse() {
        return new RecordPaymentResponse(paymentResponse(), paymentPlanResponse());
    }

    private static PaymentResponse paymentResponse() {
        return new PaymentResponse("payment-1", new BigDecimal("150.00"),
                LocalDate.of(2026, 3, 14), Instant.parse("2026-03-15T10:00:00Z"),
                "bank-1", new BigDecimal("150.00"), new BigDecimal("0.00"),
                new BigDecimal("0.00"),
                List.of(
                        new PaymentAllocationResponse(1, new BigDecimal("100.00"),
                                LocalDate.of(2026, 4, 1)),
                        new PaymentAllocationResponse(2, new BigDecimal("50.00"),
                                LocalDate.of(2026, 5, 1))),
                List.of());
    }

    private static PaymentPlanResponse paymentPlanResponse() {
        return new PaymentPlanResponse("0000004711", new BigDecimal("500.00"),
                new BigDecimal("0.00"), new BigDecimal("500.00"), new BigDecimal("0.00"),
                new BigDecimal("0.00"), Status.ACTIVE,
                List.of(
                        new InstallmentResponse(1, new BigDecimal("250.00"), new BigDecimal("0.00"),
                                LocalDate.of(2026, 12, 1)),
                        new InstallmentResponse(2, new BigDecimal("250.00"), new BigDecimal("0.00"),
                                LocalDate.of(2027, 1, 1))));
    }
}
