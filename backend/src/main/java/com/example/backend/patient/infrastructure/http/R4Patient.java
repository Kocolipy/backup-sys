package com.example.backend.patient.infrastructure.http;

import com.example.backend.patient.domain.AdministrativeGender;
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
 * {@code id}, the four elements a Patient is copied from and, in a search's answer, the Last visit
 * extension. Every other element the source sends is never bound.
 *
 * <p>R4 and R5 agree on these elements (ADR 0014, "R5 on the wire"), so the mapping is a straight
 * copy: the {@code identifier} under the NRIC NamingSystem, the {@code name} whose {@code use} is
 * {@code official}, the {@code gender} and the {@code birthDate}. The Last visit is the source's
 * own extension ({@link #LAST_VISIT}, ADR 0018), a {@code valueDate} to the day.
 *
 * @param resourceType the resource's type: a search's entry must be a {@code Patient}
 * @param id         the resource's id: the id it was read by, or in a search, a FHIR id of its own
 * @param extension  every extension the source sends; only the Last visit is read
 * @param identifier every identifier the source holds; only the NRIC is copied
 * @param name       every name the source holds; only the official one is copied
 * @param gender     the administrative gender code
 * @param birthDate  the date of birth, which must be to the day
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record R4Patient(
        String resourceType, String id, List<Extension> extension, List<Identifier> identifier,
        List<HumanName> name, String gender, String birthDate) {

    /** The official name's {@code use}. */
    private static final String OFFICIAL = "official";

    /** The patient source's Last visit extension: the date of the Patient's latest visit. */
    static final String LAST_VISIT = "https://fhir.synapxe.sg/StructureDefinition/last-visit";

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
        PatientId own = new PatientId(
                SourceSearchset.ownId(id, Failure.NO_ID, Failure.INVALID_ID));
        return new PatientMatch(toPatient(own), lastVisit());
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
     * The domain's Patient this resource is, read under {@code asked}.
     *
     * @throws PatientSourceException when the resource is another patient's, or breaks a rule a
     *                                Patient must keep, naming which and quoting no value
     */
    Patient toPatient(PatientId asked) {
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

    /** An R4 {@code Extension}: only its url and a {@code valueDate} are read. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Extension(String url, String valueDate) {
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
