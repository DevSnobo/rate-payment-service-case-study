package net.snobo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import net.snobo.db.entity.Claim;
import net.snobo.db.entity.Installment;
import net.snobo.db.entity.PaymentPlan;
import net.snobo.db.repository.ClaimRepository;
import net.snobo.rest.dto.CreatePaymentRequest;
import net.snobo.rest.dto.PaymentPlanResponse;
import net.snobo.rest.dto.RecordPaymentResponse;
import net.snobo.service.PaymentPlanService;
import net.snobo.service.PaymentService;

@SpringBootTest
@ActiveProfiles("test")
class PaymentConcurrencyTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final LocalDate PAID_ON = LocalDate.of(2026, 3, 14);

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private PaymentPlanService paymentPlanService;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void fixedClock() {
        given(clock.instant()).willReturn(Instant.parse("2026-03-15T10:00:00Z"));
        given(clock.getZone()).willReturn(BERLIN);
    }

    @Test
    void serializesTwoPaymentsAndDoesNotOverAllocate() throws Exception {
        Claim claim = Claim.create(4715L, new BigDecimal("100.00"), "Ada Lovelace",
                LocalDate.of(2026, 1, 15));
        PaymentPlan plan = claim.createPaymentPlan();
        plan.addInstallment(Installment.create(1, new BigDecimal("100.00"),
                LocalDate.of(2026, 4, 1)));
        plan.validateAgainstClaim();
        claimRepository.saveAndFlush(claim);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<RecordPaymentResponse> first = executor.submit(() -> recordAfterStart(ready, start));
            Future<RecordPaymentResponse> second = executor.submit(() -> recordAfterStart(ready, start));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            RecordPaymentResponse firstResponse = first.get(10, TimeUnit.SECONDS);
            RecordPaymentResponse secondResponse = second.get(10, TimeUnit.SECONDS);

            BigDecimal credit = firstResponse.payment().unallocatedAmount()
                    .add(secondResponse.payment().unallocatedAmount());
            assertThat(credit).isEqualByComparingTo("60.00");

            PaymentPlanResponse planResponse = paymentPlanService.getPaymentPlanForClaim(4715L);

            assertThat(planResponse.installments().get(0).amountPaid())
                    .isEqualByComparingTo("100.00");
            assertThat(planResponse.amountPaid()).isEqualByComparingTo("100.00");
            assertThat(planResponse.outstandingBalance()).isEqualByComparingTo("0.00");
            assertThat(planResponse.creditAmount()).isEqualByComparingTo("60.00");
        } finally {
            executor.shutdownNow();
        }
    }

    private RecordPaymentResponse recordAfterStart(CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        start.await();
        return paymentService.recordPayment(4715L,
                new CreatePaymentRequest(new BigDecimal("80.00"), PAID_ON, null));
    }
}
