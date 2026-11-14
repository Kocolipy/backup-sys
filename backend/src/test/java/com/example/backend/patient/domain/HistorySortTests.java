package com.example.backend.patient.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * How a history read through the patient source is ordered: by one of the date columns the
 * history declares, one at a time, in either direction — an FFI History or a NUMC History by the
 * Issue Date or the Expiry Date, an ARMC History by the Start Date or the End Date — or, with no
 * column named, in the patient source's own order. The direction's spelling is
 * {@link SortOrderTests}'.
 */
class HistorySortTests {

    /** A history declaring a date-time column beside a calendar date, as no history yet does. */
    private static final HistoryColumn<LocalDateTime> TAKEN_AT =
            new HistoryColumn<>("takenAt", "taken-at", DatePrecision.DATE_TIME);

    private static final List<HistoryColumn<?>> WITH_A_DATE_TIME = List.of(
            new HistoryColumn<>("shipped", "shipped", DatePrecision.CALENDAR_DATE), TAKEN_AT);

    /** Each history's declared columns, by its name in the cases below. */
    private static final Map<String, List<HistoryColumn<?>>> HISTORIES = Map.of(
            "FFI", FfiHistory.DATE_COLUMNS,
            "NUMC", NumcHistory.DATE_COLUMNS,
            "ARMC", ArmcHistory.DATE_COLUMNS);

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
        assertThat(HistorySort.of(FfiHistory.EXPIRY_DATE, null))
                .isEqualTo(new HistorySort(FfiHistory.EXPIRY_DATE, SortOrder.ASCENDING));
    }

    @Test
    void anArmcColumnWithNoDirectionSortsAscending() {
        assertThat(HistorySort.of(ArmcHistory.END_DATE, null))
                .isEqualTo(new HistorySort(ArmcHistory.END_DATE, SortOrder.ASCENDING));
    }

    @ParameterizedTest
    @CsvSource({
        "issueDate, ASCENDING", "issueDate, DESCENDING",
        "expiryDate, ASCENDING", "expiryDate, DESCENDING"})
    void aColumnSortsInTheDirectionNamed(String name, SortOrder order) {
        HistoryColumn<?> field = HistorySort.column(FfiHistory.DATE_COLUMNS, name);

        assertThat(HistorySort.of(field, order)).extracting(HistorySort::field, HistorySort::order)
                .containsExactly(field, order);
    }

    /** A column with no direction, or a direction with no column, is no sort. */
    @Test
    void aSortNamesBothItsColumnAndItsDirectionOrNeither() {
        assertThatThrownBy(() -> new HistorySort(null, SortOrder.ASCENDING))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A sort names both its column and its direction, or neither");
        assertThatThrownBy(() -> new HistorySort(FfiHistory.ISSUE_DATE, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A sort names both its column and its direction, or neither");
    }

    /** Each history's columns, spelled as its record on our API names the field. */
    @ParameterizedTest
    @CsvSource({
        "FFI, issueDate, issue-date", "FFI, expiryDate, expiry-date",
        "NUMC, issueDate, issue-date", "NUMC, expiryDate, expiry-date",
        "ARMC, startDate, start-date", "ARMC, endDate, end-date"})
    void aColumnIsSpelledAsTheRecordsFieldIs(String history, String spelled,
            String searchParameter) {
        assertThat(HistorySort.column(HISTORIES.get(history), spelled))
                .isEqualTo(new HistoryColumn<>(spelled, searchParameter,
                        DatePrecision.CALENDAR_DATE));
    }

    /** Only the two dates sort an FFI History: no other column does, nor any other spelling. */
    @ParameterizedTest
    @ValueSource(strings = {"type", "outcome", "status", "issue-date", "ISSUE_DATE",
        "expirydate", "lastVisit", "startDate", "endDate"})
    void anyOtherFfiColumnIsRefused(String spelled) {
        assertThatThrownBy(() -> HistorySort.column(FfiHistory.DATE_COLUMNS, spelled))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A history sorts by issueDate or expiryDate");
    }

    /** Only the two dates sort a NUMC History: no other column does, nor any other spelling. */
    @ParameterizedTest
    @ValueSource(strings = {"type", "outcome", "status", "issue-date", "ISSUE_DATE",
        "expirydate", "lastVisit", "startDate", "endDate"})
    void anyOtherNumcColumnIsRefused(String spelled) {
        assertThatThrownBy(() -> HistorySort.column(NumcHistory.DATE_COLUMNS, spelled))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A history sorts by issueDate or expiryDate");
    }

    /**
     * Only an ARMC Board's two dates sort an ARMC History: no other column does, an FFI's or a
     * NUMC Record's dates among them, nor any other spelling of theirs.
     */
    @ParameterizedTest
    @ValueSource(strings = {"issueDate", "expiryDate", "outcome", "boardType", "start-date",
        "START_DATE", "enddate", "medicalOfficer1"})
    void anyOtherArmcColumnIsRefused(String spelled) {
        assertThatThrownBy(() -> HistorySort.column(ArmcHistory.DATE_COLUMNS, spelled))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A history sorts by startDate or endDate");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  "})
    void noColumnOrABlankOneIsNone(String spelled) {
        assertThat(HISTORIES.values().stream()
                .map(columns -> HistorySort.column(columns, spelled)).toList())
                .containsOnlyNulls();
    }

    /** A date-time column a history declares sorts as any other column does. */
    @Test
    void aDeclaredDateTimeColumnSortsAsAnyOther() {
        assertThat(HistorySort.of(HistorySort.column(WITH_A_DATE_TIME, "takenAt"),
                SortOrder.DESCENDING))
                .isEqualTo(new HistorySort(TAKEN_AT, SortOrder.DESCENDING));
    }
}
