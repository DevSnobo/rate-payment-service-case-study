package net.snobo;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import net.snobo.db.entity.Claim;
import net.snobo.db.entity.Installment;
import net.snobo.db.entity.PaymentPlan;
import net.snobo.db.entity.TestInstallments;
import net.snobo.db.repository.ClaimRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentPlanFlowTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClaimRepository claimRepository;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void fixClock() {
        given(clock.instant()).willReturn(Instant.parse("2026-03-15T10:00:00Z"));
        given(clock.getZone()).willReturn(BERLIN);
    }

    @Test
    void seededPaymentPlanIsReturnedWithFullContract() throws Exception {
        seedPaymentPlan();

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
    }

    @Test
    void createdClaimHasNoPaymentPlan() throws Exception {
        mockMvc.perform(post("/api/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"claimNumber":4712,"totalAmount":150.00,"debtorName":"Grace Hopper","createdOn":"2026-02-01"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/claims/0000004712"))
                .andExpect(jsonPath("$.claimNumber").value("0000004712"))
                .andExpect(jsonPath("$.totalAmount").value(150.00))
                .andExpect(jsonPath("$.debtorName").value("Grace Hopper"))
                .andExpect(jsonPath("$.createdOn").value("2026-02-01"));

        mockMvc.perform(get("/api/claims/0000004712"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimNumber").value("0000004712"))
                .andExpect(jsonPath("$.totalAmount").value(150.00))
                .andExpect(jsonPath("$.debtorName").value("Grace Hopper"))
                .andExpect(jsonPath("$.createdOn").value("2026-02-01"));

        mockMvc.perform(get("/api/claims/0000004712/payment-plan"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Payment plan not found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("No payment plan for claim 0000004712"));
    }

    @Test
    void createsAndReadsPaymentPlan() throws Exception {
        Claim claim = Claim.create(4713L, new BigDecimal("500.00"), "Grace Hopper",
                LocalDate.of(2026, 1, 15));
        claimRepository.saveAndFlush(claim);

        mockMvc.perform(post("/api/claims/0000004713/payment-plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"installmentAmount":80.00,"firstDueDate":"2026-04-01"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/claims/0000004713/payment-plan"))
                .andExpect(jsonPath("$.claimNumber").value("0000004713"))
                .andExpect(jsonPath("$.totalClaimAmount").value(500.00))
                .andExpect(jsonPath("$.amountPaid").value(0.00))
                .andExpect(jsonPath("$.outstandingBalance").value(500.00))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.installments.length()").value(7))
                .andExpect(jsonPath("$.installments[0].scheduledAmount").value(80.00))
                .andExpect(jsonPath("$.installments[6].scheduledAmount").value(20.00))
                .andExpect(jsonPath("$.installments[0].dueDate").value("2026-04-01"))
                .andExpect(jsonPath("$.installments[6].dueDate").value("2026-10-01"));

        mockMvc.perform(get("/api/claims/0000004713/payment-plan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.installments.length()").value(7))
                .andExpect(jsonPath("$.installments[6].scheduledAmount").value(20.00))
                .andExpect(content().string(containsString("\"totalClaimAmount\":500.00")));
    }

    @Test
    void recordsPartialAndOverpaymentsAndReturnsCredit() throws Exception {
        Claim claim = Claim.create(4714L, new BigDecimal("500.00"), "Grace Hopper",
                LocalDate.of(2026, 1, 15));
        PaymentPlan plan = claim.createPaymentPlan();
        plan.addInstallment(Installment.create(1, new BigDecimal("100.00"), LocalDate.of(2026, 4, 1)));
        plan.addInstallment(Installment.create(2, new BigDecimal("200.00"), LocalDate.of(2026, 5, 1)));
        plan.addInstallment(Installment.create(3, new BigDecimal("200.00"), LocalDate.of(2026, 6, 1)));
        plan.validateAgainstClaim();
        claimRepository.saveAndFlush(claim);

        mockMvc.perform(post("/api/claims/0000004714/payment-plan/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":80.00,"paidOn":"2026-03-14","reference":"first"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.payment.amount").value(80.00))
                .andExpect(jsonPath("$.payment.allocatedAmount").value(80.00))
                .andExpect(jsonPath("$.payment.unallocatedAmount").value(0.00))
                .andExpect(jsonPath("$.payment.allocations.length()").value(1))
                .andExpect(jsonPath("$.paymentPlan.amountPaid").value(80.00))
                .andExpect(jsonPath("$.paymentPlan.outstandingBalance").value(420.00))
                .andExpect(jsonPath("$.paymentPlan.creditAmount").value(0.00))
                .andExpect(jsonPath("$.paymentPlan.status").value("ACTIVE"));

        mockMvc.perform(post("/api/claims/0000004714/payment-plan/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":500.00,"paidOn":"2026-03-15","reference":"second"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.payment.amount").value(500.00))
                .andExpect(jsonPath("$.payment.allocatedAmount").value(420.00))
                .andExpect(jsonPath("$.payment.unallocatedAmount").value(80.00))
                .andExpect(jsonPath("$.payment.allocations.length()").value(3))
                .andExpect(jsonPath("$.payment.allocations[0].installmentNumber").value(1))
                .andExpect(jsonPath("$.payment.allocations[0].amount").value(20.00))
                .andExpect(jsonPath("$.payment.allocations[2].installmentNumber").value(3))
                .andExpect(jsonPath("$.payment.allocations[2].amount").value(200.00))
                .andExpect(jsonPath("$.paymentPlan.amountPaid").value(500.00))
                .andExpect(jsonPath("$.paymentPlan.outstandingBalance").value(0.00))
                .andExpect(jsonPath("$.paymentPlan.creditAmount").value(80.00))
                .andExpect(jsonPath("$.paymentPlan.status").value("COMPLETED"));

        mockMvc.perform(get("/api/claims/0000004714/payment-plan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditAmount").value(80.00))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.installments[0].amountPaid").value(100.00))
                .andExpect(jsonPath("$.installments[2].amountPaid").value(200.00));

        mockMvc.perform(get("/api/claims/0000004714/payment-plan/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].reference").value("first"))
                .andExpect(jsonPath("$[1].reference").value("second"))
                .andExpect(jsonPath("$[1].unallocatedAmount").value(80.00));
    }

    private void seedPaymentPlan() {
        Claim claim = Claim.create(4711L, new BigDecimal("300.00"), "Ada Lovelace",
                LocalDate.of(2026, 1, 1));
        PaymentPlan plan = claim.createPaymentPlan();
        plan.addInstallment(TestInstallments.paid(1, new BigDecimal("100.00"), new BigDecimal("100.00"),
                LocalDate.of(2026, 3, 1)));
        plan.addInstallment(Installment.create(2, new BigDecimal("200.00"), LocalDate.of(2026, 5, 1)));
        plan.validateAgainstClaim();
        claimRepository.saveAndFlush(claim);
    }
}
