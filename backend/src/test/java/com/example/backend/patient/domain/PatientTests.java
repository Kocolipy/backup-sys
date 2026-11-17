package com.example.backend.patient.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * What a Patient holds — its id, and its NRIC, official name's text, gender, birth date and
 * Demographic details each as received (ADR 0020), none refused for what it holds — which of them
 * it names as invalid, and
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
        Demographics details = new Demographics(SourceField.present("+65 9123 4567"),
                SourceField.missing(), SourceField.missing(), null, null);

        assertThat(tan.withDemographics(details)).isEqualTo(new Patient(ID,
                SourceField.missing(), NAME, MALE, SourceField.invalid(), details));
    }

    /** {@code fields} with the one at {@code at} left {@code null}, the rest missing. */
    private static SourceField<?>[] missingBut(int count, int at) {
        SourceField<?>[] fields = new SourceField<?>[count];
        for (int place = 0; place < count; place++) {
            fields[place] = place == at ? null : SourceField.missing();
        }
        return fields;
    }

    @SuppressWarnings("unchecked")
    private static <T> SourceField<T> field(SourceField<?>[] fields, int at) {
        return (SourceField<T>) fields[at];
    }

    @ParameterizedTest
    @CsvSource({"0, phone", "1, email", "2, residentialAddress"})
    void demographicsHoldEachContactDetailAsReceived(int at, String name) {
        SourceField<?>[] fields = missingBut(3, at);
        assertThatThrownBy(() -> new Demographics(field(fields, 0), field(fields, 1),
                field(fields, 2), null, null))
                .isInstanceOf(NullPointerException.class).hasMessage(name);
    }

    @ParameterizedTest
    @CsvSource({"0, name", "1, relationship", "2, phone"})
    void aNextOfKinHoldsEachDetailAsReceived(int at, String name) {
        SourceField<?>[] fields = missingBut(3, at);
        assertThatThrownBy(() -> new NextOfKin(field(fields, 0), field(fields, 1),
                field(fields, 2)))
                .isInstanceOf(NullPointerException.class).hasMessage(name);
    }

    @ParameterizedTest
    @CsvSource({"0, serviceType", "1, rank", "2, unit", "3, service", "4, vocation"})
    void aServiceRecordHoldsEachFieldAsReceived(int at, String name) {
        SourceField<?>[] fields = missingBut(5, at);
        assertThatThrownBy(() -> new MilitaryHistory.ServiceRecord(field(fields, 0),
                field(fields, 1), field(fields, 2), field(fields, 3), field(fields, 4)))
                .isInstanceOf(NullPointerException.class).hasMessage(name);
    }

    @ParameterizedTest
    @CsvSource({"0, enlistmentDate", "1, ordDate", "2, mrDate", "3, rodDate"})
    void aMilitaryHistoryHoldsEachDateAsReceived(int at, String name) {
        SourceField<?>[] fields = missingBut(4, at);
        assertThatThrownBy(() -> new MilitaryHistory(List.of(), field(fields, 0),
                field(fields, 1), field(fields, 2), field(fields, 3)))
                .isInstanceOf(NullPointerException.class).hasMessage(name);
    }

    @Test
    void aMilitaryHistoryWithNoRecordListHasAnEmptyOne() {
        assertThat(new MilitaryHistory(null, SourceField.missing(), SourceField.missing(),
                SourceField.missing(), SourceField.missing()).serviceRecords()).isEmpty();
    }

    /**
     * The Demographic details are Restricted, as the rest of the Patient is: none of them prints
     * any of its values, and a Patient holding them still prints as its id alone.
     */
    @Test
    void noDemographicDetailPrintsItsValues() {
        MilitaryHistory.ServiceRecord record = new MilitaryHistory.ServiceRecord(
                SourceField.present("NSman"), SourceField.present("CPT"),
                SourceField.present("3 SIR"), SourceField.present("Army"),
                SourceField.present("Infantry Officer"));
        MilitaryHistory service = new MilitaryHistory(List.of(record), BIRTH_DATE, BIRTH_DATE,
                BIRTH_DATE, BIRTH_DATE);
        NextOfKin kin = new NextOfKin(SourceField.present("Mdm Lee Siew Kuen"),
                SourceField.present("Mother"), SourceField.present("+65 8234 5678"));
        Demographics details = new Demographics(SourceField.present("+65 9123 4567"),
                SourceField.present("tan.hw@email.com"),
                SourceField.present("Blk 123 Bishan Street 12"), kin, service);
        Patient tan = new Patient(ID, "S1234567A", NAME, AdministrativeGender.MALE, BORN, details);

        assertThat(List.of(record, service, kin, details, tan)).extracting(Object::toString)
                .containsExactly("ServiceRecord[redacted]", "MilitaryHistory[redacted]",
                        "NextOfKin[redacted]", "Demographics[redacted]",
                        "Patient[id=eq081-VQEgP8drUUqCWzHfw3]");
    }

    // ---- Demographic details in the wrong format ----------------------------------------------

    private static final SourceField<String> MISSING = SourceField.missing();
    private static final SourceField<String> INVALID = SourceField.invalid();
    private static final SourceField<LocalDate> INVALID_DAY = SourceField.invalid();

    /** A service record with only {@code rank} and {@code vocation} received, each as given. */
    private static MilitaryHistory.ServiceRecord recordOf(SourceField<String> rank,
            SourceField<String> vocation) {
        return new MilitaryHistory.ServiceRecord(MISSING, rank, MISSING, MISSING, vocation);
    }

    private static Patient tanWith(Demographics details) {
        return new Patient(ID, NRIC, NAME, MALE, BIRTH_DATE, details);
    }

    /**
     * A Patient names each Demographic detail whose format was wrong after its four elements, by
     * its path in the R5 Patient served: a contact point, the address and the next of kin by
     * their place, a service record's field and a service date by the url they are served under.
     */
    @Test
    void aPatientNamesItsInvalidDemographicDetailsByTheirPathInTheResource() {
        Demographics details = new Demographics(INVALID, INVALID, INVALID,
                new NextOfKin(INVALID, INVALID, INVALID),
                new MilitaryHistory(List.of(recordOf(INVALID, SourceField.present("Signaller")),
                        recordOf(SourceField.present("CPT"), INVALID)),
                        INVALID_DAY, SourceField.missing(), INVALID_DAY, BIRTH_DATE));
        Patient invalid = new Patient(ID, NRIC, NAME, MALE, SourceField.invalid(), details);

        assertThat(invalid.invalid()).containsExactly(
                "birthDate",
                "telecom[0].value",
                "telecom[1].value",
                "address[0].text",
                "contact[0].name.text",
                "contact[0].relationship[1].text",
                "contact[0].telecom[0].value",
                "extension[0].extension[0].extension('rank').valueString",
                "extension[0].extension[1].extension('vocation').valueString",
                "extension[0].extension('enlistmentDate').valueDate",
                "extension[0].extension('mrDate').valueDate");
    }

    /** With no phone served, the email is the first contact point. */
    @Test
    void anInvalidEmailWithNoPhoneIsTheFirstContactPoint() {
        assertThat(tanWith(new Demographics(MISSING, INVALID, MISSING, null, null)).invalid())
                .containsExactly("telecom[0].value");
    }

    /** A phone received as it should be is served first, so the email follows it. */
    @Test
    void anInvalidEmailAfterAPresentPhoneIsTheSecondContactPoint() {
        assertThat(tanWith(new Demographics(SourceField.present("+65 9123 4567"), INVALID,
                MISSING, null, null)).invalid()).containsExactly("telecom[1].value");
    }

    /** A phone received in the wrong format is served by its system, so the email follows it. */
    @Test
    void anInvalidEmailAfterAnInvalidPhoneIsTheSecondContactPoint() {
        assertThat(tanWith(new Demographics(INVALID, INVALID, MISSING, null, null)).invalid())
                .containsExactly("telecom[0].value", "telecom[1].value");
    }

    /** A Patient whose Demographic details are each present or missing names none of them. */
    @Test
    void aPatientWithNoInvalidDemographicDetailNamesNone() {
        Demographics details = new Demographics(SourceField.present("+65 9123 4567"), MISSING,
                MISSING, new NextOfKin(MISSING, SourceField.present("Mother"), MISSING),
                new MilitaryHistory(List.of(recordOf(MISSING, MISSING)), BIRTH_DATE,
                        SourceField.missing(), SourceField.missing(), SourceField.missing()));

        assertThat(tanWith(details).invalid()).isEmpty();
    }

    /**
     * Each invalid detail's name, as the counter tags it, is the same for every Patient and every
     * service record.
     */
    @Test
    void eachInvalidDemographicDetailIsTaggedByItsOwnName() {
        Demographics details = new Demographics(INVALID, INVALID, INVALID,
                new NextOfKin(INVALID, INVALID, INVALID),
                new MilitaryHistory(List.of(
                        new MilitaryHistory.ServiceRecord(INVALID, INVALID, INVALID, INVALID,
                                INVALID),
                        recordOf(INVALID, MISSING)),
                        INVALID_DAY, INVALID_DAY, INVALID_DAY, INVALID_DAY));

        assertThat(details.invalidDetails()).extracting(Demographics.Detail::tag).containsExactly(
                "phone", "email", "residentialAddress", "nextOfKin.name",
                "nextOfKin.relationship", "nextOfKin.phone", "serviceRecord.serviceType",
                "serviceRecord.rank", "serviceRecord.unit", "serviceRecord.service",
                "serviceRecord.vocation", "serviceRecord.rank", "enlistmentDate", "ordDate",
                "mrDate", "rodDate");
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
