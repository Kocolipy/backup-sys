package com.example.backend.patient.infrastructure.http;

import static com.example.backend.patient.infrastructure.http.ReceivedFields.day;
import static com.example.backend.patient.infrastructure.http.ReceivedFields.text;

import com.example.backend.observability.InvalidSourceFields;
import com.example.backend.patient.domain.AdministrativeGender;
import com.example.backend.patient.domain.Demographics;
import com.example.backend.patient.domain.MilitaryHistory;
import com.example.backend.patient.domain.NextOfKin;
import com.example.backend.patient.domain.Patient;
import com.example.backend.patient.domain.PatientId;
import com.example.backend.patient.domain.PatientMatch;
import com.example.backend.patient.domain.PatientName;
import com.example.backend.patient.domain.SourceField;
import com.example.backend.patient.infrastructure.http.PatientSourceException.Failure;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Predicate;
import tools.jackson.databind.JsonNode;

/**
 * The in-house patient source's FHIR R4 {@code Patient}, as far as the service reads it: the
 * {@code id}, the four elements a Patient is copied from, in a search's answer the Last visit
 * extension, and in a read by id the Demographic details. Every other element the source sends
 * is never bound.
 *
 * <p>R4 and R5 agree on these elements (ADR 0014, "R5 on the wire"), so the mapping is a straight
 * copy: the {@code identifier} under the NRIC NamingSystem, the {@code name} whose {@code use} is
 * {@code official}, the {@code gender} and the {@code birthDate}. The Last visit is the source's
 * own extension ({@link #LAST_VISIT}, ADR 0018), a {@code valueDate} to the day, read as received
 * (ADR 0020): bound as the raw JSON it arrived as, and present, missing or invalid. The
 * Demographic details are the first {@code phone} and {@code email} in {@code telecom}, the first
 * {@code home} {@code address}'s text, the first {@code contact} coded v2-0131 {@code N} with a
 * name text, and the military service extension ({@link #MILITARY_SERVICE}); an element without
 * what its copy needs is not copied.
 *
 * <p>A read by id copies the four as received (ADR 0020): the NRIC's value, the official name's
 * text, the gender and the birth date are each bound as the raw JSON they arrived as and read by
 * {@link ReceivedFields} — present, missing or invalid — so none fails the read, and only another
 * patient's id does. A search keeps the strict reading ADR 0018 describes for the four: a match
 * missing one, holding one in the wrong format or a gender outside the four codes fails the
 * search. Its Last visit never does.
 *
 * @param resourceType the resource's type: a search's entry must be a {@code Patient}
 * @param id         the resource's id: the id it was read by, or in a search, a FHIR id of its own
 * @param extension  every extension the source sends; the Last visit and military service are read
 * @param identifier every identifier the source holds; only the NRIC is copied
 * @param name       every name the source holds; only the official one is copied
 * @param telecom    every contact point the source holds; the first phone and email are copied
 * @param gender     the administrative gender code, as the raw JSON it arrived as
 * @param birthDate  the date of birth, a calendar date, as the raw JSON it arrived as
 * @param address    every address the source holds; the first home address's text is copied
 * @param contact    every contact the source holds; the first next of kin is copied
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record R4Patient(
        String resourceType, String id, List<Extension> extension, List<Identifier> identifier,
        List<HumanName> name, List<ContactPoint> telecom, JsonNode gender, JsonNode birthDate,
        List<Address> address, List<Contact> contact) {

    /** The official name's {@code use}. */
    private static final String OFFICIAL = "official";

    /** The patient source's Last visit extension: the date of the Patient's latest visit. */
    static final String LAST_VISIT = "https://fhir.synapxe.sg/StructureDefinition/last-visit";

    /**
     * The extension holding a Patient's military service: a placeholder URL until the in-house
     * source names its own (ADR 0014).
     */
    static final String MILITARY_SERVICE =
            "http://example.org/fhir/StructureDefinition/military-service";

    /** HL7 v2 table 0131, whose code {@code N} marks a contact as the next of kin. */
    static final String CONTACT_ROLE = "http://terminology.hl7.org/CodeSystem/v2-0131";

    /**
     * The match this resource is, in a search's answer: a Patient under its own id, which must be
     * there and a FHIR id, with its Last visit as received (ADR 0020) — missing when the source
     * records none, invalid when it is no date to the day, and never refused for what it holds.
     *
     * @throws PatientSourceException when the resource is no Patient, has no id or one outside
     *                                FHIR's grammar, or breaks a rule a Patient must keep, naming
     *                                which and quoting no value
     */
    PatientMatch toMatch() {
        if (!"Patient".equals(resourceType)) {
            throw PatientSourceException.failed(Failure.ANOTHER_RESOURCE);
        }
        PatientId own = new PatientId(
                SourceSearchset.ownId(id, Failure.NO_ID, Failure.INVALID_ID));
        int lastVisit = lastVisitIndex();
        return new PatientMatch(core(own),
                lastVisit < 0 ? SourceField.missing() : day(extension.get(lastVisit).valueDate()));
    }

    /**
     * The fields of {@code match}, read from this resource, whose format was wrong, each by its
     * path in this resource — {@code extension[1].valueDate} — beside the row's field it is
     * ({@link PatientMatch#invalid()}).
     */
    List<InvalidSourceFields.Field> invalidFields(PatientMatch match) {
        return match.invalid().stream()
                .map(field -> new InvalidSourceFields.Field(
                        "extension[" + lastVisitIndex() + "].valueDate", field))
                .toList();
    }

    /** The place of the first Last visit extension, or -1 when there is none. */
    private int lastVisitIndex() {
        return indexOf(extension, candidate -> LAST_VISIT.equals(candidate.url()));
    }

    /**
     * The domain's Patient this resource is, read under {@code asked} as received (ADR 0020),
     * with its Demographic details: its NRIC, official name's text, gender and birth date each
     * present, missing or invalid, and none refused for what it holds.
     *
     * @throws PatientSourceException when the resource is another patient's, or has a military
     *                                service date not to the day, naming which and quoting no
     *                                value
     */
    Patient toPatient(PatientId asked) {
        return received(asked).withDemographics(demographics());
    }

    /** The Demographic details this resource holds; any the source does not hold are null. */
    private Demographics demographics() {
        return new Demographics(contactPointOf(telecom, "phone"), contactPointOf(telecom, "email"),
                residentialAddress(), nextOfKin(), militaryHistory());
    }

    /** The value of the first of {@code points} under {@code system} that has one. */
    private static String contactPointOf(List<ContactPoint> points, String system) {
        return listed(points).stream()
                .filter(point -> system.equals(point.system()) && point.value() != null)
                .map(ContactPoint::value)
                .findFirst()
                .orElse(null);
    }

    /** The first {@code home} address's text, which is never rebuilt from its lines. */
    private String residentialAddress() {
        return listed(address).stream()
                .filter(candidate -> "home".equals(candidate.use()) && present(candidate.text()))
                .map(Address::text)
                .findFirst()
                .orElse(null);
    }

    /**
     * The first contact coded as next of kin with a name text: its relationship is the text of
     * the first of its relationships that has one, its phone its first phone.
     */
    private NextOfKin nextOfKin() {
        return listed(contact).stream()
                .filter(candidate -> candidate.isNextOfKin() && candidate.name() != null
                        && present(candidate.name().text()))
                .findFirst()
                .map(kin -> new NextOfKin(kin.name().text(),
                        listed(kin.relationship()).stream().map(CodeableConcept::text)
                                .filter(R4Patient::present).findFirst().orElse(null),
                        contactPointOf(kin.telecom(), "phone")))
                .orElse(null);
    }

    /**
     * The military service extension: its service records in order, each field a
     * {@code valueString}, and its dates, each a {@code valueDate} to the day. A sub-extension it
     * does not name is not read.
     */
    private MilitaryHistory militaryHistory() {
        Extension military = listed(extension).stream()
                .filter(candidate -> MILITARY_SERVICE.equals(candidate.url()))
                .findFirst()
                .orElse(null);
        if (military == null) {
            return null;
        }
        List<Extension> parts = listed(military.extension());
        return new MilitaryHistory(
                parts.stream().filter(part -> "serviceRecord".equals(part.url()))
                        .map(R4Patient::serviceRecord).toList(),
                serviceDate(parts, "enlistmentDate"),
                serviceDate(parts, "ordDate"),
                serviceDate(parts, "mrDate"),
                serviceDate(parts, "rodDate"));
    }

    private static MilitaryHistory.ServiceRecord serviceRecord(Extension record) {
        List<Extension> fields = listed(record.extension());
        return new MilitaryHistory.ServiceRecord(
                valueString(fields, "serviceType"),
                valueString(fields, "rank"),
                valueString(fields, "unit"),
                valueString(fields, "service"),
                valueString(fields, "vocation"));
    }

    private static String valueString(List<Extension> fields, String url) {
        return fields.stream().filter(field -> url.equals(field.url()))
                .map(Extension::valueString).filter(R4Patient::present).findFirst().orElse(null);
    }

    private static LocalDate serviceDate(List<Extension> parts, String url) {
        Extension date = parts.stream().filter(part -> url.equals(part.url()))
                .findFirst().orElse(null);
        if (date == null) {
            return null;
        }
        SourceField<LocalDate> day = day(date.valueDate());
        if (!day.isPresent()) {
            throw PatientSourceException.failed(Failure.SERVICE_DATE_NOT_TO_THE_DAY);
        }
        return day.value();
    }

    private static boolean present(String text) {
        return text != null && !text.isBlank();
    }

    /**
     * The domain's Patient of the five elements this resource is, under {@code asked}, with no
     * Demographic details, each element as it was received (ADR 0020): the identifier under the
     * NRIC NamingSystem's value, the official name — its text present, missing or invalid, its
     * family and given names as they came — the gender's code and the birth date.
     *
     * @throws PatientSourceException when the resource is another patient's
     */
    private Patient received(PatientId asked) {
        if (!asked.value().equals(id)) {
            throw PatientSourceException.failed(Failure.ANOTHER_RESOURCE);
        }
        int nric = nricIndex();
        int official = officialNameIndex();
        HumanName officialName = official < 0 ? null : name.get(official);
        return new Patient(
                asked,
                nric < 0 ? SourceField.missing() : text(identifier.get(nric).value()),
                officialName == null ? null : new PatientName(text(officialName.text()),
                        officialName.family(), officialName.given()),
                text(gender),
                day(birthDate),
                Demographics.NONE);
    }

    /**
     * The fields of {@code patient}, read from this resource, whose format was wrong, each by its
     * path in this answer — {@code identifier[1].value}, {@code name[2].text} — beside the element
     * of the served {@code Patient} it is ({@link Patient#invalid()}).
     */
    List<InvalidSourceFields.Field> invalidFields(Patient patient) {
        return patient.invalidElements().stream()
                .map(element -> new InvalidSourceFields.Field(pathOf(element), element.path()))
                .toList();
    }

    /**
     * Where in this answer the served {@code Patient}'s {@code element} was read from: the NRIC
     * and the name text from the identifier and the name they were found in, each counted from
     * zero, and the gender and the birth date where they are served.
     */
    private String pathOf(Patient.Element element) {
        return switch (element) {
            case NRIC -> "identifier[" + nricIndex() + "].value";
            case NAME_TEXT -> "name[" + officialNameIndex() + "].text";
            case GENDER, BIRTH_DATE -> element.path();
        };
    }

    /**
     * The domain's Patient of the five elements this resource is, under {@code asked}, with no
     * Demographic details, read strictly: what a search copies, each element required.
     *
     * @throws PatientSourceException naming the first element the resource does not hold as a
     *                                Patient must, or another patient's id, quoting no value
     */
    private Patient core(PatientId asked) {
        Patient patient = received(asked);
        if (!patient.nric().isPresent()) {
            throw PatientSourceException.failed(Failure.NO_NRIC);
        }
        if (patient.name() == null) {
            throw PatientSourceException.failed(Failure.NO_OFFICIAL_NAME);
        }
        if (!patient.name().text().isPresent()) {
            throw PatientSourceException.failed(Failure.NO_NAME_TEXT);
        }
        if (AdministrativeGender.fromCode(patient.gender().orElse(null)).isEmpty()) {
            throw PatientSourceException.failed(Failure.NO_GENDER);
        }
        return switch (patient.birthDate().received()) {
            case PRESENT -> patient;
            case MISSING -> throw PatientSourceException.failed(Failure.NO_BIRTH_DATE);
            case INVALID -> throw PatientSourceException.failed(Failure.BIRTH_DATE_NOT_TO_THE_DAY);
        };
    }

    /** The place of the first identifier under the NRIC NamingSystem, or -1 when there is none. */
    private int nricIndex() {
        return indexOf(identifier, candidate -> Patient.NRIC_SYSTEM.equals(candidate.system()));
    }

    /** The place of the first name whose {@code use} is {@code official}, or -1 when there is none. */
    private int officialNameIndex() {
        return indexOf(name, candidate -> OFFICIAL.equals(candidate.use()));
    }

    private static <T> int indexOf(List<T> elements, Predicate<T> wanted) {
        List<T> listed = listed(elements);
        for (int place = 0; place < listed.size(); place++) {
            if (wanted.test(listed.get(place))) {
                return place;
            }
        }
        return -1;
    }

    private static <T> List<T> listed(List<T> elements) {
        return elements == null ? List.of() : elements;
    }

    /**
     * An R4 {@code Extension}: only its url, a {@code valueDate} — as the raw JSON it arrived as
     * ({@link ReceivedFields}) — or {@code valueString}, and the extensions nested in it are read.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Extension(String url, JsonNode valueDate, String valueString, List<Extension> extension) {
    }

    /** An R4 {@code ContactPoint}: only its system and value are read. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ContactPoint(String system, String value) {
    }

    /** An R4 {@code Address}: only its use and text are read. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Address(String use, String text) {
    }

    /** An R4 {@code Coding}: only its system and code are read. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Coding(String system, String code) {
    }

    /** An R4 {@code CodeableConcept}: only its codings and text are read. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record CodeableConcept(List<Coding> coding, String text) {

        boolean marksNextOfKin() {
            return listed(coding).stream().anyMatch(coded ->
                    CONTACT_ROLE.equals(coded.system()) && "N".equals(coded.code()));
        }
    }

    /** An R4 {@code Patient.contact}: only its relationships, name and contact points are read. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Contact(List<CodeableConcept> relationship, ContactName name,
            List<ContactPoint> telecom) {

        boolean isNextOfKin() {
            return listed(relationship).stream().anyMatch(CodeableConcept::marksNextOfKin);
        }
    }

    /**
     * An R4 {@code Identifier}: only its system and value are read, the value as the raw JSON it
     * arrived as ({@link ReceivedFields}).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Identifier(String system, JsonNode value) {
    }

    /**
     * An R4 {@code HumanName}: only its use, text, family and given names are read, the text as
     * the raw JSON it arrived as ({@link ReceivedFields}).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record HumanName(String use, JsonNode text, String family, List<String> given) {
    }

    /** An R4 {@code HumanName} as a contact's name: only its text is read. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ContactName(String text) {
    }
}
