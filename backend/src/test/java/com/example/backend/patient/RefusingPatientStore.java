package com.example.backend.patient;

import com.example.backend.paging.domain.PageRequest;
import com.example.backend.patient.domain.ArmcHistory;
import com.example.backend.patient.domain.FfiFilter;
import com.example.backend.patient.domain.FfiHistory;
import com.example.backend.patient.domain.HistorySort;
import com.example.backend.patient.domain.MedicationHistory;
import com.example.backend.patient.domain.NumcFilter;
import com.example.backend.patient.domain.NumcHistory;
import com.example.backend.patient.domain.Patient;
import com.example.backend.patient.domain.PatientFilter;
import com.example.backend.patient.domain.PatientId;
import com.example.backend.patient.domain.PatientMatches;
import com.example.backend.patient.domain.PatientQuery;
import com.example.backend.patient.domain.PatientStore;
import java.util.Optional;

/**
 * A hand-written {@link PatientStore} that refuses every read: a test's fake extends it and
 * overrides only the reads its subject is asked to make, so a read the subject was not meant to
 * make fails the test, and a read the port gains reaches no fake but this one.
 */
public abstract class RefusingPatientStore implements PatientStore {

    @Override
    public Optional<Patient> findById(PatientId id) {
        throw refused("a Patient by id");
    }

    @Override
    public PatientMatches search(PatientQuery query, PatientFilter filter, PageRequest page) {
        throw refused("a Patient search");
    }

    @Override
    public Optional<FfiHistory> findFfiHistory(PatientId id, FfiFilter filter, HistorySort sort,
            PageRequest page) {
        throw refused("an FFI History");
    }

    @Override
    public Optional<NumcHistory> findNumcHistory(PatientId id, NumcFilter filter, HistorySort sort,
            PageRequest page) {
        throw refused("a NUMC History");
    }

    @Override
    public Optional<ArmcHistory> findArmcHistory(PatientId id, PageRequest page) {
        throw refused("an ARMC History");
    }

    @Override
    public Optional<MedicationHistory> findMedicationHistory(PatientId id, PageRequest page) {
        throw refused("a Medication History");
    }

    private static UnsupportedOperationException refused(String read) {
        return new UnsupportedOperationException("this store is not asked for " + read);
    }
}
