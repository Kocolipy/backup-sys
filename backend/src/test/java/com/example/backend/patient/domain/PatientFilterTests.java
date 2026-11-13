package com.example.backend.patient.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** The filter narrowing a Patient search: a gender, male or female. */
class PatientFilterTests {

    @Test
    void noFilterNarrowsNothing() {
        assertThat(PatientFilter.NONE.gender()).isNull();
    }

    @Test
    void aFilterWithNoGenderIsNoFilter() {
        assertThat(new PatientFilter(null)).isEqualTo(PatientFilter.NONE);
    }

    @ParameterizedTest
    @EnumSource(value = AdministrativeGender.class, names = {"MALE", "FEMALE"})
    void aFilterNarrowsByMaleOrFemale(AdministrativeGender gender) {
        assertThat(new PatientFilter(gender).gender())
                .isEqualTo(gender);
    }

    /** A Patient whose gender is other or unknown is found only with no gender filter. */
    @ParameterizedTest
    @EnumSource(value = AdministrativeGender.class, names = {"OTHER", "UNKNOWN"})
    void aFilterRefusesTheGendersItCannotNarrowBy(AdministrativeGender gender) {
        assertThatThrownBy(() -> new PatientFilter(gender))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A gender filter is male or female");
    }

    @ParameterizedTest
    @CsvSource({"male, MALE", "female, FEMALE"})
    void aGenderFilterIsSpelledAsFhirCodesIt(String code, AdministrativeGender gender) {
        assertThat(PatientFilter.genderOf(code)).isEqualTo(gender);
    }

    @ParameterizedTest
    @ValueSource(strings = {"other", "unknown", "Male", "MALE", "m", "x"})
    void aGenderFilterSpelledAnyOtherWayIsRefused(String code) {
        assertThatThrownBy(() -> PatientFilter.genderOf(code))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A gender filter is male or female");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  "})
    void noGenderFilterOrABlankOneIsNone(String code) {
        assertThat(PatientFilter.genderOf(code)).isNull();
    }
}
