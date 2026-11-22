package net.snobo.service.exception;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class InsufficientCreditException extends RuntimeException {

    public InsufficientCreditException(BigDecimal availableCredit) {
        super("Refund exceeds available credit "
                + availableCredit.setScale(2, RoundingMode.UNNECESSARY));
    }
}
