package com.example.backend.patient.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.backend.patient.domain.PatientSort.Field;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * How a Patient search is ordered: by date of birth or by Last visit, one at a time, in either
 * direction — newest Last visit first when no column is named. The direction's spelling is
 * {@link SortOrderTests}'.
 */
class PatientSortTests {

    @Test
    void theDefaultOrderIsNewestLastVisitFirst() {
        assertThat(PatientSort.DEFAULT)
                .isEqualTo(new PatientSort(Field.LAST_VISIT, SortOrder.DESCENDING));
    }

    @Test
    void noColumnIsTheDefaultOrderWhateverTheDirection() {
        assertThat(PatientSort.of(null, SortOrder.ASCENDING)).isEqualTo(PatientSort.DEFAULT);
        assertThat(PatientSort.of(null, null)).isEqualTo(PatientSort.DEFAULT);
    }

    @Test
    void aColumnWithNoDirectionSortsAscending() {
        assertThat(PatientSort.of(Field.BIRTH_DATE, null))
                .isEqualTo(new PatientSort(Field.BIRTH_DATE, SortOrder.ASCENDING));
    }

    @ParameterizedTest
    @CsvSource({
        "BIRTH_DATE, ASCENDING", "BIRTH_DATE, DESCENDING",
        "LAST_VISIT, ASCENDING", "LAST_VISIT, DESCENDING"})
    void aColumnSortsInTheDirectionNamed(Field field, SortOrder order) {
        assertThat(PatientSort.of(field, order)).isEqualTo(new PatientSort(field, order));
    }

    @Test
    void aSortNeedsBothItsColumnAndItsDirection() {
        assertThatThrownBy(() -> new PatientSort(null, SortOrder.ASCENDING))
                .isInstanceOf(NullPointerException.class).hasMessage("field");
        assertThatThrownBy(() -> new PatientSort(Field.BIRTH_DATE, null))
                .isInstanceOf(NullPointerException.class).hasMessage("order");
    }

    @ParameterizedTest
    @CsvSource({"birthDate, BIRTH_DATE", "lastVisit, LAST_VISIT"})
    void aColumnIsSpelledAsTheSearchPagesFieldIs(String spelled, Field field) {
        assertThat(Field.of(spelled)).isEqualTo(field);
    }

    /** Only the two dates sort: the name, NRIC and gender do not, nor any other spelling. */
    @ParameterizedTest
    @ValueSource(strings = {"name", "nric", "gender", "birthdate", "BIRTH_DATE", "last-visit"})
    void anyOtherColumnIsRefused(String spelled) {
        assertThatThrownBy(() -> Field.of(spelled))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A Patient search sorts by birthDate or lastVisit");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  "})
    void noColumnOrABlankOneIsNone(String spelled) {
        assertThat(Field.of(spelled)).isNull();
    }
}
