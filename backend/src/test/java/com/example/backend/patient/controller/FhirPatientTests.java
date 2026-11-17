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
 * A Patient as {@link FhirPatient} renders it, for the shapes no fixture holds: elements missing
 * or received in the wrong format (ADR 0020), a gender outside the four codes, a service record
 * lacking fields, every service date, and a Next of kin with no relationship text or phone. Each
 * part a Patient lacks is left out of the JSON, never written empty or null. Whole Patients as
 * the endpoint serves them are {@code PatientControllerTests}'.
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

    @Test
    void aServiceRecordIsRenderedWithOnlyTheFieldsItHoldsAndEveryDateInOrder() {
        MilitaryHistory history = new MilitaryHistory(
                List.of(new MilitaryHistory.ServiceRecord(null, "CPT", null, "Army", null)),
                DAY, DAY.plusDays(1), DAY.plusDays(2), DAY.plusDays(3));

        assertThat(rendered(new Demographics(null, null, null, null, history)).get("extension"))
                .isEqualTo(json.readTree("""
                        [{"url": "http://example.org/fhir/StructureDefinition/military-service",
                          "extension": [
                            {"url": "serviceRecord", "extension": [
                              {"url": "rank", "valueString": "CPT"},
                              {"url": "service", "valueString": "Army"}]},
                            {"url": "enlistmentDate", "valueDate": "2020-01-14"},
                            {"url": "ordDate", "valueDate": "2020-01-15"},
                            {"url": "mrDate", "valueDate": "2020-01-16"},
                            {"url": "rodDate", "valueDate": "2020-01-17"}]}]"""));
    }

    @Test
    void aNextOfKinWithNoRelationshipTextOrPhoneIsRenderedByTheirRoleAndNameAlone() {
        NextOfKin kin = new NextOfKin("Mdm Lee Siew Kuen", null, null);

        assertThat(rendered(new Demographics(null, null, null, kin, null)).get("contact"))
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
                new Demographics(null, null, null, null,
                        new MilitaryHistory(List.of(), DAY, null, null, null)));

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
    void anEmailWithNoPhoneIsTheOneContactPoint() {
        assertThat(rendered(new Demographics(null, "q@example.org", null, null, null))
                .get("telecom")).isEqualTo(json.readTree("""
                        [{"system": "email", "value": "q@example.org"}]"""));
    }
}
