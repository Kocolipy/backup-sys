package com.example.backend.patient.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.example.backend.patient.domain.Demographics;
import com.example.backend.patient.domain.MilitaryHistory;
import com.example.backend.patient.domain.NextOfKin;
import com.example.backend.patient.domain.Patient;
import com.example.backend.patient.domain.PatientName;
import com.example.backend.patient.domain.SourceField;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * A Patient as a FHIR R5 {@code Patient} resource, in the JSON shape Jackson writes this record
 * in: base R5, no {@code meta} and so no {@code meta.profile}, and no element beyond the five the
 * domain holds, its Demographic details and the names of the elements it received in the wrong
 * format (/docs/domain/patient.md). An element the Patient has none of is left out, never
 * written empty. The service renders FHIR itself from its own records; no FHIR library is on the
 * runtime classpath.
 *
 * <p>Shown as received (ADR 0020): an element the source left out or sent blank is left out, and
 * so is one it sent in the wrong format, which an {@link #INVALID_FIELD} extension names instead
 * by its path here — {@code name[0].text} — since FHIR JSON has no {@code null} and base R5 no
 * element for a value received in the wrong format. A gender outside the four codes is written
 * as its code.
 *
 * @param resourceType always {@code Patient}
 * @param id           the patient's id, its EHR's FHIR id
 * @param extension    the military service extension, when the Patient has one, then one
 *                     {@link #INVALID_FIELD} extension for each element received in the wrong
 *                     format, in {@link Patient#invalid()}'s order
 * @param identifier   at most one: the NRIC, under Synapxe's NRIC NamingSystem
 *                     ({@link Patient#NRIC_SYSTEM}), its system alone when its value was invalid
 * @param name         at most one: the official name, with no text when it had none or an
 *                     invalid one
 * @param telecom      the phone, then the email, each when the Patient has one
 * @param gender       the administrative gender code, one of the four or not
 * @param birthDate    the date of birth, {@code YYYY-MM-DD}
 * @param address      the residential address, as a {@code home} address's text
 * @param contact      the next of kin, coded v2-0131 {@code N}
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FhirPatient(
        String resourceType,
        String id,
        List<Extension> extension,
        List<Identifier> identifier,
        List<HumanName> name,
        List<ContactPoint> telecom,
        String gender,
        String birthDate,
        List<Address> address,
        List<Contact> contact) {

    /** The extension a Patient's military service is rendered under, as the source sends it. */
    static final String MILITARY_SERVICE =
            "http://example.org/fhir/StructureDefinition/military-service";

    /** HL7 v2 table 0131, whose code {@code N} marks a contact as the next of kin. */
    static final String CONTACT_ROLE = "http://terminology.hl7.org/CodeSystem/v2-0131";

    /**
     * The extension naming one element the Patient was received with in the wrong format, by its
     * path in this resource ({@code valueString}): the service's own, a placeholder URL until the
     * in-house FHIR work names one, as the military service's is.
     */
    static final String INVALID_FIELD =
            "http://example.org/fhir/StructureDefinition/invalid-field";

    /** The resource {@code patient} renders as. */
    public static FhirPatient of(Patient patient) {
        Demographics details = patient.demographics();
        return new FhirPatient(
                "Patient",
                patient.id().value(),
                Stream.concat(
                        Stream.ofNullable(details.militaryHistory()).map(Extension::military),
                        patient.invalid().stream().map(Extension::invalidField)).toList(),
                Identifier.nric(patient.nric()),
                Stream.ofNullable(patient.name()).map(HumanName::official).toList(),
                ContactPoint.of(details.phone(), details.email()),
                patient.gender().value(),
                patient.birthDate().map(LocalDate::toString).value(),
                Stream.ofNullable(details.residentialAddress())
                        .map(text -> new Address("home", text)).toList(),
                Stream.ofNullable(details.nextOfKin()).map(Contact::of).toList());
    }

    /**
     * A FHIR {@code Identifier}: a value and the system it is unique within, or the system alone
     * for a value received in the wrong format.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Identifier(String system, String value) {

        /** The NRIC as received: with its value, by its system alone when invalid, or none. */
        static List<Identifier> nric(SourceField<String> nric) {
            return switch (nric.received()) {
                case PRESENT -> List.of(new Identifier(Patient.NRIC_SYSTEM, nric.value()));
                case INVALID -> List.of(new Identifier(Patient.NRIC_SYSTEM, null));
                case MISSING -> List.of();
            };
        }
    }

    /**
     * A FHIR {@code HumanName}. {@code text} is authoritative, and left out when the source sent
     * none or one in the wrong format; {@code family} and {@code given} are left out, not written
     * empty, when the name is not split.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record HumanName(String use, String text, String family, List<String> given) {

        static HumanName official(PatientName name) {
            return new HumanName("official", name.text().value(), name.family(), name.given());
        }
    }

    /** A FHIR {@code ContactPoint}: a phone or an email. */
    public record ContactPoint(String system, String value) {

        /** The phone, then the email, leaving out whichever is {@code null}. */
        static List<ContactPoint> of(String phone, String email) {
            List<ContactPoint> points = new ArrayList<>();
            if (phone != null) {
                points.add(new ContactPoint("phone", phone));
            }
            if (email != null) {
                points.add(new ContactPoint("email", email));
            }
            return points;
        }
    }

    /** A FHIR {@code Address}, as its text alone. */
    public record Address(String use, String text) {
    }

    /** A FHIR {@code Coding}. */
    public record Coding(String system, String code) {
    }

    /** A FHIR {@code CodeableConcept}: codings, or a text alone. */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record CodeableConcept(List<Coding> coding, String text) {
    }

    /** A FHIR {@code HumanName} as its text alone, for a contact. */
    public record ContactName(String text) {
    }

    /**
     * A FHIR {@code Patient.contact}: the next of kin, coded v2-0131 {@code N}, then their
     * relationship as text, their name and their phone.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Contact(List<CodeableConcept> relationship, ContactName name,
            List<ContactPoint> telecom) {

        static Contact of(NextOfKin kin) {
            List<CodeableConcept> relationship = new ArrayList<>();
            relationship.add(new CodeableConcept(List.of(new Coding(CONTACT_ROLE, "N")), null));
            if (kin.relationship() != null) {
                relationship.add(new CodeableConcept(null, kin.relationship()));
            }
            return new Contact(relationship, new ContactName(kin.name()),
                    ContactPoint.of(kin.phone(), null));
        }
    }

    /**
     * A FHIR {@code Extension}: a {@code valueString}, a {@code valueDate}, or the extensions
     * nested in it.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Extension(String url, String valueString, String valueDate,
            List<Extension> extension) {

        /** The military service extension: its service records in order, then its dates. */
        static Extension military(MilitaryHistory service) {
            List<Extension> parts = new ArrayList<>();
            service.serviceRecords().forEach(record -> parts.add(new Extension("serviceRecord",
                    null, null, Stream.of(
                            string("serviceType", record.serviceType()),
                            string("rank", record.rank()),
                            string("unit", record.unit()),
                            string("service", record.service()),
                            string("vocation", record.vocation()))
                            .flatMap(Stream::ofNullable).toList())));
            Stream.of(
                    date("enlistmentDate", service.enlistmentDate()),
                    date("ordDate", service.ordDate()),
                    date("mrDate", service.mrDate()),
                    date("rodDate", service.rodDate()))
                    .flatMap(Stream::ofNullable).forEach(parts::add);
            return new Extension(MILITARY_SERVICE, null, null, parts);
        }

        /** The extension naming {@code element}, received in the wrong format. */
        static Extension invalidField(String element) {
            return new Extension(INVALID_FIELD, element, null, null);
        }

        private static Extension string(String url, String value) {
            return value == null ? null : new Extension(url, value, null, null);
        }

        private static Extension date(String url, LocalDate value) {
            return value == null ? null : new Extension(url, null, value.toString(), null);
        }
    }
}
