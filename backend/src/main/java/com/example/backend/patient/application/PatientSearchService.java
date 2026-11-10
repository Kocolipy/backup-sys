package com.example.backend.patient.application;

import com.example.backend.audit.domain.AuditActorContext;
import com.example.backend.audit.domain.AuditPatientQueryKind;
import com.example.backend.audit.domain.AuditTrail;
import com.example.backend.audit.domain.Audited;
import com.example.backend.paging.domain.PageRequest;
import com.example.backend.patient.domain.PatientMatches;
import com.example.backend.patient.domain.PatientQuery;
import com.example.backend.patient.domain.PatientStore;
import org.springframework.stereotype.Service;

/**
 * A Patient search (/docs/domain/patient.md, "Patient search"; ADR 0018): the patient source's own
 * search, passed through — the service matches, sorts and pages nothing itself, and holds and
 * caches nothing — and one {@code PATIENT_SEARCH} audit row for every search the source answered.
 *
 * <p>The row names the signed-in User who searched, how many Patients the answered page held and
 * whether the query was an NRIC or a name, and nothing of the query itself. It is appended after
 * the source answered, fail-closed (ADR 0004): a search that cannot be recorded is not answered,
 * and one the source failed read nothing, so it records nothing. No transaction of the caller's:
 * the read is a call to the source, and the append opens its own.
 */
@Service
public class PatientSearchService {

    private final PatientStore patients;

    private final AuditTrail audit;

    private final AuditActorContext actors;

    public PatientSearchService(PatientStore patients, AuditTrail audit, AuditActorContext actors) {
        this.patients = patients;
        this.audit = audit;
        this.actors = actors;
    }

    /** The page {@code page} of the Patients {@code query} matches, as the source answered it. */
    public PatientMatches search(PatientQuery query, PageRequest page) {
        PatientMatches matches = patients.search(query, page);
        audit.append(new Audited.PatientsSearched(
                actors.signedInUser().orElse(null), kindOf(query), matches.patients().size()));
        return matches;
    }

    private static AuditPatientQueryKind kindOf(PatientQuery query) {
        return switch (query) {
            case PatientQuery.ByNric _ -> AuditPatientQueryKind.NRIC;
            case PatientQuery.ByName _ -> AuditPatientQueryKind.NAME;
        };
    }
}
