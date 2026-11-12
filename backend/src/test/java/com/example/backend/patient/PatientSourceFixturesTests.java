package com.example.backend.patient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
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

        assertThat(matches).hasSize(15).allSatisfy(match -> assertThat(FIXTURES)
                .filteredOn(fixture -> fixture.get("id").equals(match.get("id")))
                .containsExactly(match));
    }

    /** The fixtures with any name containing {@code tan}, ignoring case. */
    private static List<JsonNode> namedTan() {
        return FIXTURES.stream()
                .filter(fixture -> StreamSupport.stream(fixture.at("/name").spliterator(), false)
                        .anyMatch(name -> name.get("text").asString().toLowerCase().contains("tan")))
                .toList();
    }

    /** What a stub's matcher asks of {@code parameter}: its one value, or none for absent. */
    private static Optional<String> asked(PatientSourceStubs.SearchStub stub, String parameter) {
        return stub.query().optional(parameter).flatMap(matcher -> matcher.optional("equalTo"))
                .map(JsonNode::asString);
    }

    /** The {@code tan} searches with no filter: its two pages. */
    private static boolean unfilteredTan(PatientSourceStubs.SearchStub stub) {
        return asked(stub, "name:contains").equals(Optional.of("tan"))
                && FILTER_PARAMETERS.stream().allMatch(
                        parameter -> stub.query().at("/" + parameter + "/absent").asBoolean());
    }

    /**
     * The name search's two pages hold exactly the seven fixtures with any name containing
     * {@code tan}, ignoring case — Lee Mei Ling by her maiden name — so the search spans two pages
     * of five, and each page's total is seven.
     */
    @Test
    void theNameSearchPagesHoldEveryFixtureWithANameContainingTan() {
        List<String> named = namedTan().stream().map(fixture -> fixture.get("id").asString())
                .toList();
        List<JsonNode> pages = PatientSourceStubs.searchStubs().stream()
                .filter(PatientSourceFixturesTests::unfilteredTan)
                .map(PatientSourceStubs.SearchStub::bundle)
                .toList();

        assertThat(named).hasSize(7);
        assertThat(pages).extracting(bundle -> bundle.at("/entry").size(),
                        bundle -> bundle.at("/total").asInt())
                .containsExactlyInAnyOrder(tuple(5, 7), tuple(2, 7));
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
                        tuple("Bundle", "searchset", 7),
                        tuple("Bundle", "searchset", 3),
                        tuple("Bundle", "searchset", 4));
    }

    // ---- the sort and filter stubs -------------------------------------------------------

    /** The filters a search sends the source, each as its own search parameter. */
    private static final List<String> FILTER_PARAMETERS = List.of("gender");

    /**
     * Every canned search pins each filter it does not use as absent, and each it uses to its
     * exact values, so a search with a filter no stub names falls to the fallback rather than to
     * an unfiltered stub.
     */
    @Test
    void everyCannedSearchPinsEveryFilterItUsesOrNot() {
        assertThat(PatientSourceStubs.searchStubs()).filteredOn(stub -> !stub.fallback())
                .hasSize(6)
                .allSatisfy(stub -> assertThat(FILTER_PARAMETERS)
                        .as("each filter's matcher, absent or pinned")
                        .allMatch(parameter -> pinsOrAbsent(stub.query().path(parameter))));
    }

    private static boolean pinsOrAbsent(JsonNode matcher) {
        return matcher.path("absent").asBoolean(false) || matcher.has("equalTo")
                || matcher.has("hasExactly");
    }

    /** The values a stub's matcher pins {@code parameter} to: none when it is absent. */
    private static List<String> pinned(PatientSourceStubs.SearchStub stub, String parameter) {
        JsonNode matcher = stub.query().path(parameter);
        if (matcher.has("equalTo")) {
            return List.of(matcher.get("equalTo").asString());
        }
        return StreamSupport.stream(matcher.path("hasExactly").spliterator(), false)
                .map(value -> value.get("equalTo").asString())
                .toList();
    }

    /**
     * Every canned {@code tan} search answers exactly the fixtures its gender filter selects from
     * the seven with a name containing {@code tan}, in the mock's one stable order — newest Last
     * visit first, ties by id, the fixture with none last — paged by its offset, five to a page,
     * with the total it selects: the source's behaviour, as /docs/domain/patient.md documents it.
     */
    @Test
    void everyCannedTanSearchAnswersTheFixturesItsFilterSelectsInTheMocksOrder() {
        List<PatientSourceStubs.SearchStub> canned = PatientSourceStubs.searchStubs().stream()
                .filter(stub -> asked(stub, "name:contains").equals(Optional.of("tan")))
                .toList();

        assertThat(canned).hasSize(4).allSatisfy(stub -> {
            List<String> gender = pinned(stub, "gender");
            List<String> selected = namedTan().stream()
                    .filter(fixture -> gender.isEmpty()
                            || gender.contains(fixture.get("gender").asString()))
                    .sorted(Comparator.comparing(
                                    (JsonNode fixture) -> lastVisit(fixture).orElse(null),
                                    Comparator.nullsLast(Comparator.<String>reverseOrder()))
                            .thenComparing(fixture -> fixture.get("id").asString()))
                    .map(fixture -> fixture.get("id").asString())
                    .toList();
            int offset = Integer.parseInt(asked(stub, "_offset").orElseThrow());

            assertThat(List.of(stub.bundle().at("/total").asInt(),
                    StreamSupport.stream(stub.bundle().at("/entry").spliterator(), false)
                            .map(entry -> entry.at("/resource/id").asString()).toList()))
                    .containsExactly(selected.size(),
                            selected.subList(offset, Math.min(offset + 5, selected.size())));
        });
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

    // ---- the FFI Histories ------------------------------------------------------------------

    private static List<String> fixtureIds() {
        return FIXTURES.stream().map(fixture -> fixture.get("id").asString()).toList();
    }

    /** The FFIs a stub's searchset holds, in its order. */
    private static List<JsonNode> ffisOf(PatientSourceStubs.FfiStub stub) {
        return StreamSupport.stream(stub.bundle().at("/entry").spliterator(), false)
                .map(entry -> entry.get("resource"))
                .toList();
    }

    /** The FFIs of every page of {@code patient}'s FFI History, page after page. */
    private static List<JsonNode> ffisOf(String patient) {
        return PatientSourceStubs.ffiHistories().stream()
                .filter(stub -> stub.path().equals("/Patient/" + patient + "/ffi"))
                .sorted(Comparator.comparing(stub -> stub.query().at("/_offset/equalTo")
                        .asInt()))
                .flatMap(stub -> ffisOf(stub).stream())
                .toList();
    }

    /**
     * Two fixtures have FFIs, each page its own stub — Tan Wei Ming's one page of four and Tan
     * Hui Min's two of seven — and a fallback answers every fixture's other read with none.
     */
    @Test
    void twoFixturesHaveAnFfiHistoryAndTheFallbackServesEveryFixture() {
        List<String> exact = PatientSourceStubs.ffiHistories().stream()
                .filter(stub -> !stub.path().contains("("))
                .map(stub -> stub.path() + " " + stub.query().at("/_offset/equalTo").asString()
                        + " of " + stub.bundle().at("/total").asInt())
                .toList();
        String fallback = PatientSourceStubs.ffiHistories().stream()
                .map(PatientSourceStubs.FfiStub::path)
                .filter(path -> path.contains("("))
                .findFirst().orElseThrow();

        assertThat(exact).containsExactlyInAnyOrder(
                "/Patient/eq081-VQEgP8drUUqCWzHfw3/ffi 0 of 4",
                "/Patient/dev-patient-05/ffi 0 of 7",
                "/Patient/dev-patient-05/ffi 5 of 7");
        assertThat(fixtureIds()).allSatisfy(id ->
                assertThat("/Patient/" + id + "/ffi").matches(fallback));
        assertThat("/Patient/fault-ffi-no-id/ffi").doesNotMatch(fallback);
    }

    /** Each page holds no more than five FFIs, and a history's pages hold its total. */
    @Test
    void everyFfiHistoryPageHoldsAtMostFiveAndItsPagesHoldTheirTotal() {
        assertThat(PatientSourceStubs.ffiHistories())
                .allSatisfy(stub -> assertThat(ffisOf(stub)).hasSizeLessThanOrEqualTo(5));
        assertThat(List.of(ffisOf("eq081-VQEgP8drUUqCWzHfw3").size(),
                ffisOf("dev-patient-05").size())).containsExactly(4, 7);
    }

    /** Every code of every closed set an FFI has, as the five sets spell them on the wire. */
    private static final Map<String, Set<String>> CLOSED_SETS = Map.of(
            "type", Set.of("overseas-exercises", "cat-a-ocs-scs", "ippt", "dental",
                    "ord-release-from-service", "vocational"),
            "outcome", Set.of("fit", "request-waiver", "unfit"),
            "higherAuthority", Set.of("camo", "carmo", "cnmo", "comd-mmi", "dis"),
            "submittingMc", Set.of("nee-soon-camp", "kranji-camp", "hendon-camp",
                    "mandai-hill-camp"),
            "status", Set.of("active", "inactive"));

    /**
     * Tan Hui Min's history, over its two pages, holds every code of every closed set — every
     * Outcome, Higher Authority and Status among them — so a page reading it shows each label.
     */
    @Test
    void theTwoPageHistoryCoversEveryCodeOfEveryClosedSet() {
        List<JsonNode> ffis = ffisOf("dev-patient-05");

        CLOSED_SETS.forEach((field, codes) -> assertThat(ffis)
                .extracting(ffi -> ffi.get(field).asString())
                .as(field)
                .containsOnlyElementsOf(codes)
                .containsAll(codes));
    }

    /**
     * Some of the FFIs past their Expiry Date are still Active: Status is the source's own, never
     * derived from the date, and the fixtures show it.
     */
    @Test
    void anFfiPastItsExpiryDateMayStillBeActive() {
        assertThat(ffisOf("dev-patient-05"))
                .filteredOn(ffi -> ffi.get("status").asString().equals("active"))
                .extracting(ffi -> ffi.get("expiryDate").asString())
                .anyMatch(expiry -> expiry.compareTo("2026-01-01") < 0);
    }

    /** Every FFI's id is in FHIR's id grammar and no other FFI's. */
    @Test
    void everyFfiIdIsAFhirIdOfItsOwn() {
        List<String> ids = PatientSourceStubs.ffiHistories().stream()
                .flatMap(stub -> ffisOf(stub).stream())
                .map(ffi -> ffi.get("id").asString())
                .toList();

        assertThat(ids).hasSize(11).doesNotHaveDuplicates()
                .allSatisfy(id -> assertThat(id).matches("[A-Za-z0-9\\-.]{1,64}"));
    }

    /**
     * The FFI fault stubs, each by the failure it serves under a well-formed patient id no fixture
     * uses, so it never shadows a fixture's history and the read reaches the source.
     */
    @Test
    void theFfiFaultStubsServeEachFailureUnderAPatientIdOfItsOwn() {
        assertThat(PatientSourceStubs.ffiFaults())
                .allSatisfy(fault -> assertThat(fault.id())
                        .isEqualTo("fault-ffi-" + fault.fault())
                        .matches("[A-Za-z0-9\\-.]{1,64}")
                        .isNotIn(fixtureIds()))
                .extracting(PatientSourceStubs.FaultStub::fault)
                .containsExactlyInAnyOrder("unreachable", "slow", "server-error",
                        "unexpected-status", "not-a-searchset", "no-total", "no-id",
                        "invalid-id", "no-outcome", "unknown-higher-authority",
                        "expiry-date-not-to-the-day");
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
