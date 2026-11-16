package com.example.backend.patient.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * What a Patient holds — its id, and its NRIC, official name's text, gender and birth date each
 * as received (ADR 0020), none refused for what it holds — which of them it names as invalid, and
 * what it prints as: its id, and none of the Restricted fields a record's generated
 * {@code toString} would print.
 */
class PatientTests {

    private static final PatientId ID = new PatientId("eq081-VQEgP8drUUqCWzHfw3");
    private static final PatientName NAME = new PatientName("Tan Wei Ming", null, List.of());
    private static final LocalDate BORN = LocalDate.of(1985, 3, 12);

    private static final SourceField<String> NRIC = SourceField.present("S1234567A");
    private static final SourceField<String> MALE = SourceField.present("male");
    private static final SourceField<LocalDate> BIRTH_DATE = SourceField.present(BORN);

    @Test
    void aPatientNeedsAnId() {
        assertThatThrownBy(() -> new Patient(null, NRIC, NAME, MALE, BIRTH_DATE, Demographics.NONE))
                .isInstanceOf(NullPointerException.class).hasMessage("id");
    }

    @Test
    void aPatientHoldsItsNricAsReceived() {
        assertThatThrownBy(() -> new Patient(ID, null, NAME, MALE, BIRTH_DATE, Demographics.NONE))
                .isInstanceOf(NullPointerException.class).hasMessage("nric");
    }

    @Test
    void aPatientHoldsItsGenderAsReceived() {
        assertThatThrownBy(() -> new Patient(ID, NRIC, NAME, null, BIRTH_DATE, Demographics.NONE))
                .isInstanceOf(NullPointerException.class).hasMessage("gender");
    }

    @Test
    void aPatientHoldsItsBirthDateAsReceived() {
        assertThatThrownBy(() -> new Patient(ID, NRIC, NAME, MALE, null, Demographics.NONE))
                .isInstanceOf(NullPointerException.class).hasMessage("birthDate");
    }

    /** The source is the truth: a Patient with every element missing is still a Patient. */
    @Test
    void aPatientMayHoldNoneOfItsElements() {
        Patient bare = new Patient(ID, SourceField.missing(), null, SourceField.missing(),
                SourceField.missing(), Demographics.NONE);

        assertThat(bare.invalid()).isEmpty();
    }

    /** A gender outside the four codes is kept as it came. */
    @Test
    void aPatientKeepsAGenderOutsideTheFourCodes() {
        assertThat(new Patient(ID, NRIC, NAME, SourceField.present("M"), BIRTH_DATE,
                Demographics.NONE).gender()).isEqualTo(SourceField.present("M"));
    }

    /**
     * A Patient names each element whose format was wrong by its path in the R5 Patient served,
     * in the resource's order.
     */
    @Test
    void aPatientNamesItsInvalidElementsByTheirPathInTheResource() {
        Patient invalid = new Patient(ID, SourceField.invalid(),
                new PatientName(SourceField.invalid(), "Tan", List.of()), SourceField.invalid(),
                SourceField.invalid(), Demographics.NONE);

        assertThat(invalid.invalid())
                .containsExactly("identifier[0].value", "name[0].text", "gender", "birthDate");
    }

    @Test
    void aPatientWithOneInvalidElementNamesThatOneAlone() {
        Patient invalid = new Patient(ID, NRIC, NAME, MALE, SourceField.invalid(),
                Demographics.NONE);

        assertThat(invalid.invalid()).containsExactly("birthDate");
    }

    /** The five elements as the source should send them are each present. */
    @Test
    void aPatientOfItsFiveElementsHoldsEachPresent() {
        assertThat(new Patient(ID, "S1234567A", NAME, AdministrativeGender.MALE, BORN))
                .isEqualTo(new Patient(ID, NRIC, NAME, MALE, BIRTH_DATE, Demographics.NONE));
    }

    @Test
    void aNameHoldsItsTextAsReceived() {
        assertThatThrownBy(() -> new PatientName((SourceField<String>) null, "Tan", List.of("Wei")))
                .isInstanceOf(NullPointerException.class).hasMessage("text");
    }

    @Test
    void aNameOfItsTextIsPresent() {
        assertThat(new PatientName("Suriani", null, null).text())
                .isEqualTo(SourceField.present("Suriani"));
    }

    @Test
    void aNameWithNoGivenListHasAnEmptyOne() {
        assertThat(new PatientName("Suriani", null, null).given()).isEmpty();
    }

    @Test
    void aNameKeepsItsGivenNamesWhateverTheCallerDoesToItsList() {
        List<String> given = new ArrayList<>(List.of("Wei", "Ming"));
        PatientName name = new PatientName("Tan Wei Ming", "Tan", given);

        given.clear();

        assertThat(name.given()).containsExactly("Wei", "Ming");
    }

    @Test
    void aPatientPrintsAsItsIdAlone() {
        Patient tan = new Patient(ID, "S1234567A",
                new PatientName("Tan Wei Ming", "Tan", List.of("Wei", "Ming")),
                AdministrativeGender.MALE, BORN);

        assertThat(tan.toString()).isEqualTo("Patient[id=eq081-VQEgP8drUUqCWzHfw3]");
    }

    @Test
    void aPatientNeedsItsDemographicDetails() {
        assertThatThrownBy(() -> new Patient(ID, "S1234567A", NAME, AdministrativeGender.MALE, BORN,
                null)).isInstanceOf(NullPointerException.class).hasMessage("demographics");
    }

    @Test
    void aPatientOfItsFiveElementsHasNoDemographicDetails() {
        assertThat(new Patient(ID, "S1234567A", NAME, AdministrativeGender.MALE, BORN).demographics())
                .isEqualTo(Demographics.NONE);
    }

    @Test
    void aPatientWithDemographicDetailsKeepsItsFiveElements() {
        Patient tan = new Patient(ID, SourceField.missing(), NAME, MALE, SourceField.invalid(),
                Demographics.NONE);
        Demographics details = new Demographics("+65 9123 4567", null, null, null, null);

        assertThat(tan.withDemographics(details)).isEqualTo(new Patient(ID,
                SourceField.missing(), NAME, MALE, SourceField.invalid(), details));
    }

    @Test
    void aNextOfKinNeedsAName() {
        assertThatThrownBy(() -> new NextOfKin(null, "Mother", null))
                .isInstanceOf(NullPointerException.class).hasMessage("name");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void aNextOfKinsNameCannotBeBlank(String blank) {
        assertThatThrownBy(() -> new NextOfKin(blank, "Mother", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A next of kin's name is required");
    }

    @Test
    void aMilitaryHistoryWithNoRecordListHasAnEmptyOne() {
        assertThat(new MilitaryHistory(null, null, null, null, null).serviceRecords()).isEmpty();
    }

    /**
     * The Demographic details are Restricted, as the rest of the Patient is: none of them prints
     * any of its values, and a Patient holding them still prints as its id alone.
     */
    @Test
    void noDemographicDetailPrintsItsValues() {
        MilitaryHistory.ServiceRecord record =
                new MilitaryHistory.ServiceRecord("NSman", "CPT", "3 SIR", "Army", "Infantry Officer");
        MilitaryHistory service = new MilitaryHistory(List.of(record), BORN, BORN, BORN, BORN);
        NextOfKin kin = new NextOfKin("Mdm Lee Siew Kuen", "Mother", "+65 8234 5678");
        Demographics details = new Demographics("+65 9123 4567", "tan.hw@email.com",
                "Blk 123 Bishan Street 12", kin, service);
        Patient tan = new Patient(ID, "S1234567A", NAME, AdministrativeGender.MALE, BORN, details);

        assertThat(List.of(record, service, kin, details, tan)).extracting(Object::toString)
                .containsExactly("ServiceRecord[redacted]", "MilitaryHistory[redacted]",
                        "NextOfKin[redacted]", "Demographics[redacted]",
                        "Patient[id=eq081-VQEgP8drUUqCWzHfw3]");
    }

    @ParameterizedTest
    @ValueSource(strings = {"male", "female", "other", "unknown"})
    void eachFhirGenderCodeNamesItsGender(String code) {
        assertThat(AdministrativeGender.fromCode(code)).map(AdministrativeGender::code).contains(code);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Male", "M", "", "intersex"})
    void anythingElseNamesNoGender(String code) {
        assertThat(AdministrativeGender.fromCode(code)).isEmpty();
    }
}
