package com.example.backend.patient.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** A sort's direction, as an FFI History reads it: spelled in full, exactly. */
class SortOrderTests {

    @ParameterizedTest
    @CsvSource({"ascending, ASCENDING", "descending, DESCENDING"})
    void aDirectionIsSpelledInFull(String spelled, SortOrder order) {
        assertThat(SortOrder.of(spelled)).isEqualTo(order);
    }

    @ParameterizedTest
    @ValueSource(strings = {"asc", "desc", "Ascending", "DESCENDING", "up"})
    void anyOtherDirectionIsRefused(String spelled) {
        assertThatThrownBy(() -> SortOrder.of(spelled))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A sort order is ascending or descending");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  "})
    void noDirectionOrABlankOneIsNone(String spelled) {
        assertThat(SortOrder.of(spelled)).isNull();
    }
}
