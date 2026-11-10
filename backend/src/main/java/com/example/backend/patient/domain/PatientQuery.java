package com.example.backend.patient.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A Patient search: what the one search box asked for (/docs/domain/patient.md, "Patient
 * search"). A full NRIC, in either case, is an exact NRIC search; anything else is a name search,
 * which needs at least three characters. There is no partial NRIC search, and no query that
 * lists every Patient.
 *
 * <p>{@code GET /api/patient-search} binds its {@code q} through {@link #valueOf}, so a query
 * refused here is the {@code 400} of a parameter that cannot be bound, before the patient source
 * is called. A query is Restricted data — it holds a name or an NRIC — so a refusal quotes none of
 * it and {@link #toString()} prints which of the two searches it is, alone.
 */
public sealed interface PatientQuery {

    /**
     * The search {@code text} asks for, trimmed: an NRIC search for a full NRIC in either case,
     * upper-cased, and a name search for anything else.
     *
     * @throws IllegalArgumentException for anything else under three characters
     */
    static PatientQuery valueOf(String text) {
        String query = Objects.requireNonNull(text, "text").strip();
        return ByNric.FULL_NRIC.matcher(query.toUpperCase(Locale.ROOT)).matches()
                ? new ByNric(query.toUpperCase(Locale.ROOT))
                : new ByName(query);
    }

    /**
     * A name search: the Patients whose name contains {@code text}, as the patient source matches
     * it — FHIR's {@code name:contains}, which may match a usual or maiden name as well as the
     * official one.
     *
     * @param text the name, trimmed, of at least three characters
     */
    record ByName(String text) implements PatientQuery {

        /** The fewest characters a name search takes. */
        public static final int SHORTEST = 3;

        public ByName {
            Objects.requireNonNull(text, "text");
            if (!text.equals(text.strip()) || text.codePointCount(0, text.length()) < SHORTEST) {
                throw new IllegalArgumentException(
                        "A name search needs at least " + SHORTEST + " characters");
            }
        }

        @Override
        public String toString() {
            return "PatientQuery[NAME]";
        }
    }

    /**
     * An NRIC search: the Patient whose NRIC is exactly {@code nric}.
     *
     * @param nric a full NRIC — a prefix letter, seven digits and a check letter — in upper case;
     *             its check letter is not checked
     */
    record ByNric(String nric) implements PatientQuery {

        /** A full NRIC's shape. */
        static final Pattern FULL_NRIC = Pattern.compile("[STFGM]\\d{7}[A-Z]");

        public ByNric {
            Objects.requireNonNull(nric, "nric");
            if (!FULL_NRIC.matcher(nric).matches()) {
                throw new IllegalArgumentException("An NRIC search needs a full NRIC");
            }
        }

        @Override
        public String toString() {
            return "PatientQuery[NRIC]";
        }
    }
}
