package com.example.backend.patient.application;

import com.example.backend.paging.domain.PageRequest;
import com.example.backend.patient.domain.FfiHistory;
import com.example.backend.patient.domain.PatientId;
import com.example.backend.patient.domain.PatientStore;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Reads a page of a patient's FFI History (/docs/domain/ffi.md; ADR 0019): the patient source's
 * own, passed through — the service pages and orders nothing itself, and holds and caches nothing.
 * Read-only, and not audited, as a read of one Patient by id is not. No transaction: the read is a
 * call to the source, not to the database.
 */
@Service
public class FfiHistoryService {

    private final PatientStore patients;

    public FfiHistoryService(PatientStore patients) {
        this.patients = patients;
    }

    /** The page {@code page} of the FFI History of the patient {@code id}, or none without one. */
    public Optional<FfiHistory> find(PatientId id, PageRequest page) {
        return patients.findFfiHistory(id, page);
    }
}
