package net.snobo.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void normalizesFewerDecimalsToScaleTwo() {
        assertThat(Money.normalize(new BigDecimal("100.0"), "amount")).isEqualByComparingTo("100.00");
        assertThat(Money.normalize(new BigDecimal("100.0"), "amount").scale()).isEqualTo(2);
    }

    @Test
    void rejectsValuesThatNeedRounding() {
        assertThatThrownBy(() -> Money.normalize(new BigDecimal("100.005"), "amount"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> Money.normalize(null, "amount"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requirePositiveRejectsZeroAndNegativeValues() {
        assertThatThrownBy(() -> Money.requirePositive(BigDecimal.ZERO, "amount"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.requirePositive(new BigDecimal("-0.01"), "amount"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requireNonNegativeRejectsNegativeValues() {
        assertThatThrownBy(() -> Money.requireNonNegative(new BigDecimal("-0.01"), "amount"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
