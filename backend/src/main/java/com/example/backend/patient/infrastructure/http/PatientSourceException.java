package com.example.backend.patient.infrastructure.http;

import com.example.backend.observability.LogEvent.ErrorCategory;
import com.example.backend.observability.OutboundCallFailure;
import com.example.backend.observability.OutboundCallFailure.Reason;
import com.example.backend.observability.UpstreamFaultException;
import java.util.stream.Stream;

/**
 * The in-house patient source could not be read: it gave no answer, an answer other than the
 * read's own — a Patient or a {@code 404}, a search's searchset, or an FFI History's, a NUMC
 * History's, an ARMC History's or a Medication History's searchset or a {@code 404} — or one the
 * service cannot hold. A fault of the source, answered
 * {@code 502} with the {@code exception} outcome (/docs/domain/patient.md, "Errors"), its one
 * fault record naming the {@link Failure} as its {@code event.reason} ({@link #reason()}) under
 * the {@code error.category} ADR 0014 gives an outbound call's failure ({@link #category()}):
 *
 * <ul>
 *   <li>{@code network}: no answer — {@code timed-out} (the connect or the read timeout ran out)
 *       or {@code unreachable} (no connection otherwise: refused, an unknown host, a call
 *       dropped);
 *   <li>{@code server}: the source answered {@code 5xx}, {@code server-error};
 *   <li>{@code data}: the source answered with something the service cannot use — another status
 *       ({@code unexpected-status}), a body that is not JSON or not a Patient
 *       ({@code unreadable}), a Patient breaking one of the domain's rules, a search's answer
 *       or a history that is no searchset, has no total, or holds an entry of another
 *       resource, or one with no id, or a Medication Record missing a field, holding a code
 *       outside its closed set or a date-time with no offset, or ending before it starts, under
 *       a reason naming Medication or an element of its own, so a record says which history
 *       broke. An FFI, a NUMC Record and an ARMC Board break no rule: their histories are shown
 *       as received (ADR 0020), so only their call or their searchset fails.
 * </ul>
 *
 * <p>Its message is fixed text, naming at most a status or an element, and its cause, when it has
 * one, is the original failure redacted to its type: a client's or a parser's message can quote
 * the URL, and so the patient's id, or the body, and so the patient (/docs/domain/patient.md,
 * "Logging").
 */
public final class PatientSourceException extends UpstreamFaultException {

    /**
     * Every way a read of the source fails: its {@code event.reason}, its fixed message, and the
     * {@code error.category} it is reported under. A failure of the call itself is one of
     * {@link OutboundCallFailure}'s reasons, under the category ADR 0014 gives it there; a Patient
     * breaking a domain rule is {@code data}.
     */
    enum Failure {
        /** No connection to the source, other than by a timeout: refused, unknown, or dropped. */
        UNREACHABLE(Reason.UNREACHABLE, "unreachable", "The patient source could not be reached"),
        /** No connection within the connect timeout, or no answer within the read timeout. */
        TIMED_OUT(Reason.TIMED_OUT, "timed-out", "The patient source did not answer in time"),
        /** The source answered {@code 5xx}; the message is completed by the status. */
        SERVER_ERROR(Reason.SERVER_ERROR, "server-error", "The patient source answered"),
        /** Another status, neither a {@code 200}, a {@code 404} nor a {@code 5xx}. */
        UNEXPECTED_STATUS(Reason.UNEXPECTED_STATUS, "unexpected-status",
                "The patient source answered"),
        /** A {@code 200} whose body could not be read as the read's answer at all. */
        UNREADABLE(Reason.UNREADABLE, "unreadable", "The patient source could not be read"),
        ANOTHER_RESOURCE("another-resource", "The patient source answered with another resource",
                ErrorCategory.DATA),
        NO_NRIC("no-nric", "The patient source's Patient has no NRIC", ErrorCategory.DATA),
        NO_OFFICIAL_NAME("no-official-name", "The patient source's Patient has no official name",
                ErrorCategory.DATA),
        NO_NAME_TEXT("no-name-text", "The patient source's Patient's official name has no text",
                ErrorCategory.DATA),
        NO_GENDER("no-gender", "The patient source's Patient has no gender", ErrorCategory.DATA),
        NO_BIRTH_DATE("no-birth-date", "The patient source's Patient has no birth date",
                ErrorCategory.DATA),
        BIRTH_DATE_NOT_TO_THE_DAY("birth-date-not-to-the-day",
                "The patient source's Patient's birth date is not to the day", ErrorCategory.DATA),
        /** A paged read's {@code 200} — a search's or a history's — that is no searchset. */
        NOT_A_SEARCHSET("not-a-searchset", "The patient source's page is no searchset",
                ErrorCategory.DATA),
        /** A searchset with no {@code total}, or a negative one: there is no telling the pages. */
        NO_TOTAL("no-total", "The patient source's page has no total", ErrorCategory.DATA),
        /** A search's Patient with no {@code id}, which no later read could open it by. */
        NO_ID("no-id", "The patient source's Patient has no id", ErrorCategory.DATA),
        /** A search's Patient whose {@code id} is outside FHIR's id grammar. */
        INVALID_ID("invalid-id", "The patient source's Patient's id is not a FHIR id",
                ErrorCategory.DATA),
        /** A search's Patient whose Last visit is no date to the day. */
        LAST_VISIT_NOT_TO_THE_DAY("last-visit-not-to-the-day",
                "The patient source's Patient's last visit is not to the day", ErrorCategory.DATA),
        /** A Medication History's entry with no {@code id}. */
        MEDICATION_NO_ID("medication-no-id", "The patient source's Medication Record has no id",
                ErrorCategory.DATA),
        /** A Medication History's entry whose {@code id} is outside FHIR's id grammar. */
        MEDICATION_INVALID_ID("medication-invalid-id",
                "The patient source's Medication Record's id is not a FHIR id",
                ErrorCategory.DATA),
        /** A Medication Record with no {@code medication}, or a blank one. */
        NO_MEDICATION("no-medication", "The patient source's Medication Record has no medication",
                ErrorCategory.DATA),
        /** A Medication Record with no {@code dispenseStatus}. */
        NO_DISPENSE_STATUS("no-dispense-status",
                "The patient source's Medication Record has no dispense status",
                ErrorCategory.DATA),
        /** A Medication Record whose {@code dispenseStatus} is no Dispense Status's code. */
        UNKNOWN_DISPENSE_STATUS("unknown-dispense-status",
                "The patient source's Medication Record's dispense status is not a Dispense"
                        + " Status",
                ErrorCategory.DATA),
        /** A Medication Record with no {@code startDate}. */
        NO_MEDICATION_START_DATE("no-medication-start-date",
                "The patient source's Medication Record has no start date", ErrorCategory.DATA),
        /** A Medication Record whose Start Date is no date-time with an offset. */
        MEDICATION_START_DATE_NOT_A_DATE_TIME("medication-start-date-not-a-date-time",
                "The patient source's Medication Record's start date is not a date-time with an"
                        + " offset",
                ErrorCategory.DATA),
        /** A Medication Record with no {@code endDate}. */
        NO_MEDICATION_END_DATE("no-medication-end-date",
                "The patient source's Medication Record has no end date", ErrorCategory.DATA),
        /** A Medication Record whose End Date is no date-time with an offset. */
        MEDICATION_END_DATE_NOT_A_DATE_TIME("medication-end-date-not-a-date-time",
                "The patient source's Medication Record's end date is not a date-time with an"
                        + " offset",
                ErrorCategory.DATA),
        /** A Medication Record whose End Date is before its Start Date. */
        MEDICATION_END_BEFORE_START("medication-end-before-start",
                "The patient source's Medication Record ends before it starts",
                ErrorCategory.DATA),
        /** A Medication Record with no {@code dispensary}. */
        NO_DISPENSARY("no-dispensary", "The patient source's Medication Record has no dispensary",
                ErrorCategory.DATA),
        /** A Medication Record whose {@code dispensary} is no Medical Centre's code. */
        UNKNOWN_DISPENSARY("unknown-dispensary",
                "The patient source's Medication Record's dispensary is not a Medical Centre",
                ErrorCategory.DATA),
        /** A Medication Record with no {@code prescribingDoctor}, or a blank one. */
        NO_PRESCRIBING_DOCTOR("no-prescribing-doctor",
                "The patient source's Medication Record has no prescribing doctor",
                ErrorCategory.DATA),
        /** A Patient read by id whose military service has a date that is no date to the day. */
        SERVICE_DATE_NOT_TO_THE_DAY("service-date-not-to-the-day",
                "The patient source's Patient's military service date is not to the day",
                ErrorCategory.DATA);

        /** The call's own failure this is, or {@code null} for a broken domain rule. */
        private final Reason outbound;

        private final String reason;

        private final String message;

        private final ErrorCategory category;

        Failure(Reason outbound, String reason, String message) {
            this(outbound, reason, message, outbound.category());
        }

        Failure(String reason, String message, ErrorCategory category) {
            this(null, reason, message, category);
        }

        private Failure(Reason outbound, String reason, String message, ErrorCategory category) {
            this.outbound = outbound;
            this.reason = reason;
            this.message = message;
            this.category = category;
        }

        /**
         * The failure the call's own {@code outbound} failure is. The source is sent no
         * credential, so {@link Reason#CREDENTIAL_REFUSED} is none of them: a {@code 401} is
         * answered as the status it is, and reaches here only by a defect, which this fails on.
         */
        static Failure of(Reason outbound) {
            return Stream.of(values()).filter(failure -> failure.outbound == outbound)
                    .findFirst().orElseThrow();
        }
    }

    private PatientSourceException(Failure failure, String message, Throwable redactedCause) {
        super(message, failure.category, failure.reason, redactedCause);
    }

    /** The read failed for {@code failure}, with no cause worth keeping. */
    static PatientSourceException failed(Failure failure) {
        return failed(failure, null);
    }

    /** The read failed for {@code failure}, on {@code redactedCause}. */
    static PatientSourceException failed(Failure failure, Throwable redactedCause) {
        return new PatientSourceException(failure, failure.message, redactedCause);
    }

    /** The source answered {@code status}, one the read does not take as its answer. */
    static PatientSourceException answered(int status) {
        Failure failure = status >= 500 ? Failure.SERVER_ERROR : Failure.UNEXPECTED_STATUS;
        return new PatientSourceException(failure, failure.message + " " + status, null);
    }
}
