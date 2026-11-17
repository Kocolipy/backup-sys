package com.example.backend.patient.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.backend.patient.domain.AdministrativeGender;
import com.example.backend.patient.domain.Demographics;
import com.example.backend.patient.domain.MilitaryHistory;
import com.example.backend.patient.domain.NextOfKin;
import com.example.backend.patient.domain.Patient;
import com.example.backend.patient.domain.PatientId;
import com.example.backend.patient.domain.PatientName;
import com.example.backend.patient.domain.SourceField;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A Patient as {@link FhirPatient} renders it, for the shapes no fixture holds: elements and
 * Demographic details missing or received in the wrong format (ADR 0020), a gender outside the
 * four codes, a service record lacking fields, every service date, and a Next of kin with no
 * relationship text or phone. Each part a Patient lacks is left out of the JSON, never written
 * empty or null. Whole Patients as the endpoint serves them are {@code PatientControllerTests}'.
 */
class FhirPatientTests {

    private final JsonMapper json = JsonMapper.builder().build();

    private static final LocalDate DAY = LocalDate.of(2020, 1, 14);

    private JsonNode rendered(Demographics details) {
        Patient patient = new Patient(new PatientId("probe-1"), "T7391046Q",
                new PatientName("Quah Seow Peng", null, List.of()), AdministrativeGender.FEMALE,
                LocalDate.of(1963, 7, 29), details);
        return json.valueToTree(FhirPatient.of(patient));
    }

    private static final SourceField<String> MISSING = SourceField.missing();

    private static final SourceField<String> INVALID = SourceField.invalid();

    private static SourceField<String> present(String value) {
        return SourceField.present(value);
    }

    private static Demographics militaryAlone(MilitaryHistory history) {
        return new Demographics(MISSING, MISSING, MISSING, null, history);
    }

    @Test
    void aServiceRecordIsRenderedWithOnlyTheFieldsItHoldsAndEveryDateInOrder() {
        MilitaryHistory history = new MilitaryHistory(
                List.of(new MilitaryHistory.ServiceRecord(MISSING, present("CPT"), INVALID,
                        present("Army"), MISSING)),
                SourceField.present(DAY), SourceField.present(DAY.plusDays(1)),
                SourceField.present(DAY.plusDays(2)), SourceField.present(DAY.plusDays(3)));

        assertThat(rendered(militaryAlone(history)).get("extension"))
                .isEqualTo(json.readTree("""
                        [{"url": "http://example.org/fhir/StructureDefinition/military-service",
                          "extension": [
                            {"url": "serviceRecord", "extension": [
                              {"url": "rank", "valueString": "CPT"},
                              {"url": "service", "valueString": "Army"}]},
                            {"url": "enlistmentDate", "valueDate": "2020-01-14"},
                            {"url": "ordDate", "valueDate": "2020-01-15"},
                            {"url": "mrDate", "valueDate": "2020-01-16"},
                            {"url": "rodDate", "valueDate": "2020-01-17"}]},
                         {"url": "http://example.org/fhir/StructureDefinition/invalid-field",
                          "valueString": "extension[0].extension[0].extension('unit').valueString"}]"""));
    }

    @Test
    void aNextOfKinWithNoRelationshipTextOrPhoneIsRenderedByTheirRoleAndNameAlone() {
        NextOfKin kin = new NextOfKin(present("Mdm Lee Siew Kuen"), MISSING, MISSING);

        assertThat(rendered(new Demographics(MISSING, MISSING, MISSING, kin, null)).get("contact"))
                .isEqualTo(json.readTree("""
                        [{"relationship": [{"coding": [
                            {"system": "http://terminology.hl7.org/CodeSystem/v2-0131",
                             "code": "N"}]}],
                          "name": {"text": "Mdm Lee Siew Kuen"}}]"""));
    }

    /**
     * A Patient with every element missing is its id alone: no element is written empty, and none
     * is named invalid.
     */
    @Test
    void aPatientWithEveryElementMissingIsItsIdAlone() {
        Patient bare = new Patient(new PatientId("probe-1"), SourceField.missing(), null,
                SourceField.missing(), SourceField.missing(), Demographics.NONE);

        assertThat(json.<JsonNode>valueToTree(FhirPatient.of(bare))).isEqualTo(json.readTree("""
                {"resourceType": "Patient", "id": "probe-1"}"""));
    }

    /**
     * An element received in the wrong format is left out — the NRIC's identifier keeps its
     * system, the official name its use and parts — and named instead by one extension each, by
     * its path in the resource, after the military service.
     */
    @Test
    void anElementInTheWrongFormatIsLeftOutAndNamedByAnExtension() {
        Patient invalid = new Patient(new PatientId("probe-1"), SourceField.invalid(),
                new PatientName(SourceField.invalid(), "Quah", List.of("Seow Peng")),
                SourceField.invalid(), SourceField.invalid(),
                militaryAlone(new MilitaryHistory(List.of(), SourceField.present(DAY),
                        SourceField.missing(), SourceField.missing(), SourceField.missing())));

        assertThat(json.<JsonNode>valueToTree(FhirPatient.of(invalid))).isEqualTo(json.readTree("""
                {"resourceType": "Patient", "id": "probe-1",
                 "extension": [
                   {"url": "http://example.org/fhir/StructureDefinition/military-service",
                    "extension": [{"url": "enlistmentDate", "valueDate": "2020-01-14"}]},
                   {"url": "http://example.org/fhir/StructureDefinition/invalid-field",
                    "valueString": "identifier[0].value"},
                   {"url": "http://example.org/fhir/StructureDefinition/invalid-field",
                    "valueString": "name[0].text"},
                   {"url": "http://example.org/fhir/StructureDefinition/invalid-field",
                    "valueString": "gender"},
                   {"url": "http://example.org/fhir/StructureDefinition/invalid-field",
                    "valueString": "birthDate"}],
                 "identifier": [{"system": "https://fhir.synapxe.sg/NamingSystem/nric"}],
                 "name": [{"use": "official", "family": "Quah", "given": ["Seow Peng"]}]}"""));
    }

    /** A gender outside the four codes is written as the code it came as. */
    @Test
    void aGenderOutsideTheFourIsWrittenAsItsCode() {
        Patient patient = new Patient(new PatientId("probe-1"), SourceField.missing(), null,
                SourceField.present("M"), SourceField.missing(), Demographics.NONE);

        assertThat(json.valueToTree(FhirPatient.of(patient)).get("gender").asString())
                .isEqualTo("M");
    }

    @Test
    void aResidentialAddressIsTheOneHomeAddressAsItsText() {
        assertThat(rendered(new Demographics(MISSING, MISSING, present("2 Home Road"), null,
                null)).get("address")).isEqualTo(json.readTree("""
                        [{"use": "home", "text": "2 Home Road"}]"""));
    }

    @Test
    void anEmailWithNoPhoneIsTheOneContactPoint() {
        assertThat(rendered(new Demographics(MISSING, present("q@example.org"), MISSING, null,
                null)).get("telecom")).isEqualTo(json.readTree("""
                        [{"system": "email", "value": "q@example.org"}]"""));
    }

    /**
     * A Demographic detail received in the wrong format is left out — a contact point keeps its
     * system, the home address its use, the next of kin their coding — and named instead by one
     * extension each, by its path in the resource, after the four elements'.
     */
    @Test
    void aDemographicDetailInTheWrongFormatIsLeftOutAndNamedByAnExtension() {
        Demographics details = new Demographics(INVALID, INVALID, INVALID,
                new NextOfKin(INVALID, INVALID, INVALID),
                new MilitaryHistory(List.of(new MilitaryHistory.ServiceRecord(
                        present("NSF"), INVALID, MISSING, MISSING, MISSING)),
                        SourceField.invalid(), SourceField.missing(), SourceField.missing(),
                        SourceField.present(DAY)));

        assertThat(rendered(details)).isEqualTo(json.readTree("""
                {"resourceType": "Patient", "id": "probe-1",
                 "extension": [
                   {"url": "http://example.org/fhir/StructureDefinition/military-service",
                    "extension": [
                      {"url": "serviceRecord", "extension": [
                        {"url": "serviceType", "valueString": "NSF"}]},
                      {"url": "rodDate", "valueDate": "2020-01-14"}]},
                   {"url": "http://example.org/fhir/StructureDefinition/invalid-field",
                    "valueString": "telecom[0].value"},
                   {"url": "http://example.org/fhir/StructureDefinition/invalid-field",
                    "valueString": "telecom[1].value"},
                   {"url": "http://example.org/fhir/StructureDefinition/invalid-field",
                    "valueString": "address[0].text"},
                   {"url": "http://example.org/fhir/StructureDefinition/invalid-field",
                    "valueString": "contact[0].name.text"},
                   {"url": "http://example.org/fhir/StructureDefinition/invalid-field",
                    "valueString": "contact[0].relationship[1].text"},
                   {"url": "http://example.org/fhir/StructureDefinition/invalid-field",
                    "valueString": "contact[0].telecom[0].value"},
                   {"url": "http://example.org/fhir/StructureDefinition/invalid-field",
                    "valueString": "extension[0].extension[0].extension('rank').valueString"},
                   {"url": "http://example.org/fhir/StructureDefinition/invalid-field",
                    "valueString": "extension[0].extension('enlistmentDate').valueDate"}],
                 "identifier": [{"system": "https://fhir.synapxe.sg/NamingSystem/nric",
                                 "value": "T7391046Q"}],
                 "name": [{"use": "official", "text": "Quah Seow Peng"}],
                 "telecom": [{"system": "phone"}, {"system": "email"}],
                 "gender": "female",
                 "birthDate": "1963-07-29",
                 "address": [{"use": "home"}],
                 "contact": [{"relationship": [{"coding": [
                                 {"system": "http://terminology.hl7.org/CodeSystem/v2-0131",
                                  "code": "N"}]}],
                              "telecom": [{"system": "phone"}]}]}"""));
    }

    /**
     * A Demographic detail that is missing is left out with nothing naming it: no contact point,
     * no address, and a next of kin with neither a name nor a phone is their coding alone.
     */
    @Test
    void aMissingDemographicDetailIsLeftOutUnnamed() {
        Demographics details = new Demographics(MISSING, MISSING, MISSING,
                new NextOfKin(MISSING, present("Mother"), MISSING), null);

        assertThat(rendered(details)).isEqualTo(json.readTree("""
                {"resourceType": "Patient", "id": "probe-1",
                 "identifier": [{"system": "https://fhir.synapxe.sg/NamingSystem/nric",
                                 "value": "T7391046Q"}],
                 "name": [{"use": "official", "text": "Quah Seow Peng"}],
                 "gender": "female",
                 "birthDate": "1963-07-29",
                 "contact": [{"relationship": [
                                 {"coding": [
                                   {"system": "http://terminology.hl7.org/CodeSystem/v2-0131",
                                    "code": "N"}]},
                                 {"text": "Mother"}]}]}"""));
    }
}
