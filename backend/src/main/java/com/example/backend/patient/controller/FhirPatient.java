package com.example.backend.patient.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.example.backend.patient.domain.Demographics;
import com.example.backend.patient.domain.MilitaryHistory;
import com.example.backend.patient.domain.NextOfKin;
import com.example.backend.patient.domain.Patient;
import com.example.backend.patient.domain.PatientName;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * A Patient as a FHIR R5 {@code Patient} resource, in the JSON shape Jackson writes this record
 * in: base R5, no {@code meta} and so no {@code meta.profile}, and no element beyond the five the
 * domain holds and its Demographic details (/docs/domain/patient.md). An element the Patient has
 * none of is left out, never written empty. The service renders FHIR itself from its own records;
 * no FHIR library is on the runtime classpath.
 *
 * @param resourceType always {@code Patient}
 * @param id           the patient's id, its EHR's FHIR id
 * @param extension    the military service extension, when the Patient has one
 * @param identifier   exactly one: the NRIC, under Synapxe's NRIC NamingSystem
 *                     ({@link Patient#NRIC_SYSTEM})
 * @param name         exactly one: the official name
 * @param telecom      the phone, then the email, each when the Patient has one
 * @param gender       the administrative gender code
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

    /** The resource {@code patient} renders as. */
    public static FhirPatient of(Patient patient) {
        Demographics details = patient.demographics();
        return new FhirPatient(
                "Patient",
                patient.id().value(),
                Stream.ofNullable(details.militaryHistory()).map(Extension::military).toList(),
                List.of(new Identifier(Patient.NRIC_SYSTEM, patient.nric())),
                List.of(HumanName.official(patient.name())),
                ContactPoint.of(details.phone(), details.email()),
                patient.gender().code(),
                patient.birthDate().toString(),
                Stream.ofNullable(details.residentialAddress())
                        .map(text -> new Address("home", text)).toList(),
                Stream.ofNullable(details.nextOfKin()).map(Contact::of).toList());
    }

    /** A FHIR {@code Identifier}: a value and the system it is unique within. */
    public record Identifier(String system, String value) {
    }

    /**
     * A FHIR {@code HumanName}. {@code text} is always there and authoritative; {@code family}
     * and {@code given} are left out, not written empty, when the name is not split.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record HumanName(String use, String text, String family, List<String> given) {

        static HumanName official(PatientName name) {
            return new HumanName("official", name.text(), name.family(), name.given());
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

        private static Extension string(String url, String value) {
            return value == null ? null : new Extension(url, value, null, null);
        }

        private static Extension date(String url, LocalDate value) {
            return value == null ? null : new Extension(url, null, value.toString(), null);
        }
    }
}
