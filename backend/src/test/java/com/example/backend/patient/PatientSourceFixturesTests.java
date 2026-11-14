package com.example.backend.patient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
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

    /** The FFIs, NUMC Records or ARMC Boards a history stub's searchset holds, in its order. */
    private static List<JsonNode> resourcesOf(PatientSourceStubs.HistoryStub stub) {
        return StreamSupport.stream(stub.bundle().at("/entry").spliterator(), false)
                .map(entry -> entry.get("resource"))
                .toList();
    }

    /** The filters an FFI History read sends the source, each as its own search parameter. */
    private static final List<String> FFI_FILTER_PARAMETERS = List.of("type", "outcome",
            "issue-date", "expiry-date", "higher-authority", "submitting-mc", "status");

    /** Whether {@code stub} is a fixture's own stub rather than the fallback answering several. */
    private static boolean canned(PatientSourceStubs.HistoryStub stub) {
        return !stub.path().contains("(");
    }

    /**
     * Whether {@code stub} reads its history unfiltered, in the source's own order: it pins the
     * sort and each of the history's {@code filters} absent.
     */
    private static boolean inTheSourcesOrder(PatientSourceStubs.HistoryStub stub,
            List<String> filters) {
        return canned(stub) && Stream.concat(Stream.of("_sort"), filters.stream())
                .allMatch(parameter -> stub.query().at("/" + parameter + "/absent").asBoolean());
    }

    /** The FFIs of every page of {@code patient}'s FFI History, page after page, as the source orders it. */
    private static List<JsonNode> ffisOf(String patient) {
        return PatientSourceStubs.ffiHistories().stream()
                .filter(stub -> inTheSourcesOrder(stub, FFI_FILTER_PARAMETERS))
                .filter(stub -> stub.path().equals("/Patient/" + patient + "/ffi"))
                .sorted(Comparator.comparing(stub -> stub.query().at("/_offset/equalTo")
                        .asInt()))
                .flatMap(stub -> resourcesOf(stub).stream())
                .toList();
    }

    /**
     * Two fixtures have FFIs, each page its own stub — Tan Wei Ming's one page of four and Tan
     * Hui Min's two of seven — and a fallback answers every fixture's other read with none.
     */
    @Test
    void twoFixturesHaveAnFfiHistoryAndTheFallbackServesEveryFixture() {
        List<String> exact = PatientSourceStubs.ffiHistories().stream()
                .filter(stub -> inTheSourcesOrder(stub, FFI_FILTER_PARAMETERS))
                .map(stub -> stub.path() + " " + stub.query().at("/_offset/equalTo").asString()
                        + " of " + stub.bundle().at("/total").asInt())
                .toList();
        String fallback = PatientSourceStubs.ffiHistories().stream()
                .map(PatientSourceStubs.HistoryStub::path)
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
                .allSatisfy(stub -> assertThat(resourcesOf(stub)).hasSizeLessThanOrEqualTo(5));
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
                    "mandai-hill-camp", "khatib-camp"),
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
                .filter(stub -> inTheSourcesOrder(stub, FFI_FILTER_PARAMETERS))
                .flatMap(stub -> resourcesOf(stub).stream())
                .map(ffi -> ffi.get("id").asString())
                .toList();

        assertThat(ids).hasSize(11).doesNotHaveDuplicates()
                .allSatisfy(id -> assertThat(id).matches("[A-Za-z0-9\\-.]{1,64}"));
    }

    // ---- the FFI sort and filter stubs ----------------------------------------------------

    /**
     * Every canned FFI History read pins the sort and each filter it does not use as absent, and
     * each it uses to its exact values, so a read with a filter or sort no stub names falls to the
     * fallback rather than to an unfiltered page.
     */
    @Test
    void everyCannedFfiReadPinsItsSortAndEveryFilterItUsesOrNot() {
        assertThat(PatientSourceStubs.ffiHistories()).filteredOn(PatientSourceFixturesTests::canned)
                .hasSize(17)
                .allSatisfy(stub -> assertThat(Stream.concat(Stream.of("_sort"),
                        FFI_FILTER_PARAMETERS.stream()))
                        .as("each matcher, absent or pinned")
                        .allMatch(parameter -> pinsOrAbsent(stub.query().path(parameter))));
    }

    /** The values a stub's matcher pins {@code parameter} to: none when it is absent. */
    private static List<String> pinned(PatientSourceStubs.HistoryStub stub, String parameter) {
        return pinnedIn(stub.query(), parameter);
    }

    /** The values the history stub's {@code query} pins {@code parameter} to: none when absent. */
    private static List<String> pinnedIn(JsonNode query, String parameter) {
        JsonNode matcher = query.path(parameter);
        if (matcher.has("equalTo")) {
            return List.of(matcher.get("equalTo").asString());
        }
        return StreamSupport.stream(matcher.path("hasExactly").spliterator(), false)
                .map(value -> value.get("equalTo").asString())
                .toList();
    }

    /** Whether {@code entry}'s {@code field} is one of the comma-joined codes {@code pinned} names. */
    private static boolean anyOf(JsonNode entry, String field, List<String> pinned) {
        return pinned.isEmpty()
                || List.of(pinned.getFirst().split(",")).contains(entry.get(field).asString());
    }

    /** The field of an entry each date search parameter a {@code _sort} names sorts on. */
    private static final Map<String, String> SORTED_FIELDS = Map.of(
            "issue-date", "issueDate", "expiry-date", "expiryDate",
            "start-date", "startDate", "end-date", "endDate");

    /**
     * {@code entries} — FFIs, NUMC Records or ARMC Boards — as the {@code _sort} {@code sorts}
     * pins orders them: by the date it names, {@code -} first for descending, ties by id where it
     * names {@code _id} after the date and otherwise left as the source keeps them; as they are
     * with no sort pinned.
     */
    private static List<JsonNode> sortedAs(List<JsonNode> entries, List<String> sorts) {
        if (sorts.isEmpty()) {
            return entries;
        }
        String sort = sorts.getFirst();
        String[] keys = sort.replaceFirst("^-", "").split(",");
        String field = SORTED_FIELDS.get(keys[0]);
        Comparator<String> direction = sort.startsWith("-")
                ? Comparator.<String>reverseOrder()
                : Comparator.<String>naturalOrder();
        Comparator<JsonNode> order =
                Comparator.comparing((JsonNode entry) -> entry.get(field).asString(), direction);
        return entries.stream()
                .sorted(keys.length > 1 && keys[1].equals("_id")
                        ? order.thenComparing(entry -> entry.get("id").asString())
                        : order)
                .toList();
    }

    /**
     * Whether {@code date} lies within every end {@code ends} pins — {@code ge} a from-date and
     * {@code le} a to-date, both inclusive — or any date at all with no end pinned.
     */
    private static boolean inRange(String date, List<String> ends) {
        if (ends.isEmpty()) {
            return true;
        }
        return date != null && ends.stream().allMatch(end -> end.startsWith("ge")
                ? date.compareTo(end.substring(2)) >= 0
                : end.startsWith("le") && date.compareTo(end.substring(2)) <= 0);
    }

    /**
     * Every sorted or filtered FFI read answers exactly the FFIs its filters select from the
     * patient's history in the source's own order, sorted as its {@code _sort} names — ties by
     * id — or left in that order with none, paged by its offset, five to a page, with the total it
     * selects: the source's behaviour, as /docs/domain/ffi.md documents it. A set of codes is any
     * of them, and a date range keeps both ends.
     */
    @Test
    void everySortedOrFilteredFfiReadAnswersTheFfisItsFiltersAndSortSelect() {
        List<PatientSourceStubs.HistoryStub> narrowed = PatientSourceStubs.ffiHistories().stream()
                .filter(PatientSourceFixturesTests::canned)
                .filter(stub -> !inTheSourcesOrder(stub, FFI_FILTER_PARAMETERS))
                .toList();

        assertThat(narrowed).hasSize(14).allSatisfy(stub -> {
            String patient = stub.path().replaceFirst("^/Patient/", "").replaceFirst("/ffi$", "");
            List<JsonNode> selected = ffisOf(patient).stream()
                    .filter(ffi -> anyOf(ffi, "type", pinned(stub, "type")))
                    .filter(ffi -> anyOf(ffi, "outcome", pinned(stub, "outcome")))
                    .filter(ffi -> inRange(ffi.get("issueDate").asString(),
                            pinned(stub, "issue-date")))
                    .filter(ffi -> inRange(ffi.get("expiryDate").asString(),
                            pinned(stub, "expiry-date")))
                    .filter(ffi -> anyOf(ffi, "higherAuthority", pinned(stub, "higher-authority")))
                    .filter(ffi -> anyOf(ffi, "submittingMc", pinned(stub, "submitting-mc")))
                    .filter(ffi -> anyOf(ffi, "status", pinned(stub, "status")))
                    .toList();
            List<String> ids = sortedAs(selected, pinned(stub, "_sort")).stream()
                    .map(ffi -> ffi.get("id").asString()).toList();
            int offset = stub.query().at("/_offset/equalTo").asInt();

            assertThat(List.of(stub.bundle().at("/total").asInt(),
                    resourcesOf(stub).stream().map(ffi -> ffi.get("id").asString()).toList()))
                    .containsExactly(ids.size(),
                            ids.subList(offset, Math.min(offset + 5, ids.size())));
        });
    }

    /**
     * The sort and filter stubs cover each direction of each sortable column, every filter, a
     * filtered history spanning two pages and one that matches nothing.
     */
    @Test
    void theHistorySortAndFilterStubsCoverEverySortAndFilter() {
        List<PatientSourceStubs.HistoryStub> canned = PatientSourceStubs.ffiHistories().stream()
                .filter(PatientSourceFixturesTests::canned)
                .toList();

        assertThat(List.of(
                canned.stream().flatMap(stub -> pinned(stub, "_sort").stream())
                        .collect(Collectors.toSet()),
                FFI_FILTER_PARAMETERS.stream()
                        .filter(parameter -> canned.stream()
                                .anyMatch(stub -> !pinned(stub, parameter).isEmpty()))
                        .toList(),
                canned.stream().filter(stub -> !inTheSourcesOrder(stub, FFI_FILTER_PARAMETERS))
                        .map(stub -> stub.bundle().at("/total").asInt())
                        .anyMatch(total -> total > 5),
                canned.stream().filter(stub -> !inTheSourcesOrder(stub, FFI_FILTER_PARAMETERS))
                        .anyMatch(stub -> stub.bundle().at("/total").asInt() == 0)))
                .containsExactly(
                        Set.of("issue-date,_id", "-issue-date,_id", "expiry-date,_id",
                                "-expiry-date,_id"),
                        FFI_FILTER_PARAMETERS, true, true);
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

    // ---- the NUMC Histories -----------------------------------------------------------------

    /** The filters a NUMC History read sends the source, each as its own search parameter. */
    private static final List<String> NUMC_FILTER_PARAMETERS =
            List.of("type", "outcome", "issue-date", "expiry-date");

    /** The NUMC Records of every page of {@code patient}'s NUMC History, page after page. */
    private static List<JsonNode> recordsOf(String patient) {
        return PatientSourceStubs.numcHistories().stream()
                .filter(stub -> inTheSourcesOrder(stub, NUMC_FILTER_PARAMETERS))
                .filter(stub -> stub.path().equals("/Patient/" + patient + "/numc"))
                .sorted(Comparator.comparing(stub -> stub.query().at("/_offset/equalTo")
                        .asInt()))
                .flatMap(stub -> resourcesOf(stub).stream())
                .toList();
    }

    /**
     * Two fixtures have NUMC Records, each page its own stub — Tan Wei Ming's one page of four and
     * Tan Hui Min's two of seven — and a fallback answers every fixture's other read with none.
     */
    @Test
    void twoFixturesHaveANumcHistoryAndTheFallbackServesEveryFixture() {
        List<String> exact = PatientSourceStubs.numcHistories().stream()
                .filter(stub -> inTheSourcesOrder(stub, NUMC_FILTER_PARAMETERS))
                .map(stub -> stub.path() + " " + stub.query().at("/_offset/equalTo").asString()
                        + " of " + stub.bundle().at("/total").asInt())
                .toList();
        String fallback = PatientSourceStubs.numcHistories().stream()
                .map(PatientSourceStubs.HistoryStub::path)
                .filter(path -> path.contains("("))
                .findFirst().orElseThrow();

        assertThat(exact).containsExactlyInAnyOrder(
                "/Patient/eq081-VQEgP8drUUqCWzHfw3/numc 0 of 4",
                "/Patient/dev-patient-05/numc 0 of 7",
                "/Patient/dev-patient-05/numc 5 of 7");
        assertThat(fixtureIds()).allSatisfy(id ->
                assertThat("/Patient/" + id + "/numc").matches(fallback));
        assertThat("/Patient/fault-numc-no-id/numc").doesNotMatch(fallback);
    }

    /**
     * Every canned read is matched on the whole page the backend asks for — five to a page, its
     * offset and an accurate total — and pins the sort and each filter it does not use as absent,
     * and each it uses to its exact values, so a read of any other page, or with a filter or sort
     * no stub names, falls to the fallback rather than to an unfiltered page.
     */
    @Test
    void everyCannedNumcReadPinsItsPageItsSortAndEveryFilterItUsesOrNot() {
        assertThat(PatientSourceStubs.numcHistories()).filteredOn(PatientSourceFixturesTests::canned)
                .hasSize(13)
                .allSatisfy(stub -> assertThat(List.of(
                        stub.query().at("/_count/equalTo").asString(),
                        stub.query().at("/_offset/equalTo").isString(),
                        stub.query().at("/_total/equalTo").asString()))
                        .containsExactly("5", true, "accurate"))
                .allSatisfy(stub -> assertThat(Stream.concat(Stream.of("_sort"),
                        NUMC_FILTER_PARAMETERS.stream()))
                        .as("each matcher, absent or pinned")
                        .allMatch(parameter -> pinsOrAbsent(stub.query().path(parameter))));
    }

    /** Each page holds no more than five NUMC Records, and a history's pages hold its total. */
    @Test
    void everyNumcHistoryPageHoldsAtMostFiveAndItsPagesHoldTheirTotal() {
        assertThat(PatientSourceStubs.numcHistories())
                .allSatisfy(stub -> assertThat(resourcesOf(stub)).hasSizeLessThanOrEqualTo(5));
        assertThat(List.of(recordsOf("eq081-VQEgP8drUUqCWzHfw3").size(),
                recordsOf("dev-patient-05").size())).containsExactly(4, 7);
    }

    /** Every code of both closed sets a NUMC Record has, as the two sets spell them on the wire. */
    private static final Map<String, Set<String>> NUMC_CLOSED_SETS = Map.of(
            "type", Set.of("hyperbaric-chamber", "submariner", "diving"),
            "outcome", Set.of("fit", "unfit", "waiver-granted", "waiver-rejected"));

    /**
     * Tan Hui Min's history, over its two pages, holds every code of both closed sets, so a page
     * reading it shows each label and each badge.
     */
    @Test
    void theTwoPageNumcHistoryCoversEveryCodeOfBothClosedSets() {
        List<JsonNode> records = recordsOf("dev-patient-05");

        NUMC_CLOSED_SETS.forEach((field, codes) -> assertThat(records)
                .extracting(record -> record.get(field).asString())
                .as(field)
                .containsOnlyElementsOf(codes)
                .containsAll(codes));
    }

    /** Every NUMC Record any stub answers is a {@code NUMC} of exactly its four elements and its id. */
    @Test
    void everyNumcRecordHoldsItsFourElementsAndItsIdAlone() {
        assertThat(PatientSourceStubs.numcHistories().stream()
                .flatMap(stub -> resourcesOf(stub).stream()))
                .isNotEmpty()
                .allSatisfy(record -> assertThat(List.of(record.get("resourceType").asString(),
                        record.propertyNames()))
                        .containsExactly("NUMC", Set.of("resourceType", "id", "type", "outcome",
                                "issueDate", "expiryDate")));
    }

    /** Every NUMC Record's id is in FHIR's id grammar and no other record's, an FFI's included. */
    @Test
    void everyNumcRecordIdIsAFhirIdOfItsOwn() {
        List<String> ids = PatientSourceStubs.numcHistories().stream()
                .filter(stub -> inTheSourcesOrder(stub, NUMC_FILTER_PARAMETERS))
                .flatMap(stub -> resourcesOf(stub).stream())
                .map(record -> record.get("id").asString())
                .toList();
        List<String> ffiIds = PatientSourceStubs.ffiHistories().stream()
                .flatMap(stub -> resourcesOf(stub).stream())
                .map(ffi -> ffi.get("id").asString())
                .toList();

        assertThat(ids).hasSize(11).doesNotHaveDuplicates()
                .doesNotContainAnyElementsOf(ffiIds)
                .allSatisfy(id -> assertThat(id).matches("[A-Za-z0-9\\-.]{1,64}"));
    }

    // ---- the NUMC sort and filter stubs ---------------------------------------------------

    /**
     * Every sorted or filtered NUMC read answers exactly the NUMC Records its filters select from
     * the patient's history in the source's own order, sorted as its {@code _sort} names — ties
     * by id — or left in that order with none, paged by its offset, five to a page, with the
     * total it selects: the source's behaviour, as /docs/domain/numc.md documents it. A set of
     * codes is any of them, and a date range keeps both ends — each record exactly as the
     * unfiltered history holds it.
     */
    @Test
    void everySortedOrFilteredNumcReadAnswersTheRecordsItsFiltersAndSortSelect() {
        List<PatientSourceStubs.HistoryStub> narrowed = PatientSourceStubs.numcHistories().stream()
                .filter(PatientSourceFixturesTests::canned)
                .filter(stub -> !inTheSourcesOrder(stub, NUMC_FILTER_PARAMETERS))
                .toList();

        assertThat(narrowed).hasSize(10).allSatisfy(stub -> {
            String patient = stub.path().replaceFirst("^/Patient/", "")
                    .replaceFirst("/numc$", "");
            JsonNode query = stub.query();
            List<JsonNode> selected = recordsOf(patient).stream()
                    .filter(record -> anyOf(record, "type", pinnedIn(query, "type")))
                    .filter(record -> anyOf(record, "outcome", pinnedIn(query, "outcome")))
                    .filter(record -> inRange(record.get("issueDate").asString(),
                            pinnedIn(query, "issue-date")))
                    .filter(record -> inRange(record.get("expiryDate").asString(),
                            pinnedIn(query, "expiry-date")))
                    .toList();
            List<JsonNode> expected = sortedAs(selected, pinnedIn(query, "_sort"));
            int offset = query.at("/_offset/equalTo").asInt();

            assertThat(List.of(stub.bundle().at("/total").asInt(), resourcesOf(stub)))
                    .containsExactly(expected.size(),
                            expected.subList(offset, Math.min(offset + 5, expected.size())));
        });
    }

    /**
     * The sort and filter stubs cover each direction of each sortable column, on Tan Wei Ming,
     * every filter, on Tan Hui Min, two filters combined, and a filter that matches nothing.
     */
    @Test
    void theNumcSortAndFilterStubsCoverEverySortAndFilter() {
        List<PatientSourceStubs.HistoryStub> narrowed = PatientSourceStubs.numcHistories().stream()
                .filter(PatientSourceFixturesTests::canned)
                .filter(stub -> !inTheSourcesOrder(stub, NUMC_FILTER_PARAMETERS))
                .toList();
        List<PatientSourceStubs.HistoryStub> sorted = narrowed.stream()
                .filter(stub -> !pinnedIn(stub.query(), "_sort").isEmpty())
                .toList();
        List<PatientSourceStubs.HistoryStub> filtered = narrowed.stream()
                .filter(stub -> pinnedIn(stub.query(), "_sort").isEmpty())
                .toList();

        assertThat(List.of(
                sorted.stream().map(stub -> stub.path() + " " + pinnedIn(stub.query(), "_sort"))
                        .collect(Collectors.toSet()),
                filtered.stream().map(PatientSourceStubs.HistoryStub::path)
                        .collect(Collectors.toSet()),
                NUMC_FILTER_PARAMETERS.stream()
                        .filter(parameter -> filtered.stream()
                                .anyMatch(stub -> !pinnedIn(stub.query(), parameter).isEmpty()))
                        .toList(),
                filtered.stream().anyMatch(stub -> NUMC_FILTER_PARAMETERS.stream()
                        .filter(parameter -> !pinnedIn(stub.query(), parameter).isEmpty())
                        .count() > 1),
                filtered.stream().anyMatch(stub -> stub.bundle().at("/total").asInt() == 0)))
                .containsExactly(
                        Set.of("/Patient/eq081-VQEgP8drUUqCWzHfw3/numc [issue-date,_id]",
                                "/Patient/eq081-VQEgP8drUUqCWzHfw3/numc [-issue-date,_id]",
                                "/Patient/eq081-VQEgP8drUUqCWzHfw3/numc [expiry-date,_id]",
                                "/Patient/eq081-VQEgP8drUUqCWzHfw3/numc [-expiry-date,_id]"),
                        Set.of("/Patient/dev-patient-05/numc"),
                        NUMC_FILTER_PARAMETERS, true, true);
    }

    /**
     * The NUMC fault stubs, each by the failure it serves under a well-formed patient id no
     * fixture uses, so it never shadows a fixture's history and the read reaches the source.
     */
    @Test
    void theNumcFaultStubsServeEachFailureUnderAPatientIdOfItsOwn() {
        assertThat(PatientSourceStubs.numcFaults())
                .allSatisfy(fault -> assertThat(fault.id())
                        .isEqualTo("fault-numc-" + fault.fault())
                        .matches("[A-Za-z0-9\\-.]{1,64}")
                        .isNotIn(fixtureIds()))
                .extracting(PatientSourceStubs.FaultStub::fault)
                .containsExactlyInAnyOrder("unreachable", "slow", "server-error",
                        "unexpected-status", "not-a-searchset", "no-total", "no-id",
                        "invalid-id", "no-outcome", "unknown-type", "unknown-outcome",
                        "expiry-date-not-to-the-day");
    }

    // ---- the ARMC Histories -----------------------------------------------------------------

    /** The filters an ARMC History read sends the source, each as its own search parameter. */
    private static final List<String> ARMC_FILTER_PARAMETERS =
            List.of("outcome", "start-date", "end-date");

    /** The ARMC Boards of every page of {@code patient}'s ARMC History, page after page. */
    private static List<JsonNode> boardsOf(String patient) {
        return PatientSourceStubs.armcHistories().stream()
                .filter(stub -> inTheSourcesOrder(stub, ARMC_FILTER_PARAMETERS))
                .filter(stub -> stub.path().equals("/Patient/" + patient + "/armc"))
                .sorted(Comparator.comparing(stub -> stub.query().at("/_offset/equalTo")
                        .asInt()))
                .flatMap(stub -> resourcesOf(stub).stream())
                .toList();
    }

    /** Every ARMC Board of every fixture's ARMC History, each once, as its pages hold it. */
    private static List<JsonNode> everyArmcBoard() {
        return PatientSourceStubs.armcHistories().stream()
                .filter(stub -> inTheSourcesOrder(stub, ARMC_FILTER_PARAMETERS))
                .flatMap(stub -> resourcesOf(stub).stream())
                .toList();
    }

    /**
     * Two fixtures have ARMC Boards, each page its own stub — Tan Wei Ming's one page of two and
     * Tan Hui Min's two of seven — and a fallback answers every fixture's other read with none.
     */
    @Test
    void twoFixturesHaveAnArmcHistoryAndTheFallbackServesEveryFixture() {
        List<String> exact = PatientSourceStubs.armcHistories().stream()
                .filter(stub -> inTheSourcesOrder(stub, ARMC_FILTER_PARAMETERS))
                .map(stub -> stub.path() + " " + stub.query().at("/_offset/equalTo").asString()
                        + " of " + stub.bundle().at("/total").asInt())
                .toList();
        String fallback = PatientSourceStubs.armcHistories().stream()
                .map(PatientSourceStubs.HistoryStub::path)
                .filter(path -> path.contains("("))
                .findFirst().orElseThrow();

        assertThat(exact).containsExactlyInAnyOrder(
                "/Patient/eq081-VQEgP8drUUqCWzHfw3/armc 0 of 2",
                "/Patient/dev-patient-05/armc 0 of 7",
                "/Patient/dev-patient-05/armc 5 of 7");
        assertThat(fixtureIds()).allSatisfy(id ->
                assertThat("/Patient/" + id + "/armc").matches(fallback));
        assertThat("/Patient/fault-armc-no-id/armc").doesNotMatch(fallback);
    }

    /**
     * Every canned read is matched on the whole page the backend asks for — five to a page, its
     * offset and an accurate total — and pins the sort and each filter it does not use as absent,
     * and each it uses to its exact values, so a read of any other page, or with a filter or sort
     * no stub names, falls to the fallback rather than to an unfiltered page.
     */
    @Test
    void everyCannedArmcReadPinsItsPageItsSortAndEveryFilterItUsesOrNot() {
        assertThat(PatientSourceStubs.armcHistories()).filteredOn(PatientSourceFixturesTests::canned)
                .hasSize(13)
                .allSatisfy(stub -> assertThat(List.of(
                        stub.query().at("/_count/equalTo").asString(),
                        stub.query().at("/_offset/equalTo").isString(),
                        stub.query().at("/_total/equalTo").asString()))
                        .containsExactly("5", true, "accurate"))
                .allSatisfy(stub -> assertThat(Stream.concat(Stream.of("_sort"),
                        ARMC_FILTER_PARAMETERS.stream()))
                        .as("each matcher, absent or pinned")
                        .allMatch(parameter -> pinsOrAbsent(stub.query().path(parameter))));
    }

    /** Each page holds no more than five ARMC Boards, and a history's pages hold its total. */
    @Test
    void everyArmcHistoryPageHoldsAtMostFiveAndItsPagesHoldTheirTotal() {
        assertThat(PatientSourceStubs.armcHistories())
                .allSatisfy(stub -> assertThat(resourcesOf(stub)).hasSizeLessThanOrEqualTo(5));
        assertThat(List.of(boardsOf("eq081-VQEgP8drUUqCWzHfw3").size(),
                boardsOf("dev-patient-05").size())).containsExactly(2, 7);
    }

    /** Every code of both closed sets an ARMC Board has, as the two sets spell them on the wire. */
    private static final Map<String, Set<String>> ARMC_CLOSED_SETS = Map.of(
            "boardType", Set.of("aces"),
            "outcome", Set.of("fit", "unfit"));

    /**
     * Tan Hui Min's history, over its two pages, holds every code of both closed sets, so a page
     * reading it shows each label and each badge.
     */
    @Test
    void theTwoPageArmcHistoryCoversEveryCodeOfBothClosedSets() {
        List<JsonNode> boards = boardsOf("dev-patient-05");

        ARMC_CLOSED_SETS.forEach((field, codes) -> assertThat(boards)
                .extracting(board -> board.get(field).asString())
                .as(field)
                .containsOnlyElementsOf(codes)
                .containsAll(codes));
    }

    /**
     * Every ARMC Board is an {@code ARMC} of exactly its six elements and its id, but a board
     * still open, which has no End Date: at least one fixture's board is open.
     */
    @Test
    void everyArmcBoardHoldsItsElementsAndItsIdAloneAndOneIsStillOpen() {
        assertThat(everyArmcBoard()).hasSize(9)
                .allSatisfy(PatientSourceFixturesTests::holdsItsElementsAlone)
                .filteredOn(board -> !board.has("endDate"))
                .extracting(board -> board.get("id").asString())
                .containsExactly("armc-tan-hui-min-1");
    }

    /** Every ARMC Board any stub answers, sorted or filtered too, holds its elements alone. */
    @Test
    void everyArmcBoardAnyStubAnswersHoldsItsElementsAlone() {
        assertThat(PatientSourceStubs.armcHistories().stream()
                .flatMap(stub -> resourcesOf(stub).stream()))
                .isNotEmpty()
                .allSatisfy(PatientSourceFixturesTests::holdsItsElementsAlone);
    }

    /** Whether {@code board} is an {@code ARMC} of exactly its elements and its id, the End Date optional. */
    private static void holdsItsElementsAlone(JsonNode board) {
        Set<String> closed = Set.of("resourceType", "id", "boardType", "outcome", "startDate",
                "endDate", "medicalOfficer1", "medicalOfficer2");
        Set<String> open = new HashSet<>(closed);
        open.remove("endDate");
        assertThat(List.<Object>of(board.get("resourceType").asString(),
                Set.copyOf(board.propertyNames())))
                .isIn(List.of("ARMC", closed), List.of("ARMC", open));
    }

    /** Every fixture's board ends on or after the day it starts, when it has ended. */
    @Test
    void everyArmcBoardEndsOnOrAfterItStarts() {
        assertThat(everyArmcBoard()).filteredOn(board -> board.has("endDate"))
                .allSatisfy(board -> assertThat(LocalDate.parse(board.get("endDate").asString()))
                        .isAfterOrEqualTo(LocalDate.parse(board.get("startDate").asString())));
    }

    /** Every ARMC Board's id is in FHIR's id grammar and no other record's, an FFI's or NUMC's. */
    @Test
    void everyArmcBoardIdIsAFhirIdOfItsOwn() {
        List<String> ids = everyArmcBoard().stream()
                .map(board -> board.get("id").asString())
                .toList();
        Stream<String> others = Stream.concat(
                PatientSourceStubs.ffiHistories().stream()
                        .flatMap(stub -> resourcesOf(stub).stream()),
                PatientSourceStubs.numcHistories().stream()
                        .flatMap(stub -> resourcesOf(stub).stream()))
                .map(record -> record.get("id").asString());

        assertThat(ids).hasSize(9).doesNotHaveDuplicates()
                .doesNotContainAnyElementsOf(others.toList())
                .allSatisfy(id -> assertThat(id).matches("[A-Za-z0-9\\-.]{1,64}"));
    }

    // ---- the ARMC sort and filter stubs ---------------------------------------------------

    /** {@code board}'s date {@code field}, or {@code null} for the End Date of a board still open. */
    private static String dateOf(JsonNode board, String field) {
        return board.has(field) ? board.get(field).asString() : null;
    }

    /**
     * Every sorted or filtered ARMC read answers exactly the ARMC Boards its filters select from
     * the patient's history in the source's own order — an Outcome is that one code, a date range
     * keeps both ends, and a board with no End Date is in no End Date range — sorted as its
     * {@code _sort} names, ties left as the source keeps them, or left in that order with none,
     * paged by its offset, five to a page, with the total it selects: the source's behaviour, as
     * /docs/domain/armc.md documents it. Each board is exactly as the unfiltered history holds
     * it.
     */
    @Test
    void everySortedOrFilteredArmcReadAnswersTheBoardsItsFiltersAndSortSelect() {
        List<PatientSourceStubs.HistoryStub> narrowed = PatientSourceStubs.armcHistories().stream()
                .filter(PatientSourceFixturesTests::canned)
                .filter(stub -> !inTheSourcesOrder(stub, ARMC_FILTER_PARAMETERS))
                .toList();

        assertThat(narrowed).hasSize(10).allSatisfy(stub -> {
            String patient = stub.path().replaceFirst("^/Patient/", "")
                    .replaceFirst("/armc$", "");
            JsonNode query = stub.query();
            List<JsonNode> selected = boardsOf(patient).stream()
                    .filter(board -> anyOf(board, "outcome", pinnedIn(query, "outcome")))
                    .filter(board -> inRange(dateOf(board, "startDate"),
                            pinnedIn(query, "start-date")))
                    .filter(board -> inRange(dateOf(board, "endDate"),
                            pinnedIn(query, "end-date")))
                    .toList();
            List<JsonNode> expected = sortedAs(selected, pinnedIn(query, "_sort"));
            int offset = query.at("/_offset/equalTo").asInt();

            assertThat(List.of(stub.bundle().at("/total").asInt(), resourcesOf(stub)))
                    .containsExactly(expected.size(),
                            expected.subList(offset, Math.min(offset + 5, expected.size())));
        });
    }

    /**
     * The sort and filter stubs cover each direction of each sortable column, on Tan Wei Ming,
     * each sent as the column alone; every filter, on Tan Hui Min, each Outcome among them; an
     * End Date range leaving out a board still open; two filters combined; and a filter that
     * matches nothing.
     */
    @Test
    void theArmcSortAndFilterStubsCoverEverySortAndFilter() {
        List<PatientSourceStubs.HistoryStub> narrowed = PatientSourceStubs.armcHistories().stream()
                .filter(PatientSourceFixturesTests::canned)
                .filter(stub -> !inTheSourcesOrder(stub, ARMC_FILTER_PARAMETERS))
                .toList();
        List<PatientSourceStubs.HistoryStub> sorted = narrowed.stream()
                .filter(stub -> !pinnedIn(stub.query(), "_sort").isEmpty())
                .toList();
        List<PatientSourceStubs.HistoryStub> filtered = narrowed.stream()
                .filter(stub -> pinnedIn(stub.query(), "_sort").isEmpty())
                .toList();

        assertThat(List.of(
                sorted.stream().map(stub -> stub.path() + " " + pinnedIn(stub.query(), "_sort"))
                        .collect(Collectors.toSet()),
                filtered.stream().map(PatientSourceStubs.HistoryStub::path)
                        .collect(Collectors.toSet()),
                ARMC_FILTER_PARAMETERS.stream()
                        .filter(parameter -> filtered.stream()
                                .anyMatch(stub -> !pinnedIn(stub.query(), parameter).isEmpty()))
                        .toList(),
                filtered.stream().flatMap(stub -> pinnedIn(stub.query(), "outcome").stream())
                        .collect(Collectors.toSet()),
                filtered.stream().anyMatch(stub -> !pinnedIn(stub.query(), "end-date").isEmpty()
                        && boardsOf("dev-patient-05").stream()
                                .anyMatch(board -> !board.has("endDate")
                                        && !resourcesOf(stub).contains(board))),
                filtered.stream().anyMatch(stub -> ARMC_FILTER_PARAMETERS.stream()
                        .filter(parameter -> !pinnedIn(stub.query(), parameter).isEmpty())
                        .count() > 1),
                filtered.stream().anyMatch(stub -> stub.bundle().at("/total").asInt() == 0)))
                .containsExactly(
                        Set.of("/Patient/eq081-VQEgP8drUUqCWzHfw3/armc [start-date]",
                                "/Patient/eq081-VQEgP8drUUqCWzHfw3/armc [-start-date]",
                                "/Patient/eq081-VQEgP8drUUqCWzHfw3/armc [end-date]",
                                "/Patient/eq081-VQEgP8drUUqCWzHfw3/armc [-end-date]"),
                        Set.of("/Patient/dev-patient-05/armc"),
                        ARMC_FILTER_PARAMETERS, Set.of("fit", "unfit"), true, true, true);
    }

    /**
     * The ARMC fault stubs, each by the failure it serves under a well-formed patient id no
     * fixture uses, so it never shadows a fixture's history and the read reaches the source.
     */
    @Test
    void theArmcFaultStubsServeEachFailureUnderAPatientIdOfItsOwn() {
        assertThat(PatientSourceStubs.armcFaults())
                .allSatisfy(fault -> assertThat(fault.id())
                        .isEqualTo("fault-armc-" + fault.fault())
                        .matches("[A-Za-z0-9\\-.]{1,64}")
                        .isNotIn(fixtureIds()))
                .extracting(PatientSourceStubs.FaultStub::fault)
                .containsExactlyInAnyOrder("unreachable", "slow", "server-error",
                        "unexpected-status", "not-a-searchset", "no-total", "no-id",
                        "invalid-id", "no-outcome", "no-medical-officer-2",
                        "unknown-board-type", "unknown-outcome", "end-before-start",
                        "end-date-not-to-the-day");
    }

    // ---- the Medication Histories -----------------------------------------------------------

    /** The filters a Medication History read sends the source, each as its own search parameter. */
    private static final List<String> MEDICATION_FILTER_PARAMETERS =
            List.of("dispense-status", "start-date", "end-date");

    /** The Medication Records of every page of {@code patient}'s Medication History, page after page. */
    private static List<JsonNode> medicationsOf(String patient) {
        return PatientSourceStubs.medicationHistories().stream()
                .filter(stub -> inTheSourcesOrder(stub, MEDICATION_FILTER_PARAMETERS))
                .filter(stub -> stub.path().equals("/Patient/" + patient + "/medication"))
                .sorted(Comparator.comparing(stub -> stub.query().at("/_offset/equalTo")
                        .asInt()))
                .flatMap(stub -> resourcesOf(stub).stream())
                .toList();
    }

    /** Every Medication Record of every fixture's Medication History, each once, as its pages hold it. */
    private static List<JsonNode> everyMedicationRecord() {
        return PatientSourceStubs.medicationHistories().stream()
                .filter(stub -> inTheSourcesOrder(stub, MEDICATION_FILTER_PARAMETERS))
                .flatMap(stub -> resourcesOf(stub).stream())
                .toList();
    }

    /**
     * Two fixtures have Medication Records, each page its own stub — Tan Wei Ming's two pages of
     * six and Tan Hui Min's one of four — and a fallback answers every fixture's other read with
     * none.
     */
    @Test
    void twoFixturesHaveAMedicationHistoryAndTheFallbackServesEveryFixture() {
        List<String> exact = PatientSourceStubs.medicationHistories().stream()
                .filter(stub -> inTheSourcesOrder(stub, MEDICATION_FILTER_PARAMETERS))
                .map(stub -> stub.path() + " " + stub.query().at("/_offset/equalTo").asString()
                        + " of " + stub.bundle().at("/total").asInt())
                .toList();
        String fallback = PatientSourceStubs.medicationHistories().stream()
                .map(PatientSourceStubs.HistoryStub::path)
                .filter(path -> path.contains("("))
                .findFirst().orElseThrow();

        assertThat(exact).containsExactlyInAnyOrder(
                "/Patient/eq081-VQEgP8drUUqCWzHfw3/medication 0 of 6",
                "/Patient/eq081-VQEgP8drUUqCWzHfw3/medication 5 of 6",
                "/Patient/dev-patient-05/medication 0 of 4");
        assertThat(fixtureIds()).allSatisfy(id ->
                assertThat("/Patient/" + id + "/medication").matches(fallback));
        assertThat("/Patient/fault-medication-no-id/medication").doesNotMatch(fallback);
    }

    /**
     * Every canned Medication History read is matched on the whole page the backend asks for —
     * five to a page, its offset and an accurate total — and pins the sort and each filter it does
     * not use as absent, and each it uses to its exact values, so a read of any other page, or
     * with a filter or sort no stub names, falls to the fallback rather than to an unfiltered
     * page.
     */
    @Test
    void everyCannedMedicationReadPinsItsPageItsSortAndEveryFilterItUsesOrNot() {
        assertThat(PatientSourceStubs.medicationHistories())
                .filteredOn(PatientSourceFixturesTests::canned)
                .hasSize(16)
                .allSatisfy(stub -> assertThat(List.of(
                        stub.query().at("/_count/equalTo").asString(),
                        stub.query().at("/_offset/equalTo").isString(),
                        stub.query().at("/_total/equalTo").asString()))
                        .containsExactly("5", true, "accurate"))
                .allSatisfy(stub -> assertThat(Stream.concat(Stream.of("_sort"),
                        MEDICATION_FILTER_PARAMETERS.stream()))
                        .as("each matcher, absent or pinned")
                        .allMatch(parameter -> pinsOrAbsent(stub.query().path(parameter))));
    }

    /** Each page holds no more than five Medication Records, and a history's pages hold its total. */
    @Test
    void everyMedicationHistoryPageHoldsAtMostFiveAndItsPagesHoldTheirTotal() {
        assertThat(PatientSourceStubs.medicationHistories())
                .allSatisfy(stub -> assertThat(resourcesOf(stub)).hasSizeLessThanOrEqualTo(5));
        assertThat(List.of(medicationsOf("eq081-VQEgP8drUUqCWzHfw3").size(),
                medicationsOf("dev-patient-05").size())).containsExactly(6, 4);
    }

    /** Tan Hui Min's history holds every Dispense Status, so a page reading it shows each badge. */
    @Test
    void tanHuiMinsMedicationHistoryCoversEveryDispenseStatus() {
        assertThat(medicationsOf("dev-patient-05"))
                .extracting(record -> record.get("dispenseStatus").asString())
                .containsOnly("pending", "dispensed", "uncollected")
                .contains("pending", "dispensed", "uncollected");
    }

    /** The two histories hold every Medical Centre between them, as their Dispensaries. */
    @Test
    void theMedicationHistoriesCoverEveryMedicalCentre() {
        Set<String> centres = Set.of("nee-soon-camp", "kranji-camp", "hendon-camp",
                "mandai-hill-camp", "khatib-camp");

        assertThat(everyMedicationRecord())
                .extracting(record -> record.get("dispensary").asString())
                .containsOnlyElementsOf(centres)
                .containsAll(centres);
    }

    /** A Medication Record's six required elements, beside its resource type and its id. */
    private static final Set<String> REQUIRED_MEDICATION_FIELDS = Set.of("resourceType", "id",
            "medication", "dispenseStatus", "startDate", "endDate", "dispensary",
            "prescribingDoctor");

    /** A Medication Record's six optional elements. */
    private static final Set<String> OPTIONAL_MEDICATION_FIELDS = Set.of("dosage", "route",
            "frequency", "step", "quantity", "duration");

    /**
     * Every Medication Record is a {@code Medication} of every required element, its id and
     * any of the optional ones, nothing else; one fixture's record has none of the six.
     */
    @Test
    void everyMedicationRecordHoldsItsRequiredElementsAndOneHasNoOptionalOne() {
        assertThat(everyMedicationRecord()).hasSize(10)
                .allSatisfy(record -> assertThat(List.<Object>of(
                        record.get("resourceType").asString(),
                        REQUIRED_MEDICATION_FIELDS.stream().allMatch(record::has),
                        record.propertyNames().stream()
                                .allMatch(field -> REQUIRED_MEDICATION_FIELDS.contains(field)
                                        || OPTIONAL_MEDICATION_FIELDS.contains(field))))
                        .containsExactly("Medication", true, true))
                .filteredOn(record -> OPTIONAL_MEDICATION_FIELDS.stream().noneMatch(record::has))
                .extracting(record -> record.get("id").asString())
                .containsExactly("medication-tan-hui-min-4");
    }

    /** Every Medication Record any stub answers, sorted or filtered too, holds its elements alone. */
    @Test
    void everyMedicationRecordAnyStubAnswersHoldsItsElementsAlone() {
        assertThat(PatientSourceStubs.medicationHistories().stream()
                .flatMap(stub -> resourcesOf(stub).stream()))
                .isNotEmpty()
                .allSatisfy(record -> assertThat(List.<Object>of(
                        record.get("resourceType").asString(),
                        REQUIRED_MEDICATION_FIELDS.stream().allMatch(record::has),
                        record.propertyNames().stream()
                                .allMatch(field -> REQUIRED_MEDICATION_FIELDS.contains(field)
                                        || OPTIONAL_MEDICATION_FIELDS.contains(field))))
                        .containsExactly("Medication", true, true));
    }

    /** Every fixture's record ends on or after the instant it starts. */
    @Test
    void everyMedicationRecordEndsOnOrAfterItStarts() {
        assertThat(everyMedicationRecord())
                .allSatisfy(record -> assertThat(
                        OffsetDateTime.parse(record.get("endDate").asString()))
                        .isAfterOrEqualTo(OffsetDateTime.parse(
                                record.get("startDate").asString())));
    }

    /** Every Medication Record's id is in FHIR's id grammar and no other record's. */
    @Test
    void everyMedicationRecordIdIsAFhirIdOfItsOwn() {
        List<String> ids = everyMedicationRecord().stream()
                .map(record -> record.get("id").asString())
                .toList();
        Stream<String> others = Stream.of(PatientSourceStubs.ffiHistories(),
                        PatientSourceStubs.numcHistories(), PatientSourceStubs.armcHistories())
                .flatMap(List::stream)
                .flatMap(stub -> resourcesOf(stub).stream())
                .map(record -> record.get("id").asString());

        assertThat(ids).hasSize(10).doesNotHaveDuplicates()
                .doesNotContainAnyElementsOf(others.toList())
                .allSatisfy(id -> assertThat(id).matches("[A-Za-z0-9\\-.]{1,64}"));
    }

    // ---- the Medication sort and filter stubs ---------------------------------------------

    /** The instant a Medication Record's date-time {@code field} names, whatever its offset. */
    private static Instant instantOf(JsonNode record, String field) {
        return OffsetDateTime.parse(record.get(field).asString()).toInstant();
    }

    /**
     * Whether {@code instant} lies within every end {@code ends} pins — {@code ge} an instant, on
     * or after it, and {@code lt} an instant, before it: the instants a date-time range's
     * Singapore minutes are sent as, both minutes inclusive — or any instant with no end pinned.
     */
    private static boolean inDateTimeRange(Instant instant, List<String> ends) {
        return ends.stream().allMatch(end -> end.startsWith("ge")
                ? !instant.isBefore(Instant.parse(end.substring(2)))
                : end.startsWith("lt") && instant.isBefore(Instant.parse(end.substring(2))));
    }

    /**
     * {@code records} as the {@code _sort} {@code sorts} pins orders them: by the instant the
     * date-time it names is, {@code -} first for descending, ties left as the source keeps them;
     * as they are with no sort pinned.
     */
    private static List<JsonNode> sortedByInstant(List<JsonNode> records, List<String> sorts) {
        if (sorts.isEmpty()) {
            return records;
        }
        String sort = sorts.getFirst();
        String field = SORTED_FIELDS.get(sort.replaceFirst("^-", ""));
        Comparator<JsonNode> order = Comparator.comparing(record -> instantOf(record, field));
        return records.stream()
                .sorted(sort.startsWith("-") ? order.reversed() : order)
                .toList();
    }

    /** The canned Medication History reads that are sorted or filtered, not in the source's order. */
    private static List<PatientSourceStubs.HistoryStub> narrowedMedicationReads() {
        return PatientSourceStubs.medicationHistories().stream()
                .filter(PatientSourceFixturesTests::canned)
                .filter(stub -> !inTheSourcesOrder(stub, MEDICATION_FILTER_PARAMETERS))
                .toList();
    }

    /**
     * Every sorted or filtered Medication read answers exactly the Medication Records its filters
     * select from the patient's history in the source's own order — the Dispense Statuses any of
     * them, and a date-time range both ends, compared as instants whatever offset a record was
     * given in — sorted as its {@code _sort} names by the whole date-time, ties left as the source
     * keeps them, or left in that order with none, paged by its offset, five to a page, with the
     * total it selects: the source's behaviour, as /docs/domain/medication.md documents it. Each
     * record is exactly as the unfiltered history holds it.
     */
    @Test
    void everySortedOrFilteredMedicationReadAnswersTheRecordsItsFiltersAndSortSelect() {
        assertThat(narrowedMedicationReads()).hasSize(13).allSatisfy(stub -> {
            String patient = stub.path().replaceFirst("^/Patient/", "")
                    .replaceFirst("/medication$", "");
            JsonNode query = stub.query();
            List<JsonNode> selected = medicationsOf(patient).stream()
                    .filter(record -> anyOf(record, "dispenseStatus",
                            pinnedIn(query, "dispense-status")))
                    .filter(record -> inDateTimeRange(instantOf(record, "startDate"),
                            pinnedIn(query, "start-date")))
                    .filter(record -> inDateTimeRange(instantOf(record, "endDate"),
                            pinnedIn(query, "end-date")))
                    .toList();
            List<JsonNode> expected = sortedByInstant(selected, pinnedIn(query, "_sort"));
            int offset = query.at("/_offset/equalTo").asInt();

            assertThat(List.of(stub.bundle().at("/total").asInt(), resourcesOf(stub)))
                    .containsExactly(expected.size(),
                            expected.subList(offset, Math.min(offset + 5, expected.size())));
        });
    }

    /**
     * Whether some filtered read answers a record whose date-time falls on the very minute one of
     * its {@code prefix} bounds names: for {@code ge}, the bound's own instant; for {@code lt},
     * the minute before it — the bound's to-minute, which the range keeps whole.
     */
    private static boolean aBoundOnARecordsMinuteKeepsIt(
            List<PatientSourceStubs.HistoryStub> filtered, String prefix) {
        return filtered.stream().anyMatch(stub -> Stream.of("start-date", "end-date")
                .anyMatch(parameter -> pinnedIn(stub.query(), parameter).stream()
                        .filter(end -> end.startsWith(prefix))
                        .map(end -> Instant.parse(end.substring(2))
                                .minusSeconds(prefix.equals("lt") ? 60 : 0))
                        .anyMatch(minute -> resourcesOf(stub).stream()
                                .anyMatch(record -> instantOf(record,
                                        SORTED_FIELDS.get(parameter)).equals(minute)))));
    }

    /**
     * The sort and filter stubs cover each direction of each date-time, on Tan Wei Ming, each sent
     * as the column alone; every filter, on Tan Hui Min, each Dispense Status among them and two
     * at once; a from-bound and a to-bound each on a record's exact minute, keeping it; two
     * filters combined; and a filter that matches nothing.
     */
    @Test
    void theMedicationSortAndFilterStubsCoverEverySortAndFilter() {
        List<PatientSourceStubs.HistoryStub> sorted = narrowedMedicationReads().stream()
                .filter(stub -> !pinnedIn(stub.query(), "_sort").isEmpty())
                .toList();
        List<PatientSourceStubs.HistoryStub> filtered = narrowedMedicationReads().stream()
                .filter(stub -> pinnedIn(stub.query(), "_sort").isEmpty())
                .toList();

        assertThat(List.of(
                sorted.stream().map(stub -> stub.path() + " " + pinnedIn(stub.query(), "_sort"))
                        .collect(Collectors.toSet()),
                filtered.stream().map(PatientSourceStubs.HistoryStub::path)
                        .collect(Collectors.toSet()),
                MEDICATION_FILTER_PARAMETERS.stream()
                        .filter(parameter -> filtered.stream()
                                .anyMatch(stub -> !pinnedIn(stub.query(), parameter).isEmpty()))
                        .toList(),
                filtered.stream().flatMap(stub -> pinnedIn(stub.query(), "dispense-status")
                                .stream())
                        .collect(Collectors.toSet()),
                aBoundOnARecordsMinuteKeepsIt(filtered, "ge"),
                aBoundOnARecordsMinuteKeepsIt(filtered, "lt"),
                filtered.stream().anyMatch(stub -> MEDICATION_FILTER_PARAMETERS.stream()
                        .filter(parameter -> !pinnedIn(stub.query(), parameter).isEmpty())
                        .count() > 1),
                filtered.stream().anyMatch(stub -> stub.bundle().at("/total").asInt() == 0)))
                .containsExactly(
                        Set.of("/Patient/eq081-VQEgP8drUUqCWzHfw3/medication [start-date]",
                                "/Patient/eq081-VQEgP8drUUqCWzHfw3/medication [-start-date]",
                                "/Patient/eq081-VQEgP8drUUqCWzHfw3/medication [end-date]",
                                "/Patient/eq081-VQEgP8drUUqCWzHfw3/medication [-end-date]"),
                        Set.of("/Patient/dev-patient-05/medication"),
                        MEDICATION_FILTER_PARAMETERS,
                        Set.of("pending", "dispensed", "uncollected", "pending,uncollected"),
                        true, true, true, true);
    }

    /**
     * The Medication fault stubs, each by the failure it serves under a well-formed patient id
     * no fixture uses, so it never shadows a fixture's history and the read reaches the source.
     */
    @Test
    void theMedicationFaultStubsServeEachFailureUnderAPatientIdOfItsOwn() {
        assertThat(PatientSourceStubs.medicationFaults())
                .allSatisfy(fault -> assertThat(fault.id())
                        .isEqualTo("fault-medication-" + fault.fault())
                        .matches("[A-Za-z0-9\\-.]{1,64}")
                        .isNotIn(fixtureIds()))
                .extracting(PatientSourceStubs.FaultStub::fault)
                .containsExactlyInAnyOrder("unreachable", "slow", "server-error",
                        "unexpected-status", "not-a-searchset", "no-total", "no-id",
                        "invalid-id", "no-dispense-status", "no-prescribing-doctor",
                        "unknown-dispense-status", "unknown-dispensary",
                        "start-date-not-a-date-time", "end-before-start");
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
