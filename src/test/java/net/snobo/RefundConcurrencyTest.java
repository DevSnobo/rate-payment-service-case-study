package net.snobo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
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
import net.snobo.db.entity.Payment;
import net.snobo.db.entity.PaymentPlan;
import net.snobo.db.repository.ClaimRepository;
import net.snobo.rest.dto.CreateRefundRequest;
import net.snobo.rest.dto.PaymentPlanResponse;
import net.snobo.rest.dto.RecordRefundResponse;
import net.snobo.service.PaymentPlanService;
import net.snobo.service.RefundService;
import net.snobo.service.exception.InsufficientCreditException;

@SpringBootTest
@ActiveProfiles("test")
class RefundConcurrencyTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final LocalDate REFUNDED_ON = LocalDate.of(2026, 3, 20);

    @Autowired
    private RefundService refundService;

    @Autowired
    private PaymentPlanService paymentPlanService;

    @Autowired
    private ClaimRepository claimRepository;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void fixedClock() {
        given(clock.instant()).willReturn(Instant.parse("2026-03-25T10:00:00Z"));
        given(clock.getZone()).willReturn(BERLIN);
    }

    @Test
    void serializesRefundsAndDoesNotRefundTheSameCreditTwice() throws Exception {
        Claim claim = Claim.create(4717L, new BigDecimal("100.00"), "Ada Lovelace",
                LocalDate.of(2026, 1, 15));
        PaymentPlan plan = claim.createPaymentPlan();
        plan.addInstallment(Installment.create(1, new BigDecimal("100.00"),
                LocalDate.of(2026, 4, 1)));
        plan.validateAgainstClaim();
        Payment payment = Payment.create(new BigDecimal("150.00"), LocalDate.of(2026, 3, 14),
                null, Instant.parse("2026-03-15T10:00:00Z"));
        plan.addPayment(payment);
        plan.allocate(payment);
        claimRepository.saveAndFlush(claim);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<RecordRefundResponse> first = executor.submit(
                    () -> refundAfterStart(payment.getPaymentId(), ready, start));
            Future<RecordRefundResponse> second = executor.submit(
                    () -> refundAfterStart(payment.getPaymentId(), ready, start));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            int successes = 0;
            int failures = 0;
            for (Future<RecordRefundResponse> future : List.of(first, second)) {
                try {
                    future.get(10, TimeUnit.SECONDS);
                    successes++;
                } catch (ExecutionException exception) {
                    assertThat(exception.getCause()).isInstanceOf(InsufficientCreditException.class);
                    failures++;
                }
            }

            assertThat(successes).isEqualTo(1);
            assertThat(failures).isEqualTo(1);

            PaymentPlanResponse planResponse = paymentPlanService.getPaymentPlanForClaim(4717L);
            assertThat(planResponse.amountPaid()).isEqualByComparingTo("100.00");
            assertThat(planResponse.outstandingBalance()).isEqualByComparingTo("0.00");
            assertThat(planResponse.creditAmount()).isEqualByComparingTo("10.00");
            assertThat(planResponse.refundedAmount()).isEqualByComparingTo("40.00");
        } finally {
            executor.shutdownNow();
        }
    }

    private RecordRefundResponse refundAfterStart(String paymentId, CountDownLatch ready,
                                                  CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        return refundService.recordRefund(4717L, paymentId,
                new CreateRefundRequest(new BigDecimal("40.00"), REFUNDED_ON, null));
    }
}
