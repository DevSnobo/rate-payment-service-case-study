package net.snobo.db.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Test fixture for building installments with an amount already paid. The production
 * {@code setAmountPaid} setter stays package-private, so this helper lives in the entity package.
 */
public final class TestInstallments {

    private TestInstallments() {
    }

    public static Installment paid(int installmentNumber, BigDecimal scheduledAmount, BigDecimal amountPaid,
                                   LocalDate dueDate) {
        Installment installment = Installment.create(installmentNumber, scheduledAmount, dueDate);
        installment.setAmountPaid(amountPaid);
        return installment;
    }
}
