package com.example.backend.patient.infrastructure.http;

import com.example.backend.observability.LogEvent.ErrorCategory;
import com.example.backend.observability.OutboundCallFailure;
import com.example.backend.observability.OutboundCallFailure.Reason;
import com.example.backend.observability.UpstreamFaultException;
import java.util.stream.Stream;

/**
 * The in-house patient source could not be read: it gave no answer, an answer other than the
 * read's own — a Patient or a {@code 404}, a search's searchset, or an FFI History's or a NUMC
 * History's searchset or a {@code 404} — or one the service cannot hold. A fault of the source, answered
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
 *       or a history that is no searchset, has no total, or holds an entry with no id, or an
 *       FFI or a NUMC Record missing a field, holding a code outside its closed set or a date
 *       not to the day — a NUMC Record's each under a reason naming NUMC, so a record says
 *       which history broke.
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
        /** An FFI History's entry with no {@code id}. */
        FFI_NO_ID("no-id", "The patient source's FFI has no id", ErrorCategory.DATA),
        /** An FFI History's entry whose {@code id} is outside FHIR's id grammar. */
        FFI_INVALID_ID("invalid-id", "The patient source's FFI's id is not a FHIR id",
                ErrorCategory.DATA),
        /** An FFI with no {@code type}. */
        NO_FFI_TYPE("no-type", "The patient source's FFI has no type", ErrorCategory.DATA),
        /** An FFI whose {@code type} is no FFI Type's code. */
        UNKNOWN_FFI_TYPE("unknown-type", "The patient source's FFI's type is not an FFI Type",
                ErrorCategory.DATA),
        /** An FFI with no {@code outcome}. */
        NO_OUTCOME("no-outcome", "The patient source's FFI has no outcome", ErrorCategory.DATA),
        /** An FFI whose {@code outcome} is no Outcome's code. */
        UNKNOWN_OUTCOME("unknown-outcome",
                "The patient source's FFI's outcome is not an Outcome", ErrorCategory.DATA),
        /** An FFI with no {@code issueDate}. */
        NO_ISSUE_DATE("no-issue-date", "The patient source's FFI has no issue date",
                ErrorCategory.DATA),
        /** An FFI whose Issue Date is no date to the day. */
        ISSUE_DATE_NOT_TO_THE_DAY("issue-date-not-to-the-day",
                "The patient source's FFI's issue date is not to the day", ErrorCategory.DATA),
        /** An FFI with no {@code expiryDate}. */
        NO_EXPIRY_DATE("no-expiry-date", "The patient source's FFI has no expiry date",
                ErrorCategory.DATA),
        /** An FFI whose Expiry Date is no date to the day. */
        EXPIRY_DATE_NOT_TO_THE_DAY("expiry-date-not-to-the-day",
                "The patient source's FFI's expiry date is not to the day", ErrorCategory.DATA),
        /** An FFI with no {@code higherAuthority}. */
        NO_HIGHER_AUTHORITY("no-higher-authority",
                "The patient source's FFI has no higher authority", ErrorCategory.DATA),
        /** An FFI whose {@code higherAuthority} is no Higher Authority's code. */
        UNKNOWN_HIGHER_AUTHORITY("unknown-higher-authority",
                "The patient source's FFI's higher authority is not a Higher Authority",
                ErrorCategory.DATA),
        /** An FFI with no {@code submittingMc}. */
        NO_SUBMITTING_MC("no-submitting-mc", "The patient source's FFI has no submitting MC",
                ErrorCategory.DATA),
        /** An FFI whose {@code submittingMc} is no Submitting MC's code. */
        UNKNOWN_SUBMITTING_MC("unknown-submitting-mc",
                "The patient source's FFI's submitting MC is not a Submitting MC",
                ErrorCategory.DATA),
        /** An FFI with no {@code status}. */
        NO_STATUS("no-status", "The patient source's FFI has no status", ErrorCategory.DATA),
        /** An FFI whose {@code status} is no Status's code. */
        UNKNOWN_STATUS("unknown-status", "The patient source's FFI's status is not a Status",
                ErrorCategory.DATA),
        /** A NUMC History's entry with no {@code id}. */
        NUMC_NO_ID("numc-no-id", "The patient source's NUMC Record has no id",
                ErrorCategory.DATA),
        /** A NUMC History's entry whose {@code id} is outside FHIR's id grammar. */
        NUMC_INVALID_ID("numc-invalid-id",
                "The patient source's NUMC Record's id is not a FHIR id", ErrorCategory.DATA),
        /** A NUMC Record with no {@code type}. */
        NO_NUMC_TYPE("no-numc-type", "The patient source's NUMC Record has no type",
                ErrorCategory.DATA),
        /** A NUMC Record whose {@code type} is no NUMC Type's code. */
        UNKNOWN_NUMC_TYPE("unknown-numc-type",
                "The patient source's NUMC Record's type is not a NUMC Type", ErrorCategory.DATA),
        /** A NUMC Record with no {@code outcome}. */
        NO_NUMC_OUTCOME("no-numc-outcome", "The patient source's NUMC Record has no outcome",
                ErrorCategory.DATA),
        /** A NUMC Record whose {@code outcome} is no NUMC Outcome's code. */
        UNKNOWN_NUMC_OUTCOME("unknown-numc-outcome",
                "The patient source's NUMC Record's outcome is not a NUMC Outcome",
                ErrorCategory.DATA),
        /** A NUMC Record with no {@code issueDate}. */
        NO_NUMC_ISSUE_DATE("no-numc-issue-date",
                "The patient source's NUMC Record has no issue date", ErrorCategory.DATA),
        /** A NUMC Record whose Issue Date is no date to the day. */
        NUMC_ISSUE_DATE_NOT_TO_THE_DAY("numc-issue-date-not-to-the-day",
                "The patient source's NUMC Record's issue date is not to the day",
                ErrorCategory.DATA),
        /** A NUMC Record with no {@code expiryDate}. */
        NO_NUMC_EXPIRY_DATE("no-numc-expiry-date",
                "The patient source's NUMC Record has no expiry date", ErrorCategory.DATA),
        /** A NUMC Record whose Expiry Date is no date to the day. */
        NUMC_EXPIRY_DATE_NOT_TO_THE_DAY("numc-expiry-date-not-to-the-day",
                "The patient source's NUMC Record's expiry date is not to the day",
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
