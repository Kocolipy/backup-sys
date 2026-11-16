package com.example.backend.patient.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * A date column a history declares names itself on our API, the patient source's search
 * parameter it is sent as, and its precision — each required, so a declaration missing one fails
 * where it is made rather than as a read is sent.
 */
class HistoryColumnTests {

    @Test
    void aColumnWithNoNameIsRefused() {
        assertThatThrownBy(() -> new HistoryColumn<>(null, "taken-at", DatePrecision.DATE_TIME))
                .isInstanceOf(NullPointerException.class).hasMessage("name");
    }

    @Test
    void aColumnWithNoSearchParameterIsRefused() {
        assertThatThrownBy(() -> new HistoryColumn<>("takenAt", null, DatePrecision.DATE_TIME))
                .isInstanceOf(NullPointerException.class).hasMessage("searchParameter");
    }

    @Test
    void aColumnWithNoPrecisionIsRefused() {
        assertThatThrownBy(() -> new HistoryColumn<>("takenAt", "taken-at", null))
                .isInstanceOf(NullPointerException.class).hasMessage("precision");
    }
}
