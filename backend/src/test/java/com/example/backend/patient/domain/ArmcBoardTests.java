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

/**
 * An ARMC Board as the domain holds it (/docs/domain/armc.md; ADR 0020): each element as the
 * patient source sent it — present, missing or invalid — none refused, naming the elements whose
 * format was wrong, printed by its id alone; and a page of ARMC History counting no fewer than
 * none. How a field is classified as it is read is {@code ReceivedFieldsTests}'.
 */
class ArmcBoardTests {

    private static final LocalDate STARTED = LocalDate.of(2024, 1, 1);

    private static final LocalDate ENDED = LocalDate.of(2024, 2, 10);

    private static ArmcBoard fit(String id) {
        return new ArmcBoard(SourceField.present(id), SourceField.present("aces"),
                SourceField.present("fit"), SourceField.present(STARTED),
                SourceField.present(ENDED), SourceField.present("Medical Officer 1"),
                SourceField.present("Medical Officer 2"));
    }

    /**
     * The source is the truth: a board whose id is outside FHIR's grammar, whose codes are no
     * members of their closed sets, which ends before it starts, or whose elements are missing or
     * invalid, is a board all the same.
     */
    @Test
    void noBoardIsRefusedForWhatItHolds() {
        ArmcBoard board = new ArmcBoard(SourceField.present("armc tan/1"),
                SourceField.present("aircrew"), SourceField.present("waiver-granted"),
                SourceField.present(STARTED), SourceField.present(STARTED.minusDays(1)),
                SourceField.missing(), SourceField.invalid());

        assertThat(Arrays.<Object>asList(board.id(), board.boardType(), board.outcome(),
                board.startDate(), board.endDate(), board.medicalOfficer1(),
                board.medicalOfficer2()))
                .containsExactly(SourceField.present("armc tan/1"),
                        SourceField.present("aircrew"), SourceField.present("waiver-granted"),
                        SourceField.present(LocalDate.of(2024, 1, 1)),
                        SourceField.present(LocalDate.of(2023, 12, 31)),
                        SourceField.missing(), SourceField.invalid());
    }

    /** Each element is held as some field, even one the source never sent: none is {@code null}. */
    static Stream<Arguments> eachElementLeftOut() {
        String[] names = {"id", "boardType", "outcome", "startDate", "endDate",
            "medicalOfficer1", "medicalOfficer2"};
        List<Arguments> cases = new ArrayList<>();
        for (int element = 0; element < names.length; element++) {
            SourceField<?>[] fields = new SourceField<?>[names.length];
            Arrays.fill(fields, SourceField.missing());
            fields[element] = null;
            cases.add(Arguments.of(names[element], fields));
        }
        return cases.stream();
    }

    @ParameterizedTest
    @MethodSource("eachElementLeftOut")
    @SuppressWarnings("unchecked")
    void aBoardHoldsEachElementAsAField(String element, SourceField<?>[] fields) {
        assertThatThrownBy(() -> new ArmcBoard((SourceField<String>) fields[0],
                (SourceField<String>) fields[1], (SourceField<String>) fields[2],
                (SourceField<LocalDate>) fields[3], (SourceField<LocalDate>) fields[4],
                (SourceField<String>) fields[5], (SourceField<String>) fields[6]))
                .isInstanceOf(NullPointerException.class)
                .hasMessage(element);
    }

    /** A board names the elements whose format was wrong, as the API spells them, in its order. */
    @Test
    void aBoardNamesItsInvalidElementsInItsOrder() {
        ArmcBoard board = new ArmcBoard(SourceField.invalid(), SourceField.present("aces"),
                SourceField.invalid(), SourceField.missing(), SourceField.invalid(),
                SourceField.missing(), SourceField.invalid());

        assertThat(board.invalid()).containsExactly("id", "outcome", "endDate",
                "medicalOfficer2");
    }

    @Test
    void aBoardWithTheBoardTypeTheStartDateOrMedicalOfficer1InvalidNamesThem() {
        ArmcBoard board = new ArmcBoard(SourceField.missing(), SourceField.invalid(),
                SourceField.missing(), SourceField.invalid(), SourceField.missing(),
                SourceField.invalid(), SourceField.missing());

        assertThat(board.invalid()).containsExactly("boardType", "startDate",
                "medicalOfficer1");
    }

    @Test
    void aBoardMissingElementsButNoneInvalidNamesNone() {
        ArmcBoard board = new ArmcBoard(SourceField.missing(), SourceField.missing(),
                SourceField.missing(), SourceField.missing(), SourceField.missing(),
                SourceField.missing(), SourceField.missing());

        assertThat(board.invalid()).isEmpty();
    }

    /** An ARMC Board is the patient's Restricted data: printed, it names its id alone. */
    @Test
    void aBoardPrintsItsIdAlone() {
        assertThat(fit("armc-tan-wei-ming-1")).hasToString("ArmcBoard[armc-tan-wei-ming-1]");
    }

    @Test
    void aBoardWithNoIdPrintsNoneOfItsElements() {
        ArmcBoard board = new ArmcBoard(SourceField.missing(), SourceField.present("aces"),
                SourceField.present("fit"), SourceField.present(STARTED),
                SourceField.present(ENDED), SourceField.present("Medical Officer 1"),
                SourceField.present("Medical Officer 2"));

        assertThat(board).hasToString("ArmcBoard[no id]");
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
