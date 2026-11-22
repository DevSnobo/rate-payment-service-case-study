package net.snobo.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
import net.snobo.rest.dto.InstallmentResponse;
import net.snobo.rest.dto.PaymentPlanResponse;
import net.snobo.service.ClaimService;
import net.snobo.service.PaymentPlanService;
import net.snobo.service.exception.ClaimNotFoundException;
import net.snobo.service.exception.DuplicateClaimException;
import net.snobo.service.exception.InvalidClaimRequestException;
import net.snobo.service.exception.InvalidPaymentPlanRequestException;
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

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsPaymentPlanForPaddedClaimNumber() throws Exception {
        PaymentPlanResponse response = new PaymentPlanResponse(
                "0000004711",
                new BigDecimal("300.00"),
                new BigDecimal("100.00"),
                new BigDecimal("200.00"),
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

    private static PaymentPlanResponse paymentPlanResponse() {
        return new PaymentPlanResponse("0000004711", new BigDecimal("500.00"),
                new BigDecimal("0.00"), new BigDecimal("500.00"), Status.ACTIVE,
                List.of(
                        new InstallmentResponse(1, new BigDecimal("250.00"), new BigDecimal("0.00"),
                                LocalDate.of(2026, 12, 1)),
                        new InstallmentResponse(2, new BigDecimal("250.00"), new BigDecimal("0.00"),
                                LocalDate.of(2027, 1, 1))));
    }
}
