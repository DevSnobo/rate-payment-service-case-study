package net.snobo.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Money rules shared by the domain entities. Amounts are always scale 2; values that
 * would need rounding are rejected instead of silently rounded.
 */
public final class Money {

    public static final int SCALE = 2;

    private Money() {
    }

    public static BigDecimal normalize(BigDecimal amount, String fieldName) {
        if (amount == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        try {
            return amount.setScale(SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(fieldName + " must have at most 2 decimal places", e);
        }
    }

    public static BigDecimal requirePositive(BigDecimal amount, String fieldName) {
        BigDecimal value = normalize(amount, fieldName);
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return value;
    }

    public static BigDecimal requireNonNegative(BigDecimal amount, String fieldName) {
        BigDecimal value = normalize(amount, fieldName);
        if (value.signum() < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
        return value;
    }
}
