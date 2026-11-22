package net.snobo.domain;

import java.util.Locale;

/**
 * Claim numbers are positive longs with at most 10 digits. The API presents them as a
 * fixed-width, zero-padded string.
 */
public final class ClaimNumbers {

    public static final int WIDTH = 10;
    public static final long MAX = 9_999_999_999L;

    private ClaimNumbers() {
    }

    public static String format(long claimNumber) {
        return String.format(Locale.ROOT, "%0" + WIDTH + "d", require(claimNumber));
    }

    public static long require(long claimNumber) {
        if (claimNumber <= 0) {
            throw new IllegalArgumentException("claimNumber must be positive");
        }
        if (claimNumber > MAX) {
            throw new IllegalArgumentException("claimNumber must have at most 10 digits");
        }
        return claimNumber;
    }
}
