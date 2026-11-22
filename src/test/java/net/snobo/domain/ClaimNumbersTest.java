package net.snobo.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ClaimNumbersTest {

    @Test
    void formatsClaimNumberAsTenCharacters() {
        assertThat(ClaimNumbers.format(4711L)).isEqualTo("0000004711");
    }

    @Test
    void rejectsZero() {
        assertThatThrownBy(() -> ClaimNumbers.require(0L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ClaimNumbers.format(0L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsValueAboveMaximum() {
        assertThatThrownBy(() -> ClaimNumbers.require(10_000_000_000L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void formatsMaximumClaimNumber() {
        assertThat(ClaimNumbers.format(9_999_999_999L)).isEqualTo("9999999999");
    }
}
