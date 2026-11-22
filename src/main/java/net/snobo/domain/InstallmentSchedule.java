package net.snobo.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a payment plan schedule from the claim total. All but the last
 * installment use the regular amount; the last installment gets the remainder.
 */
public final class InstallmentSchedule {

    public static final int MAX_INSTALLMENTS = 120;

    private InstallmentSchedule() {
    }

    public static List<ScheduledInstallment> fromCount(BigDecimal totalAmount, int installmentCount,
                                                       LocalDate firstDueDate) {
        BigDecimal total = Money.requirePositive(totalAmount, "totalAmount");
        requireFirstDueDate(firstDueDate);
        if (installmentCount < 1 || installmentCount > MAX_INSTALLMENTS) {
            throw new IllegalArgumentException("installmentCount must be between 1 and "
                    + MAX_INSTALLMENTS);
        }

        BigDecimal regularAmount = total.divide(BigDecimal.valueOf(installmentCount), 2,
                RoundingMode.DOWN);
        if (regularAmount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "installmentCount is too high for the claim total");
        }

        List<ScheduledInstallment> schedule = new ArrayList<>(installmentCount);
        for (int number = 1; number < installmentCount; number++) {
            schedule.add(new ScheduledInstallment(number, regularAmount,
                    dueDate(firstDueDate, number - 1)));
        }
        BigDecimal lastAmount = total
                .subtract(regularAmount.multiply(BigDecimal.valueOf(installmentCount - 1L)))
                .setScale(2, RoundingMode.UNNECESSARY);
        schedule.add(new ScheduledInstallment(installmentCount, lastAmount,
                dueDate(firstDueDate, installmentCount - 1)));
        return List.copyOf(schedule);
    }

    public static List<ScheduledInstallment> fromAmount(BigDecimal totalAmount,
                                                        BigDecimal installmentAmount,
                                                        LocalDate firstDueDate) {
        BigDecimal total = Money.requirePositive(totalAmount, "totalAmount");
        BigDecimal regularAmount = Money.requirePositive(installmentAmount, "installmentAmount");
        requireFirstDueDate(firstDueDate);

        BigDecimal fullCountDecimal = total.divide(regularAmount, 0, RoundingMode.DOWN);
        if (fullCountDecimal.compareTo(BigDecimal.valueOf(MAX_INSTALLMENTS)) > 0) {
            throw new IllegalArgumentException("installmentAmount is too low: plan would exceed "
                    + MAX_INSTALLMENTS + " installments");
        }
        int fullCount = fullCountDecimal.intValueExact();

        if (fullCount == 0) {
            return List.of(new ScheduledInstallment(1, total, firstDueDate));
        }

        BigDecimal remainder = total
                .subtract(regularAmount.multiply(BigDecimal.valueOf(fullCount)))
                .setScale(2, RoundingMode.UNNECESSARY);

        List<ScheduledInstallment> schedule = new ArrayList<>(fullCount + 1);
        for (int number = 1; number <= fullCount; number++) {
            schedule.add(new ScheduledInstallment(number, regularAmount,
                    dueDate(firstDueDate, number - 1)));
        }
        if (remainder.signum() > 0) {
            int number = fullCount + 1;
            if (number > MAX_INSTALLMENTS) {
                throw new IllegalArgumentException("installmentAmount is too low: plan would exceed "
                        + MAX_INSTALLMENTS + " installments");
            }
            schedule.add(new ScheduledInstallment(number, remainder,
                    dueDate(firstDueDate, number - 1)));
        }
        return List.copyOf(schedule);
    }

    private static LocalDate dueDate(LocalDate firstDueDate, int months) {
        return firstDueDate.plusMonths(months);
    }

    private static void requireFirstDueDate(LocalDate firstDueDate) {
        if (firstDueDate == null) {
            throw new IllegalArgumentException("firstDueDate is required");
        }
    }
}
