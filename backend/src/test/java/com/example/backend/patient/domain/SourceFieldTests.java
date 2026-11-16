package com.example.backend.patient.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static java.util.Map.entry;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A field of a record the patient source holds, as it was received (ADR 0020): there with its
 * value, missing, or invalid, holding a value only when it is there; and which of a record's
 * fields were invalid, by name.
 */
class SourceFieldTests {

    @Test
    void aPresentFieldHoldsItsValue() {
        SourceField<String> field = SourceField.present("submariner");

        assertThat(Arrays.asList(field.received(), field.value()))
                .containsExactly(SourceField.Received.PRESENT, "submariner");
    }

    @Test
    void aMissingOrAnInvalidFieldHoldsNoValue() {
        assertThat(Arrays.asList(SourceField.missing().received(), SourceField.missing().value(),
                SourceField.invalid().received(), SourceField.invalid().value()))
                .containsExactly(SourceField.Received.MISSING, null,
                        SourceField.Received.INVALID, null);
    }

    @Test
    void aPresentFieldHasAValue() {
        assertThatThrownBy(() -> SourceField.present(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A field is present exactly when it holds a value");
    }

    @Test
    void aFieldNotPresentHoldsNoValue() {
        assertThatThrownBy(() -> new SourceField<>(SourceField.Received.INVALID, "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A field is present exactly when it holds a value");
    }

    @Test
    void aFieldIsReceivedOneWayOrAnother() {
        assertThatThrownBy(() -> new SourceField<>(null, "x"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("received");
    }

    @Test
    void onlyAFieldReceivedWithAValueIsPresent() {
        assertThat(List.of(SourceField.present("fit").isPresent(),
                SourceField.missing().isPresent(), SourceField.invalid().isPresent()))
                .containsExactly(true, false, false);
    }

    @Test
    void aPresentFieldGivesItsValueOverTheOtherOne() {
        assertThat(SourceField.present("numc-1").orElse("no id")).isEqualTo("numc-1");
    }

    @Test
    void aFieldNotPresentGivesTheOtherValue() {
        assertThat(List.of(SourceField.<String>missing().orElse("no id"),
                SourceField.<String>invalid().orElse("no id")))
                .containsExactly("no id", "no id");
    }

    @Test
    void mappingAPresentFieldMapsItsValue() {
        assertThat(SourceField.present(LocalDate.of(2024, 1, 10)).map(LocalDate::toString))
                .isEqualTo(SourceField.present("2024-01-10"));
    }

    @Test
    void mappingAFieldNotPresentKeepsHowItWasReceived() {
        assertThat(Arrays.asList(SourceField.<LocalDate>missing().map(LocalDate::toString),
                SourceField.<LocalDate>invalid().map(LocalDate::toString)))
                .containsExactly(SourceField.missing(), SourceField.invalid());
    }

    @Test
    void theInvalidFieldsAreNamedInTheOrderGiven() {
        assertThat(SourceField.invalidAmong(
                entry("id", SourceField.invalid()),
                entry("type", SourceField.present("diving")),
                entry("outcome", SourceField.missing()),
                entry("issueDate", SourceField.invalid())))
                .containsExactly("id", "issueDate");
    }

    @Test
    void aRecordWithNoInvalidFieldNamesNone() {
        assertThat(SourceField.invalidAmong(entry("id", SourceField.present("numc-1")),
                entry("type", SourceField.missing()))).isEmpty();
    }
}
