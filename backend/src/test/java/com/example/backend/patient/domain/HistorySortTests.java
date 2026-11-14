package com.example.backend.patient.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.backend.patient.domain.HistorySort.Field;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * How a history, an FFI History or a NUMC History, is ordered: by the Issue Date or the Expiry
 * Date, one at a time, in either direction — or, with no column named, in the patient source's
 * own order. The direction's spelling is {@link SortOrderTests}'.
 */
class HistorySortTests {

    @Test
    void theSourcesOrderNamesNoColumnAndNoDirection() {
        assertThat(Arrays.asList(HistorySort.SOURCE_ORDER.field(), HistorySort.SOURCE_ORDER.order()))
                .containsOnlyNulls();
    }

    @ParameterizedTest
    @NullSource
    @EnumSource(SortOrder.class)
    void noColumnIsTheSourcesOrderWhateverTheDirection(SortOrder order) {
        assertThat(HistorySort.of(null, order)).isEqualTo(HistorySort.SOURCE_ORDER);
    }

    @Test
    void aColumnWithNoDirectionSortsAscending() {
        assertThat(HistorySort.of(Field.EXPIRY_DATE, null))
                .isEqualTo(new HistorySort(Field.EXPIRY_DATE, SortOrder.ASCENDING));
    }

    @ParameterizedTest
    @CsvSource({
        "ISSUE_DATE, ASCENDING", "ISSUE_DATE, DESCENDING",
        "EXPIRY_DATE, ASCENDING", "EXPIRY_DATE, DESCENDING"})
    void aColumnSortsInTheDirectionNamed(Field field, SortOrder order) {
        assertThat(HistorySort.of(field, order)).extracting(HistorySort::field, HistorySort::order)
                .containsExactly(field, order);
    }

    /** A column with no direction, or a direction with no column, is no sort. */
    @Test
    void aSortNamesBothItsColumnAndItsDirectionOrNeither() {
        assertThatThrownBy(() -> new HistorySort(null, SortOrder.ASCENDING))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A sort names both its column and its direction, or neither");
        assertThatThrownBy(() -> new HistorySort(Field.ISSUE_DATE, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A sort names both its column and its direction, or neither");
    }

    @ParameterizedTest
    @CsvSource({"issueDate, ISSUE_DATE", "expiryDate, EXPIRY_DATE"})
    void aColumnIsSpelledAsTheRecordsFieldIs(String spelled, Field field) {
        assertThat(Field.of(spelled)).isEqualTo(field);
    }

    /** Only the two dates sort: no other column does, nor any other spelling of theirs. */
    @ParameterizedTest
    @ValueSource(strings = {"type", "outcome", "status", "issue-date", "ISSUE_DATE",
        "expirydate", "lastVisit"})
    void anyOtherColumnIsRefused(String spelled) {
        assertThatThrownBy(() -> Field.of(spelled))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A history sorts by issueDate or expiryDate");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  "})
    void noColumnOrABlankOneIsNone(String spelled) {
        assertThat(Field.of(spelled)).isNull();
    }
}
