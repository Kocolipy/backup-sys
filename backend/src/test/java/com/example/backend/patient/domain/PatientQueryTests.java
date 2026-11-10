package com.example.backend.patient.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A Patient search's one box: a full NRIC, in either case, is an exact NRIC search, and anything
 * else a name search of at least three characters. There is no partial NRIC search, and no text
 * of the query is ever quoted by a refusal or printed by {@code toString}.
 */
class PatientQueryTests {

    @ParameterizedTest
    @CsvSource({
        "S8512345A, S8512345A",
        "s8512345a, S8512345A",
        "' T0312345C ', T0312345C",
        "F1234567N, F1234567N",
        "G7654321x, G7654321X",
        "M1234567K, M1234567K"})
    void aFullNricIsAnNricSearchForItInUpperCase(String typed, String nric) {
        assertThat(PatientQuery.valueOf(typed))
                .asInstanceOf(type(PatientQuery.ByNric.class))
                .extracting(PatientQuery.ByNric::nric)
                .isEqualTo(nric);
    }

    @ParameterizedTest
    @CsvSource({
        "tan, tan",
        "'  Tan Hui  ', Tan Hui",
        "S851, S851",
        "S8512345, S8512345",
        "S85123456A, S85123456A",
        "A8512345A, A8512345A",
        "S851234AA, S851234AA",
        "1234567, 1234567"})
    void anythingButAFullNricIsANameSearchForItTrimmed(String typed, String name) {
        assertThat(PatientQuery.valueOf(typed))
                .asInstanceOf(type(PatientQuery.ByName.class))
                .extracting(PatientQuery.ByName::text)
                .isEqualTo(name);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "t", "ta", "  ta  ", "S1"})
    void aNameUnderThreeCharactersIsRefusedWithoutQuotingIt(String typed) {
        assertThatThrownBy(() -> PatientQuery.valueOf(typed))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A name search needs at least 3 characters");
    }

    @ParameterizedTest
    @ValueSource(strings = {"S8512345", "s8512345a", "A8512345A", "S8512345AA", " S8512345A"})
    void anNricSearchRefusesAnythingButAFullNricWithoutQuotingIt(String nric) {
        assertThatThrownBy(() -> new PatientQuery.ByNric(nric))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("An NRIC search needs a full NRIC");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ta", " tan", "tan "})
    void aNameSearchRefusesAnUntrimmedOrShortName(String name) {
        assertThatThrownBy(() -> new PatientQuery.ByName(name))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A name search needs at least 3 characters");
    }

    @Test
    void aQueryIsRequired() {
        assertThatThrownBy(() -> PatientQuery.valueOf(null))
                .isInstanceOf(NullPointerException.class).hasMessage("text");
    }

    @Test
    void aNameSearchsNameIsRequired() {
        assertThatThrownBy(() -> new PatientQuery.ByName(null))
                .isInstanceOf(NullPointerException.class).hasMessage("text");
    }

    @Test
    void anNricSearchsNricIsRequired() {
        assertThatThrownBy(() -> new PatientQuery.ByNric(null))
                .isInstanceOf(NullPointerException.class).hasMessage("nric");
    }

    /** A query is Restricted data: printed, it says only what kind of search it is. */
    @ParameterizedTest
    @CsvSource({"S8512345A, PatientQuery[NRIC]", "Tan Hui Min, PatientQuery[NAME]"})
    void aQueryPrintsItsKindAlone(String typed, String printed) {
        assertThat(PatientQuery.valueOf(typed)).hasToString(printed);
    }
}
