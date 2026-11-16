package com.example.backend.patient.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * A Patient: the person care is about, as the service reads them from the in-house patient
 * source (/docs/domain/patient.md, ADR 0014). Shown as received (ADR 0020): its NRIC, its
 * official name's text, its gender and its date of birth are each a {@link SourceField} —
 * present, missing or in the wrong format — and none is refused for what it holds, a gender
 * outside the four codes included. Its id is the one it was asked for, never the source's to
 * change: a Patient under another id is no Patient of this one.
 *
 * <p>Pure domain, with no FHIR wire shape: the web adapter renders it as an R5 {@code Patient}.
 * There is no age here. Age is derived from {@code birthDate} where it is shown, never stored.
 *
 * <p>Patient data is Restricted, Sensitive Normal, and a log names a patient by its id alone
 * (/docs/domain/patient.md, "Logging"). So {@link #toString()} prints the id and nothing else: a
 * record's generated one prints every field, and would put the NRIC, the name and the date of
 * birth into any record, message or exception that happened to print a Patient.
 *
 * @param id           the patient's id: its EHR's FHIR id, and its FHIR resource id here
 * @param nric         the NRIC, its one identifier, under {@link #NRIC_SYSTEM}; dummy values in
 *                     the development fixtures, their shape unchecked
 * @param name         the official name, or {@code null} when the source holds none
 * @param gender       the administrative gender's code, known to {@link AdministrativeGender} or
 *                     not
 * @param birthDate    the date of birth
 * @param demographics the Demographic details, which a read by id copies and a search does not;
 *                     {@link Demographics#NONE} when there are none
 */
public record Patient(
        PatientId id, SourceField<String> nric, PatientName name, SourceField<String> gender,
        SourceField<LocalDate> birthDate, Demographics demographics) implements ReceivedRecord {

    /** Synapxe's NRIC NamingSystem: the {@code system} a Patient's NRIC identifier is under. */
    public static final String NRIC_SYSTEM = "https://fhir.synapxe.sg/NamingSystem/nric";

    /**
     * An element of the R5 {@code Patient} the service serves that is shown as received, by its
     * path there — the one NRIC identifier's and the one name's, each first — as
     * {@link #invalid()} names it.
     */
    public enum Element {
        NRIC("identifier[0].value"),
        NAME_TEXT("name[0].text"),
        GENDER("gender"),
        BIRTH_DATE("birthDate");

        private final String path;

        Element(String path) {
            this.path = path;
        }

        /** Its path in the served {@code Patient}. */
        public String path() {
            return path;
        }
    }

    public Patient {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(nric, "nric");
        Objects.requireNonNull(gender, "gender");
        Objects.requireNonNull(birthDate, "birthDate");
        Objects.requireNonNull(demographics, "demographics");
    }

    /** A Patient of the five elements, each as the source should send it, with these details. */
    public Patient(PatientId id, String nric, PatientName name, AdministrativeGender gender,
            LocalDate birthDate, Demographics demographics) {
        this(id, SourceField.present(Objects.requireNonNull(nric, "nric")),
                Objects.requireNonNull(name, "name"),
                SourceField.present(Objects.requireNonNull(gender, "gender").code()),
                SourceField.present(Objects.requireNonNull(birthDate, "birthDate")),
                demographics);
    }

    /** A Patient of the five elements alone, each as the source should send it, with no Demographic details. */
    public Patient(
            PatientId id, String nric, PatientName name, AdministrativeGender gender, LocalDate birthDate) {
        this(id, nric, name, gender, birthDate, Demographics.NONE);
    }

    /** This Patient with {@code details} as its Demographic details. */
    public Patient withDemographics(Demographics details) {
        return new Patient(id, nric, name, gender, birthDate, details);
    }

    /** The elements whose format was wrong, in {@link Element}'s order. */
    public List<Element> invalidElements() {
        return Stream.of(Element.values())
                .filter(element -> field(element).received() == SourceField.Received.INVALID)
                .toList();
    }

    /** The field {@code element} holds; a Patient with no official name has no name text. */
    private SourceField<?> field(Element element) {
        return switch (element) {
            case NRIC -> nric;
            case NAME_TEXT -> name == null ? SourceField.missing() : name.text();
            case GENDER -> gender;
            case BIRTH_DATE -> birthDate;
        };
    }

    /**
     * The elements whose format was wrong, by their path in the R5 {@code Patient} the service
     * serves ({@link Element#path()}), in {@link Element}'s order.
     */
    @Override
    public List<String> invalid() {
        return invalidElements().stream().map(Element::path).toList();
    }

    @Override
    public String toString() {
        return "Patient[id=" + id + "]";
    }
}
