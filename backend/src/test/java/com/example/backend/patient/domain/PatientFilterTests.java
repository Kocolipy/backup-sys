package com.example.backend.patient.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The filters narrowing a Patient search: a gender, male or female, and a date range for the date
 * of birth and for the Last visit, each end optional and inclusive, and a from-date never after
 * its to-date.
 */
class PatientFilterTests {

    private static final LocalDate JAN_1 = LocalDate.of(1980, 1, 1);

    private static final LocalDate DEC_31 = LocalDate.of(1989, 12, 31);

    @Test
    void noFilterNarrowsNothing() {
        assertThat(PatientFilter.NONE).isEqualTo(
                new PatientFilter(null, new DateRange(null, null), new DateRange(null, null)));
    }

    @ParameterizedTest
    @CsvSource(nullValues = "-", value = {
        "1980-01-01, 1989-12-31",
        "1980-01-01, 1980-01-01",
        "1980-01-01, -",
        "-, 1989-12-31",
        "-, -"})
    void aRangeTakesEitherEndOrBothOrNeitherOneDayLongAtTheLeast(LocalDate from, LocalDate to) {
        assertThat(new DateRange(from, to)).extracting(DateRange::from, DateRange::to)
                .containsExactly(from, to);
    }

    @Test
    void aRangeWhoseFromDateIsAfterItsToDateIsRefused() {
        assertThatThrownBy(() -> new DateRange(DEC_31, JAN_1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A date range's from-date may not be after its to-date");
    }

    @Test
    void aRangeOneDayInvertedIsRefused() {
        assertThatThrownBy(() -> new DateRange(JAN_1.plusDays(1), JAN_1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @EnumSource(value = AdministrativeGender.class, names = {"MALE", "FEMALE"})
    void aFilterNarrowsByMaleOrFemale(AdministrativeGender gender) {
        assertThat(new PatientFilter(gender, DateRange.ANY, DateRange.ANY).gender())
                .isEqualTo(gender);
    }

    /** A Patient whose gender is other or unknown is found only with no gender filter. */
    @ParameterizedTest
    @EnumSource(value = AdministrativeGender.class, names = {"OTHER", "UNKNOWN"})
    void aFilterRefusesTheGendersItCannotNarrowBy(AdministrativeGender gender) {
        assertThatThrownBy(() -> new PatientFilter(gender, DateRange.ANY, DateRange.ANY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A gender filter is male or female");
    }

    @Test
    void aFilterKeepsItsRanges() {
        DateRange births = new DateRange(JAN_1, DEC_31);
        DateRange visits = new DateRange(null, LocalDate.of(2026, 6, 30));

        assertThat(new PatientFilter(null, births, visits))
                .extracting(PatientFilter::birthDate, PatientFilter::lastVisit)
                .containsExactly(births, visits);
    }

    @Test
    void aFilterNeedsBothRanges() {
        assertThatThrownBy(() -> new PatientFilter(null, null, DateRange.ANY))
                .isInstanceOf(NullPointerException.class).hasMessage("birthDate");
        assertThatThrownBy(() -> new PatientFilter(null, DateRange.ANY, null))
                .isInstanceOf(NullPointerException.class).hasMessage("lastVisit");
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
