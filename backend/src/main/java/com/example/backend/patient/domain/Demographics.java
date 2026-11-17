package com.example.backend.patient.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * A Patient's Demographic details, as a read by id copies them from the in-house patient source
 * (/docs/domain/patient.md, "Demographic details"): how they are reached, their next of kin and
 * their military service. Each detail is shown as received (ADR 0020) — present, missing or in
 * the wrong format, independently of the others — and none is refused for what it holds. A
 * Patient with no contact coded as their next of kin has no {@code nextOfKin}, and one with no
 * military service extension no {@code militaryHistory}.
 *
 * <p>Restricted, as the rest of the Patient is: {@link #toString()} prints none of it.
 *
 * @param phone              the first phone the source lists
 * @param email              the first email the source lists
 * @param residentialAddress the text of the first {@code home} address
 * @param nextOfKin          the first contact coded as the Patient's next of kin, or
 *                           {@code null} when there is none
 * @param militaryHistory    the Patient's military service, or {@code null} when the source
 *                           holds none
 */
public record Demographics(SourceField<String> phone, SourceField<String> email,
        SourceField<String> residentialAddress, NextOfKin nextOfKin,
        MilitaryHistory militaryHistory) {

    /** A Patient the source holds no Demographic details for. */
    public static final Demographics NONE = new Demographics(SourceField.missing(),
            SourceField.missing(), SourceField.missing(), null, null);

    public Demographics {
        Objects.requireNonNull(phone, "phone");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(residentialAddress, "residentialAddress");
    }

    /**
     * One Demographic detail, as {@link #invalidDetails()} names it: a contact detail, a field of
     * one service record, or a service date.
     */
    public sealed interface Detail permits Contact, ServiceRecordField, ServiceDateField {

        /**
         * The detail's name, the same for every Patient and every service record — {@code phone},
         * {@code serviceRecord.rank} — as the {@code patient_source.invalid_fields} counter tags
         * it.
         */
        String tag();
    }

    /**
     * A detail of how the Patient, or their next of kin, is reached, each with the field it names
     * and where it sits in the R5 {@code Patient} the service serves (/docs/domain/patient.md,
     * "Shown as received"): the phone first among the contact points, then the email; the one
     * home address; the one contact, the next of kin, their relationship's text after the coding
     * that marks them, their phone their one contact point. A Patient with no next of kin has
     * none of theirs.
     */
    public enum Contact implements Detail {
        PHONE("phone", Demographics::phone, details -> "telecom[0].value"),
        EMAIL("email", Demographics::email, details -> "telecom["
                + (details.phone().received() == SourceField.Received.MISSING ? 0 : 1)
                + "].value"),
        RESIDENTIAL_ADDRESS("residentialAddress", Demographics::residentialAddress,
                details -> "address[0].text"),
        NEXT_OF_KIN_NAME("nextOfKin.name", details -> details.kin(NextOfKin::name),
                details -> "contact[0].name.text"),
        NEXT_OF_KIN_RELATIONSHIP("nextOfKin.relationship",
                details -> details.kin(NextOfKin::relationship),
                details -> "contact[0].relationship[1].text"),
        NEXT_OF_KIN_PHONE("nextOfKin.phone", details -> details.kin(NextOfKin::phone),
                details -> "contact[0].telecom[0].value");

        private final String tag;
        private final Function<Demographics, SourceField<String>> of;
        private final Function<Demographics, String> path;

        Contact(String tag, Function<Demographics, SourceField<String>> of,
                Function<Demographics, String> path) {
            this.tag = tag;
            this.of = of;
            this.path = path;
        }

        @Override
        public String tag() {
            return tag;
        }
    }

    /** The next of kin's field {@code field} reads, missing when there is no next of kin. */
    private SourceField<String> kin(Function<NextOfKin, SourceField<String>> field) {
        return nextOfKin == null ? SourceField.missing() : field.apply(nextOfKin);
    }

    /**
     * A field of the service record at {@code record}, counted from zero in the source's order.
     *
     * @param record the record's place among the service records
     * @param field  the field
     */
    public record ServiceRecordField(int record, MilitaryHistory.ServiceRecord.Field field)
            implements Detail {

        @Override
        public String tag() {
            return "serviceRecord." + field.url();
        }
    }

    /**
     * A date of the military service.
     *
     * @param date the date
     */
    public record ServiceDateField(MilitaryHistory.ServiceDate date) implements Detail {

        @Override
        public String tag() {
            return date.url();
        }
    }

    /**
     * The details whose format was wrong: the contact details, the next of kin's, each service
     * record's fields in order, then the service dates.
     */
    public List<Detail> invalidDetails() {
        List<Detail> details = new ArrayList<>();
        Stream.of(Contact.values())
                .filter(contact -> isInvalid(contact.of.apply(this)))
                .forEach(details::add);
        if (militaryHistory != null) {
            List<MilitaryHistory.ServiceRecord> records = militaryHistory.serviceRecords();
            IntStream.range(0, records.size()).forEach(record ->
                    Stream.of(MilitaryHistory.ServiceRecord.Field.values())
                            .filter(field -> isInvalid(records.get(record).field(field)))
                            .forEach(field -> details.add(new ServiceRecordField(record, field))));
            Stream.of(MilitaryHistory.ServiceDate.values())
                    .filter(date -> isInvalid(militaryHistory.date(date)))
                    .forEach(date -> details.add(new ServiceDateField(date)));
        }
        return List.copyOf(details);
    }

    /**
     * Where {@code detail} sits in the R5 {@code Patient} the service serves, as an
     * invalid-field extension names it (/docs/domain/patient.md, "Shown as received"): a contact
     * detail where {@link Contact} places it, and, in the military service extension, served
     * first among the extensions, a service record's field and a service date by the url of the
     * sub-extension they would be served under, since one left out has no place of its own.
     */
    public String pathOf(Detail detail) {
        return switch (detail) {
            case Contact contact -> contact.path.apply(this);
            case ServiceRecordField(int record, MilitaryHistory.ServiceRecord.Field field) ->
                    "extension[0].extension[" + record + "].extension('" + field.url()
                            + "').valueString";
            case ServiceDateField(MilitaryHistory.ServiceDate date) ->
                    "extension[0].extension('" + date.url() + "').valueDate";
        };
    }

    private static boolean isInvalid(SourceField<?> field) {
        return field.received() == SourceField.Received.INVALID;
    }

    @Override
    public String toString() {
        return "Demographics[redacted]";
    }
}
