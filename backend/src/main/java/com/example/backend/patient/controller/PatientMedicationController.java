package com.example.backend.patient.controller;

import com.example.backend.paging.domain.PageParameter;
import com.example.backend.paging.domain.PageRequest;
import com.example.backend.patient.application.MedicationHistoryService;
import com.example.backend.patient.controller.PatientController.NoSuchPatientException;
import com.example.backend.patient.domain.PatientId;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound HTTP adapter for a patient's Medication History (ADR 0019, D7):
 * {@code GET /api/patient-medication/{patientId}}.
 *
 * <p>Requires {@code patient:read}, declared here with method security and repeated by the chain
 * as a backstop (ADR 0015). Outside {@code /api/patient}, as a Patient search is, so it answers
 * plain JSON and its refusals the application API's {@code ApiError}, never a FHIR
 * {@code OperationOutcome}.
 *
 * <p>Every parameter is checked as the request is read in, before the patient source is called:
 * {@code patientId} binds through {@link PatientId}, so one outside FHIR's id grammar is the
 * binder's {@code 400}; and {@code startIndex} and {@code count} are read by ADR 0017's rules
 * ({@link PageRequest#of}): coerced, never refused, and a value that is no integer is the
 * binder's {@code 400}. The page they come to is the source's to read — {@code count=0} and a
 * page past the end too (ADR 0018, D3) — and the answer names its {@code startIndex} as it was
 * asked for. The read takes no sort and no filter: the order is the source's own.
 *
 * <p>A patient the source holds none under is the {@code 404}, which the SPA reads as "No patient
 * in context"; a failure of the source is the app-wide handler's {@code 502}. Nothing here logs
 * or audits: a Medication History is Restricted data, and its read is no more audited than a
 * read of one Patient by id is.
 */
@RestController
@RequestMapping("/api/patient-medication")
public class PatientMedicationController {

    private final MedicationHistoryService medications;

    public PatientMedicationController(MedicationHistoryService medications) {
        this.medications = medications;
    }

    @GetMapping("/{patientId}")
    @PreAuthorize("hasAuthority('patient:read')")
    public MedicationHistoryPage read(@PathVariable PatientId patientId,
            @RequestParam(required = false) PageParameter startIndex,
            @RequestParam(required = false) PageParameter count) {
        PageRequest page = PageRequest.of(startIndex, count);
        return medications.find(patientId, page)
                .map(history -> MedicationHistoryPage.of(history, page.startIndex()))
                .orElseThrow(NoSuchPatientException::new);
    }
}
