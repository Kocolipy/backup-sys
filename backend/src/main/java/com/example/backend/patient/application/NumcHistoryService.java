package com.example.backend.patient.application;

import com.example.backend.paging.domain.PageRequest;
import com.example.backend.patient.domain.NumcHistory;
import com.example.backend.patient.domain.PatientId;
import com.example.backend.patient.domain.PatientStore;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Reads a page of a patient's NUMC History (/docs/domain/numc.md; ADR 0019, D5): the patient
 * source's own, passed through in the source's order — the service orders and pages nothing
 * itself, and holds and caches nothing. Read-only, and not audited, as a read of one Patient by
 * id is not. No transaction: the read is a call to the source, not to the database.
 */
@Service
public class NumcHistoryService {

    private final PatientStore patients;

    public NumcHistoryService(PatientStore patients) {
        this.patients = patients;
    }

    /** The page {@code page} of the NUMC History of the patient {@code id}, or none without the patient. */
    public Optional<NumcHistory> find(PatientId id, PageRequest page) {
        return patients.findNumcHistory(id, page);
    }
}
