package com.example.backend.patient.domain;

import com.example.backend.paging.domain.PageRequest;
import java.util.Optional;

/**
 * Outbound port for reading Patients: one by its id, a page of a Patient search, or a page of one
 * patient's FFI History. Owned by the domain and implemented by an infrastructure adapter, so the
 * dependency points inward: the HTTP adapter over the in-house patient source (ADR 0014), which
 * the controller, the wire and the SPA do not know of.
 *
 * <p>Read-only: nothing in the service writes a Patient.
 */
public interface PatientStore {

    /** The Patient held under {@code id}, or none when the source holds no patient under it. */
    Optional<Patient> findById(PatientId id);

    /**
     * The page {@code page} of the Patients {@code query} matches and {@code filter} narrows, as
     * the source matches, filters and pages them, in the source's own stable order (ADR 0018).
     */
    PatientMatches search(PatientQuery query, PatientFilter filter, PageRequest page);

    /**
     * The page {@code page} of the FFI History of the patient held under {@code id} that
     * {@code filter} narrows, in the order {@code sort} names — the source's own order for
     * {@link FfiSort#SOURCE_ORDER} — as the source filters, orders and pages it, ties broken by
     * id (ADR 0019), or none when the source holds no patient under it.
     */
    Optional<FfiHistory> findFfiHistory(PatientId id, FfiFilter filter, FfiSort sort,
            PageRequest page);
}
