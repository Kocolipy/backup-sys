package com.example.backend.patient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.JsonNode;

/**
 * The patient source's development fixtures, read from the stub files the local mock serves
 * ({@code patient-source-mock/mappings}): twelve dummy patients — four under ids borrowed from an
 * EHR vendor's public sandbox test patients, eight under development ids of the mock's own —
 * covering every gender code, each with an NRIC that cannot be a real person's
 * (/docs/domain/patient.md, "The patient source's fixtures"). Each fixture's values as the service
 * reads them are {@code PatientStoreContract}'s. Beside them, the fault stubs serve each way the
 * source fails, under ids of their own, and the search stubs answer the documented searches with
 * the fixtures themselves.
 */
class PatientSourceFixturesTests {

    private static final String NRIC_SYSTEM = "https://fhir.synapxe.sg/NamingSystem/nric";

    private static final List<JsonNode> FIXTURES = PatientSourceStubs.fixtures();

    /** Every fixture's NRIC: the value of its identifier under the NRIC NamingSystem. */
    private static List<String> nrics() {
        return FIXTURES.stream().map(fixture -> nric(fixture).orElseThrow()).toList();
    }

    /**
     * Every NRIC the stub files hold: each fixture's, and each fault stub's that carries one — a
     * fault stub's Patient is as dummy as a fixture's.
     */
    private static List<String> stubNrics() {
        return Stream.concat(nrics().stream(), PatientSourceStubs.faults().stream()
                        .map(fault -> nric(fault.response().at("/jsonBody")))
                        .flatMap(Optional::stream))
                .toList();
    }

    private static Optional<String> nric(JsonNode patient) {
        return StreamSupport.stream(patient.at("/identifier").spliterator(), false)
                .filter(identifier -> NRIC_SYSTEM.equals(identifier.get("system").asString()))
                .map(identifier -> identifier.get("value").asString())
                .findFirst();
    }

    /**
     * The twelve fixtures: four under the sandbox ids /docs/domain/patient.md lists, and eight
     * under the mock's own development ids.
     */
    @Test
    void theFixturesAreTwelvePatientsUnderTheIdsTheDomainDocLists() {
        assertThat(FIXTURES).extracting(fixture -> fixture.get("id").asString())
                .containsExactlyInAnyOrder(
                        "eq081-VQEgP8drUUqCWzHfw3",
                        "erXuFYUfucBZaryVksYEcMg3",
                        "e0w0LEDCYtfckT6N.CkJKCw3",
                        "egqBHVfQlt4Bw3XGXoxVxHg3",
                        "dev-patient-05",
                        "dev-patient-06",
                        "dev-patient-07",
                        "dev-patient-08",
                        "dev-patient-09",
                        "dev-patient-10",
                        "dev-patient-11",
                        "dev-patient-12");
    }

    @Test
    void theFixturesBetweenThemCarryEveryGenderCode() {
        assertThat(FIXTURES).extracting(fixture -> fixture.get("gender").asString())
                .containsOnly("male", "female", "other", "unknown")
                .contains("male", "female", "other", "unknown");
    }

    // ---- the Last visit and the search stubs ---------------------------------------------

    private static final String LAST_VISIT =
            "https://fhir.synapxe.sg/StructureDefinition/last-visit";

    private static Optional<String> lastVisit(JsonNode patient) {
        return StreamSupport.stream(patient.at("/extension").spliterator(), false)
                .filter(extension -> LAST_VISIT.equals(extension.get("url").asString()))
                .map(extension -> extension.get("valueDate").asString())
                .findFirst();
    }

    /** Every fixture has a Last visit, a date to the day, but one: Tan Boon Huat has none. */
    @Test
    void everyFixtureButOneHasALastVisitToTheDay() {
        assertThat(FIXTURES).filteredOn(fixture -> lastVisit(fixture).isEmpty())
                .extracting(fixture -> fixture.get("id").asString())
                .containsExactly("dev-patient-10");
        assertThat(FIXTURES).extracting(fixture -> lastVisit(fixture).orElse("2000-01-01"))
                .allSatisfy(date -> assertThat(date).matches("\\d{4}-\\d{2}-\\d{2}"));
    }

    /** Each search answers its matches as the fixtures themselves, exactly as read by id. */
    @Test
    void everySearchMatchIsTheFixtureUnderItsId() {
        List<JsonNode> matches = PatientSourceStubs.searches().stream()
                .flatMap(bundle -> StreamSupport.stream(bundle.at("/entry").spliterator(), false))
                .map(entry -> entry.get("resource"))
                .toList();

        assertThat(matches).hasSize(8).allSatisfy(match -> assertThat(FIXTURES)
                .filteredOn(fixture -> fixture.get("id").equals(match.get("id")))
                .containsExactly(match));
    }

    /**
     * The name search's two pages hold exactly the seven fixtures with any name containing
     * {@code tan}, ignoring case — Lee Mei Ling by her maiden name — so the search spans two pages
     * of five, and each page's total is seven.
     */
    @Test
    void theNameSearchPagesHoldEveryFixtureWithANameContainingTan() {
        List<String> named = FIXTURES.stream()
                .filter(fixture -> StreamSupport.stream(fixture.at("/name").spliterator(), false)
                        .anyMatch(name -> name.get("text").asString().toLowerCase().contains("tan")))
                .map(fixture -> fixture.get("id").asString())
                .toList();
        List<JsonNode> pages = PatientSourceStubs.searches().stream()
                .filter(bundle -> bundle.at("/total").asInt() == 7)
                .toList();

        assertThat(named).hasSize(7);
        assertThat(pages).extracting(bundle -> bundle.at("/entry").size())
                .containsExactlyInAnyOrder(5, 2);
        assertThat(pages.stream()
                .flatMap(bundle -> StreamSupport.stream(bundle.at("/entry").spliterator(), false))
                .map(entry -> entry.at("/resource/id").asString()))
                .containsExactlyInAnyOrderElementsOf(named);
    }

    /** Every search stub answers a searchset Bundle carrying its total, the fallback's none. */
    @Test
    void everySearchStubIsASearchsetWithATotal() {
        assertThat(PatientSourceStubs.searches())
                .extracting(bundle -> bundle.at("/resourceType").asString(),
                        bundle -> bundle.at("/type").asString(),
                        bundle -> bundle.at("/total").asInt())
                .containsExactlyInAnyOrder(
                        tuple("Bundle", "searchset", 0),
                        tuple("Bundle", "searchset", 0),
                        tuple("Bundle", "searchset", 1),
                        tuple("Bundle", "searchset", 7),
                        tuple("Bundle", "searchset", 7));
    }

    @Test
    void everyFixtureNricIsUnique() {
        assertThat(nrics()).hasSize(FIXTURES.size()).doesNotHaveDuplicates();
    }

    /** No two stubs, fixture or fault, share an NRIC. */
    @Test
    void everyStubNricIsUnique() {
        assertThat(stubNrics()).hasSize(FIXTURES.size() + 6).doesNotHaveDuplicates();
    }

    @Test
    void everyStubNricHasTheNricShape() {
        assertThat(stubNrics()).isNotEmpty()
                .allSatisfy(nric -> assertThat(nric).matches("[STFG][0-9]{7}[A-Z]"));
    }

    /**
     * The fault stubs, each by the failure it serves and the id it serves it under: a delay past
     * the read timeout, a body dribbled out past it, a {@code 5xx}, and one Patient per domain
     * rule it breaks.
     */
    @Test
    void theFaultStubsServeEachFailureUnderAnIdOfItsOwn() {
        assertThat(PatientSourceStubs.faults())
                .extracting(PatientSourceStubs.FaultStub::fault, PatientSourceStubs.FaultStub::id)
                .containsExactlyInAnyOrder(
                        tuple("slow", "fault-slow"),
                        tuple("slow-body", "fault-slow-body"),
                        tuple("server-error", "fault-server-error"),
                        tuple("no-nric", "fault-no-nric"),
                        tuple("no-official-name", "fault-no-official-name"),
                        tuple("no-name-text", "fault-no-name-text"),
                        tuple("no-gender", "fault-no-gender"),
                        tuple("no-birth-date", "fault-no-birth-date"));
    }

    /**
     * A fault stub's id is well-formed — so the read reaches the source rather than being the
     * {@code 400} — and no fixture's, so it never shadows a fixture.
     */
    @Test
    void everyFaultStubIdIsAWellFormedIdNoFixtureUses() {
        List<String> fixtureIds = FIXTURES.stream().map(fixture -> fixture.get("id").asString())
                .toList();
        assertThat(PatientSourceStubs.faults()).extracting(PatientSourceStubs.FaultStub::id)
                .allSatisfy(id -> assertThat(id).matches("[A-Za-z0-9\\-.]{1,64}")
                        .isNotIn(fixtureIds));
    }

    /** A Patient a fault stub serves is under the id it is served for, so only its rule breaks. */
    @Test
    void everyFaultStubPatientIsUnderItsOwnId() {
        assertThat(PatientSourceStubs.faults())
                .filteredOn(fault -> fault.response().at("/status").asInt() == 200)
                .hasSize(7)
                .allSatisfy(fault -> assertThat(fault.response().at("/jsonBody/id").asString())
                        .isEqualTo(fault.id()));
    }

    /**
     * Pins the check-letter algorithm the next test relies on to worked values, so the
     * algorithm itself cannot drift into one every NRIC fails.
     */
    @ParameterizedTest
    @CsvSource({
        "S1234567, D",
        "T1234567, J",
        "S9212345, F",
        "T0312345, B",
        "S7812345, A",
        "F1234567, N",
        "G1234567, X"})
    void theCheckLetterAlgorithmGivesTheValidLetter(String number, char valid) {
        assertThat(NricCheckLetter.of(number)).isEqualTo(valid);
    }

    /**
     * Every dummy NRIC, a fixture's or a fault stub's, carries a check letter the algorithm rejects, so none can be a real
     * person's, and a real-looking value cannot slip into the stub files later.
     */
    @Test
    void everyStubNricFailsTheCheckLetterAlgorithm() {
        assertThat(stubNrics()).isNotEmpty().allSatisfy(nric ->
                assertThat(nric.charAt(8)).isNotEqualTo(NricCheckLetter.of(nric.substring(0, 8))));
    }

    /**
     * The NRIC check letter: the seven digits weighted 2, 7, 6, 5, 4, 3, 2, plus 4 for a T or G
     * prefix, taken mod 11 to index the S/T letters J Z I H G F E D C B A or the F/G letters
     * X W U T R Q P N M L K. Test code only: production never validates an NRIC.
     */
    private static final class NricCheckLetter {

        private static final int[] WEIGHTS = {2, 7, 6, 5, 4, 3, 2};

        static char of(String prefixAndDigits) {
            char prefix = prefixAndDigits.charAt(0);
            int sum = prefix == 'T' || prefix == 'G' ? 4 : 0;
            for (int i = 0; i < WEIGHTS.length; i++) {
                sum += (prefixAndDigits.charAt(i + 1) - '0') * WEIGHTS[i];
            }
            String letters = prefix == 'S' || prefix == 'T' ? "JZIHGFEDCBA" : "XWUTRQPNMLK";
            return letters.charAt(sum % 11);
        }
    }
}
