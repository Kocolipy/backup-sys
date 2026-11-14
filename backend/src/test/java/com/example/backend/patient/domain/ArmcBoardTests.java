package com.example.backend.patient.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * An ARMC Board as the domain holds it (/docs/domain/armc.md): every element required but its End
 * Date, which is on or after its Start Date when there is one, both Medical Officers named, its
 * id in FHIR's id grammar, printed by its id alone; and a page of ARMC History counting no fewer
 * than none. How a closed set's member is found by its code is {@code SourceCodeTests}'.
 */
class ArmcBoardTests {

    private static final LocalDate STARTED = LocalDate.of(2024, 1, 1);

    private static final LocalDate ENDED = LocalDate.of(2024, 2, 10);

    private static ArmcBoard board(String id, LocalDate startDate, LocalDate endDate) {
        return new ArmcBoard(id, ArmcBoardType.ACES, ArmcOutcome.FIT, startDate, endDate,
                "Medical Officer 1", "Medical Officer 2");
    }

    private static ArmcBoard fit(String id) {
        return board(id, STARTED, ENDED);
    }

    @Test
    void aBoardHoldsEveryElementItWasGiven() {
        ArmcBoard board = fit("armc-tan-wei-ming-1");

        assertThat(Arrays.<Object>asList(board.id(), board.boardType(), board.outcome(),
                board.startDate(), board.endDate(), board.medicalOfficer1(),
                board.medicalOfficer2()))
                .containsExactly("armc-tan-wei-ming-1", ArmcBoardType.ACES, ArmcOutcome.FIT,
                        STARTED, ENDED, "Medical Officer 1", "Medical Officer 2");
    }

    /** An absent End Date is a board still open. */
    @Test
    void aBoardStillOpenHasNoEndDate() {
        assertThat(board("armc-open", STARTED, null).endDate()).isNull();
    }

    @Test
    void aBoardMayEndOnTheDayItStarts() {
        assertThat(board("armc-one-day", STARTED, STARTED).endDate()).isEqualTo(STARTED);
    }

    @Test
    void aBoardEndingBeforeItStartsIsRefused() {
        assertThatThrownBy(() -> board("armc-backwards", STARTED, STARTED.minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("An ARMC Board's End Date must not be before its Start Date");
    }

    /** The End Date rule, as a reader of the source asks it before building a board. */
    @Test
    void anEndDateBeforeTheStartDateIsTheOneThatEndsBeforeItStarts() {
        assertThat(List.of(
                ArmcBoard.endsBeforeItStarts(STARTED, STARTED.minusDays(1)),
                ArmcBoard.endsBeforeItStarts(STARTED, STARTED),
                ArmcBoard.endsBeforeItStarts(STARTED, ENDED),
                ArmcBoard.endsBeforeItStarts(STARTED, null)))
                .containsExactly(true, false, false, false);
    }

    /** An id outside FHIR's grammar is refused, quoting none of it. */
    @ParameterizedTest
    @ValueSource(strings = {"", "armc tan", "armc/tan", "armc_tan",
        "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"})
    void anIdOutsideTheGrammarIsRefused(String id) {
        assertThatThrownBy(() -> fit(id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("An ARMC Board's id must match the FHIR id grammar");
    }

    @Test
    void aBoardWithNoIdIsRefused() {
        assertThatThrownBy(() -> fit(null)).isInstanceOf(IllegalArgumentException.class);
    }

    /** Every element but the End Date is required: each one left out is refused, naming it. */
    static Stream<Arguments> eachRequiredElementLeftOut() {
        Object[] whole = {ArmcBoardType.ACES, ArmcOutcome.UNFIT, STARTED, "Medical Officer 3",
            "Medical Officer 4"};
        String[] names = {"boardType", "outcome", "startDate", "medicalOfficer1",
            "medicalOfficer2"};
        List<Arguments> cases = new ArrayList<>();
        for (int element = 0; element < whole.length; element++) {
            Object[] elements = whole.clone();
            elements[element] = null;
            cases.add(Arguments.of(names[element], elements));
        }
        return cases.stream();
    }

    @ParameterizedTest
    @MethodSource("eachRequiredElementLeftOut")
    void aBoardMissingARequiredElementIsRefused(String element, Object[] elements) {
        assertThatThrownBy(() -> new ArmcBoard("armc-1", (ArmcBoardType) elements[0],
                (ArmcOutcome) elements[1], (LocalDate) elements[2], null, (String) elements[3],
                (String) elements[4]))
                .isInstanceOf(NullPointerException.class)
                .hasMessage(element);
    }

    /** A Medical Officer is a name: a blank one names no one, and is refused. */
    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void aBlankMedicalOfficerIsRefused(int which) {
        assertThatThrownBy(() -> new ArmcBoard("armc-1", ArmcBoardType.ACES, ArmcOutcome.FIT,
                STARTED, null, which == 1 ? " " : "Medical Officer 1",
                which == 2 ? "" : "Medical Officer 2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("An ARMC Board's Medical Officer #" + which + " must be named");
    }

    /** An ARMC Board is the patient's Restricted data: printed, it names its id alone. */
    @Test
    void aBoardPrintsItsIdAlone() {
        assertThat(fit("armc-tan-wei-ming-1")).hasToString("ArmcBoard[armc-tan-wei-ming-1]");
    }

    @Test
    void aPageOfHistoryHoldsItsTotalAndItsBoardsInOrder() {
        List<ArmcBoard> boards = new ArrayList<>(List.of(fit("armc-2"), fit("armc-1")));
        ArmcHistory history = new ArmcHistory(7, boards);
        boards.clear();

        assertThat(List.of(history.totalResults(), history.boards()))
                .containsExactly(7, List.of(fit("armc-2"), fit("armc-1")));
    }

    @Test
    void aPageOfHistoryCountsNoFewerThanNone() {
        assertThat(new ArmcHistory(0, List.of()).totalResults()).isZero();
        assertThatThrownBy(() -> new ArmcHistory(-1, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aPageOfHistoryHoldsItsBoards() {
        assertThatThrownBy(() -> new ArmcHistory(0, null))
                .isInstanceOf(NullPointerException.class);
    }
}
