package com.example.backend.patient.application;

import com.example.backend.paging.domain.PageRequest;
import com.example.backend.patient.domain.ArmcFilter;
import com.example.backend.patient.domain.ArmcHistory;
import com.example.backend.patient.domain.HistorySort;
import com.example.backend.patient.domain.PatientId;
import com.example.backend.patient.domain.PatientStore;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Reads a page of a patient's ARMC History (/docs/domain/armc.md; ADR 0019, D6): the patient
 * source's own, passed through with its filters and its sort — the service filters, orders and
 * pages nothing itself, and holds and caches nothing. Read-only, and not audited, as a read of one Patient by
 * id is not. No transaction: the read is a call to the source, not to the database.
 */
@Service
public class ArmcHistoryService {

    private final PatientStore patients;

    public ArmcHistoryService(PatientStore patients) {
        this.patients = patients;
    }

    /**
     * The page {@code page} of the ARMC History of the patient {@code id} that {@code filter}
     * narrows, in the order {@code sort} names, or none without the patient.
     */
    public Optional<ArmcHistory> find(PatientId id, ArmcFilter filter, HistorySort sort,
            PageRequest page) {
        return patients.findArmcHistory(id, filter, sort, page);
    }
}
