package com.example.backend.patient.domain;

import com.example.backend.paging.domain.PageRequest;
import java.util.Optional;

/**
 * Outbound port for reading Patients: one by its id, or a page of a Patient search. Owned by the
 * domain and implemented by an infrastructure adapter, so the dependency points inward: the HTTP
 * adapter over the in-house patient source (ADR 0014), which the controller, the wire and the SPA
 * do not know of.
 *
 * <p>Read-only: nothing in the service writes a Patient.
 */
public interface PatientStore {

    /** The Patient held under {@code id}, or none when the source holds no patient under it. */
    Optional<Patient> findById(PatientId id);

    /**
     * The page {@code page} of the Patients {@code query} matches and {@code filter} narrows, in
     * the order {@code sort} names, as the source matches, filters, orders and pages them: ties
     * broken by id, a Patient with no Last visit last in both directions (ADR 0018).
     */
    PatientMatches search(PatientQuery query, PatientFilter filter, PatientSort sort,
            PageRequest page);
}
