package com.example.backend.patient.application;

import com.example.backend.paging.domain.PageRequest;
import com.example.backend.patient.domain.HistorySort;
import com.example.backend.patient.domain.MedicationFilter;
import com.example.backend.patient.domain.MedicationHistory;
import com.example.backend.patient.domain.PatientId;
import com.example.backend.patient.domain.PatientStore;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Reads a page of a patient's Medication History (/docs/domain/medication.md; ADR 0019, D7): the
 * patient source's own, passed through with its filters and its sort — the service filters,
 * orders and pages nothing itself, and holds and caches nothing. Read-only, and not audited, as a
 * read of one Patient by id is not. No transaction: the read is a call to the source, not to the database.
 */
@Service
public class MedicationHistoryService {

    private final PatientStore patients;

    public MedicationHistoryService(PatientStore patients) {
        this.patients = patients;
    }

    /**
     * The page {@code page} of the Medication History of the patient {@code id} that
     * {@code filter} narrows, in the order {@code sort} names, or none without the patient.
     */
    public Optional<MedicationHistory> find(PatientId id, MedicationFilter filter,
            HistorySort sort, PageRequest page) {
        return patients.findMedicationHistory(id, filter, sort, page);
    }
}
