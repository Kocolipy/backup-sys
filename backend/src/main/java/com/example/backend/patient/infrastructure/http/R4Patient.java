package com.example.backend.patient.infrastructure.http;

import com.example.backend.patient.domain.AdministrativeGender;
import com.example.backend.patient.domain.Demographics;
import com.example.backend.patient.domain.MilitaryHistory;
import com.example.backend.patient.domain.NextOfKin;
import com.example.backend.patient.domain.Patient;
import com.example.backend.patient.domain.PatientId;
import com.example.backend.patient.domain.PatientMatch;
import com.example.backend.patient.domain.PatientName;
import com.example.backend.patient.infrastructure.http.PatientSourceException.Failure;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;

/**
 * The in-house patient source's FHIR R4 {@code Patient}, as far as the service reads it: the
 * {@code id}, the four elements a Patient is copied from, in a search's answer the Last visit
 * extension, and in a read by id the Demographic details. Every other element the source sends
 * is never bound.
 *
 * <p>R4 and R5 agree on these elements (ADR 0014, "R5 on the wire"), so the mapping is a straight
 * copy: the {@code identifier} under the NRIC NamingSystem, the {@code name} whose {@code use} is
 * {@code official}, the {@code gender} and the {@code birthDate}. The Last visit is the source's
 * own extension ({@link #LAST_VISIT}, ADR 0018), a {@code valueDate} to the day. The Demographic
 * details are the first {@code phone} and {@code email} in {@code telecom}, the first {@code home}
 * {@code address}'s text, the first {@code contact} coded v2-0131 {@code N} with a name text, and
 * the military service extension ({@link #MILITARY_SERVICE}); an element without what its copy
 * needs is not copied.
 *
 * @param resourceType the resource's type: a search's entry must be a {@code Patient}
 * @param id         the resource's id: the id it was read by, or in a search, a FHIR id of its own
 * @param extension  every extension the source sends; the Last visit and military service are read
 * @param identifier every identifier the source holds; only the NRIC is copied
 * @param name       every name the source holds; only the official one is copied
 * @param telecom    every contact point the source holds; the first phone and email are copied
 * @param gender     the administrative gender code
 * @param birthDate  the date of birth, which must be to the day
 * @param address    every address the source holds; the first home address's text is copied
 * @param contact    every contact the source holds; the first next of kin is copied
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record R4Patient(
        String resourceType, String id, List<Extension> extension, List<Identifier> identifier,
        List<HumanName> name, List<ContactPoint> telecom, String gender, String birthDate,
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
     * there and a FHIR id, with its Last visit.
     *
     * @throws PatientSourceException when the resource is no Patient, has no id or one outside
     *                                FHIR's grammar, breaks a rule a Patient must keep, or has a
     *                                Last visit not to the day, naming which and quoting no value
     */
    PatientMatch toMatch() {
        if (!"Patient".equals(resourceType)) {
            throw PatientSourceException.failed(Failure.ANOTHER_RESOURCE);
        }
        if (id == null) {
            throw PatientSourceException.failed(Failure.NO_ID);
        }
        PatientId own;
        try {
            own = new PatientId(id);
        } catch (IllegalArgumentException outsideTheGrammar) {
            throw PatientSourceException.failed(Failure.INVALID_ID);
        }
        return new PatientMatch(core(own), lastVisit());
    }

    /** The Last visit's date, or {@code null} when the source records none. */
    private LocalDate lastVisit() {
        Extension lastVisit = listed(extension).stream()
                .filter(candidate -> LAST_VISIT.equals(candidate.url()))
                .findFirst()
                .orElse(null);
        if (lastVisit == null) {
            return null;
        }
        try {
            return LocalDate.parse(Objects.requireNonNull(lastVisit.valueDate()));
        } catch (DateTimeParseException | NullPointerException notToTheDay) {
            throw PatientSourceException.failed(Failure.LAST_VISIT_NOT_TO_THE_DAY);
        }
    }

    /**
     * The domain's Patient this resource is, read under {@code asked}, with its Demographic
     * details.
     *
     * @throws PatientSourceException when the resource is another patient's, breaks a rule a
     *                                Patient must keep, or has a military service date not to the
     *                                day, naming which and quoting no value
     */
    Patient toPatient(PatientId asked) {
        return core(asked).withDemographics(demographics());
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
        try {
            return LocalDate.parse(Objects.requireNonNull(date.valueDate()));
        } catch (DateTimeParseException | NullPointerException notToTheDay) {
            throw PatientSourceException.failed(Failure.SERVICE_DATE_NOT_TO_THE_DAY);
        }
    }

    private static boolean present(String text) {
        return text != null && !text.isBlank();
    }

    /**
     * The domain's Patient of the five elements this resource is, under {@code asked}, with no
     * Demographic details: what a search copies.
     */
    private Patient core(PatientId asked) {
        if (!asked.value().equals(id)) {
            throw PatientSourceException.failed(Failure.ANOTHER_RESOURCE);
        }
        return new Patient(
                asked,
                nric(),
                officialName(),
                AdministrativeGender.fromCode(gender).orElseThrow(() ->
                        PatientSourceException.failed(Failure.NO_GENDER)),
                dayOfBirth());
    }

    /** The date of birth, which must be there, and to the day. */
    private LocalDate dayOfBirth() {
        if (birthDate == null) {
            throw PatientSourceException.failed(Failure.NO_BIRTH_DATE);
        }
        try {
            return LocalDate.parse(birthDate);
        } catch (DateTimeParseException notToTheDay) {
            throw PatientSourceException.failed(Failure.BIRTH_DATE_NOT_TO_THE_DAY);
        }
    }

    private String nric() {
        return listed(identifier).stream()
                .filter(candidate -> Patient.NRIC_SYSTEM.equals(candidate.system()))
                .map(Identifier::value)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> PatientSourceException.failed(Failure.NO_NRIC));
    }

    /**
     * The official name, which must have its text: the text is the name as the person writes it,
     * and is never rebuilt from the family and given names, which many names do not split into.
     */
    private PatientName officialName() {
        HumanName official = listed(name).stream()
                .filter(candidate -> OFFICIAL.equals(candidate.use()))
                .findFirst()
                .orElseThrow(() -> PatientSourceException.failed(Failure.NO_OFFICIAL_NAME));
        if (official.text() == null || official.text().isBlank()) {
            throw PatientSourceException.failed(Failure.NO_NAME_TEXT);
        }
        return new PatientName(official.text(), official.family(), official.given());
    }

    private static <T> List<T> listed(List<T> elements) {
        return elements == null ? List.of() : elements;
    }

    /**
     * An R4 {@code Extension}: only its url, a {@code valueDate} or {@code valueString}, and the
     * extensions nested in it are read.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Extension(String url, String valueDate, String valueString, List<Extension> extension) {
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
    record Contact(List<CodeableConcept> relationship, HumanName name, List<ContactPoint> telecom) {

        boolean isNextOfKin() {
            return listed(relationship).stream().anyMatch(CodeableConcept::marksNextOfKin);
        }
    }

    /** An R4 {@code Identifier}: only its system and value are read. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Identifier(String system, String value) {
    }

    /** An R4 {@code HumanName}: only its use, text, family and given names are read. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record HumanName(String use, String text, String family, List<String> given) {
    }
}
