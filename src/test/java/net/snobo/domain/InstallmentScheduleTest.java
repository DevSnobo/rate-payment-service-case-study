package net.snobo.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class InstallmentScheduleTest {

    private static final LocalDate FIRST_DUE_DATE = LocalDate.of(2026, 12, 1);

    @Test
    void countModeSplitsEvenly() {
        List<ScheduledInstallment> schedule = InstallmentSchedule.fromCount(
                new BigDecimal("500.00"), 5, FIRST_DUE_DATE);

        assertThat(schedule)
                .hasSize(5)
                .allSatisfy(installment ->
                assertThat(installment.amount()).isEqualByComparingTo("100.00"));
        assertThat(schedule).extracting(ScheduledInstallment::installmentNumber)
                .containsExactly(1, 2, 3, 4, 5);
        assertThat(sum(schedule)).isEqualByComparingTo("500.00");
    }

    @Test
    void countModePutsRemainderOnLastInstallment() {
        List<ScheduledInstallment> schedule = InstallmentSchedule.fromCount(
                new BigDecimal("500.00"), 9, FIRST_DUE_DATE);

        assertThat(schedule).hasSize(9);
        assertThat(schedule.subList(0, 8)).allSatisfy(installment ->
                assertThat(installment.amount()).isEqualByComparingTo("55.55"));
        assertThat(schedule.get(8).amount()).isEqualByComparingTo("55.60");
        assertThat(sum(schedule)).isEqualByComparingTo("500.00");
    }

    @Test
    void countModeRejectsZeroOrNegativeCount() {
        BigDecimal totalAmount = new BigDecimal("500.00");
        assertThatThrownBy(() -> InstallmentSchedule.fromCount(totalAmount, 0, FIRST_DUE_DATE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("installmentCount must be between 1 and 120");
    }

    @Test
    void countModeRejectsMoreThanMaximum() {
        BigDecimal totalAmount = new BigDecimal("500.00");
        assertThatThrownBy(() -> InstallmentSchedule.fromCount(totalAmount, 121, FIRST_DUE_DATE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("installmentCount must be between 1 and 120");
    }

    @Test
    void countModeRejectsCountThatMakesZeroRegularAmount() {
        BigDecimal totalAmount = new BigDecimal("0.09");
        assertThatThrownBy(() -> InstallmentSchedule.fromCount(totalAmount, 10, FIRST_DUE_DATE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("installmentCount is too high for the claim total");
    }

    @Test
    void amountModeCreatesFullInstallmentsAndFinishingInstallment() {
        List<ScheduledInstallment> schedule = InstallmentSchedule.fromAmount(
                new BigDecimal("500.00"), new BigDecimal("80.00"), FIRST_DUE_DATE);

        assertThat(schedule).hasSize(7);
        assertThat(schedule.subList(0, 6)).allSatisfy(installment ->
                assertThat(installment.amount()).isEqualByComparingTo("80.00"));
        assertThat(schedule.get(6).amount()).isEqualByComparingTo("20.00");
        assertThat(sum(schedule)).isEqualByComparingTo("500.00");
    }

    @Test
    void amountModeDoesNotAddFinishingInstallmentWhenItDividesEvenly() {
        List<ScheduledInstallment> schedule = InstallmentSchedule.fromAmount(
                new BigDecimal("500.00"), new BigDecimal("100.00"), FIRST_DUE_DATE);

        assertThat(schedule).hasSize(5)
                .allSatisfy(installment -> assertThat(installment.amount()).isEqualByComparingTo("100.00"));
    }

    @Test
    void amountModeGreaterThanTotalCreatesOneInstallment() {
        List<ScheduledInstallment> schedule = InstallmentSchedule.fromAmount(
                new BigDecimal("500.00"), new BigDecimal("600.00"), FIRST_DUE_DATE);

        assertThat(schedule).hasSize(1);
        assertThat(schedule.getFirst().amount()).isEqualByComparingTo("500.00");
    }

    @Test
    void amountModeCanLeaveATinyFinishingInstallment() {
        List<ScheduledInstallment> schedule = InstallmentSchedule.fromAmount(
                new BigDecimal("500.00"), new BigDecimal("499.99"), FIRST_DUE_DATE);

        assertThat(schedule).hasSize(2);
        assertThat(schedule.get(0).amount()).isEqualByComparingTo("499.99");
        assertThat(schedule.get(1).amount()).isEqualByComparingTo("0.01");
    }

    @Test
    void amountModeRejectsTooLowAmount() {
        BigDecimal totalAmount = new BigDecimal("500.00");
        BigDecimal installmentAmount = new BigDecimal("0.01");
        assertThatThrownBy(() -> InstallmentSchedule.fromAmount(totalAmount, installmentAmount, FIRST_DUE_DATE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("installmentAmount is too low: plan would exceed 120 installments");
    }

    @Test
    void amountModeRejectsValuesNeedingRounding() {
        BigDecimal totalAmount = new BigDecimal("500.00");
        BigDecimal installmentAmount = new BigDecimal("80.001");
        assertThatThrownBy(() -> InstallmentSchedule.fromAmount(totalAmount, installmentAmount, FIRST_DUE_DATE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("installmentAmount must have at most 2 decimal places");
    }

    @Test
    void dueDatesAreMonthlyAndMonthEndIsClamped() {
        List<ScheduledInstallment> schedule = InstallmentSchedule.fromCount(
                new BigDecimal("300.00"), 3, LocalDate.of(2026, 1, 31));

        assertThat(schedule).extracting(ScheduledInstallment::dueDate)
                .containsExactly(LocalDate.of(2026, 1, 31),
                        LocalDate.of(2026, 2, 28),
                        LocalDate.of(2026, 3, 31));
    }

    private static BigDecimal sum(List<ScheduledInstallment> schedule) {
        return schedule.stream()
                .map(ScheduledInstallment::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
