package com.example.backend.patient.controller;

import static com.example.backend.patient.controller.QueryParameters.range;
import static com.example.backend.patient.controller.QueryParameters.sort;
import static com.example.backend.patient.controller.QueryParameters.sortOrder;
import static com.example.backend.patient.controller.QueryParameters.spelled;

import com.example.backend.paging.domain.PageParameter;
import com.example.backend.paging.domain.PageRequest;
import com.example.backend.patient.application.ArmcHistoryService;
import com.example.backend.patient.controller.PatientController.NoSuchPatientException;
import com.example.backend.patient.domain.ArmcFilter;
import com.example.backend.patient.domain.ArmcHistory;
import com.example.backend.patient.domain.ArmcOutcome;
import com.example.backend.patient.domain.HistorySort;
import com.example.backend.patient.domain.PatientId;
import com.example.backend.patient.domain.SortOrder;
import com.example.backend.patient.domain.SourceCode;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound HTTP adapter for a patient's ARMC History (ADR 0019, D6):
 * {@code GET /api/patient-armc/{patientId}}.
 *
 * <p>Requires {@code patient:read}, declared here with method security and repeated by the chain
 * as a backstop (ADR 0015). Outside {@code /api/patient}, as a Patient search is, so it answers
 * plain JSON and its refusals the application API's {@code ApiError}, never a FHIR
 * {@code OperationOutcome}.
 *
 * <p>Every parameter is checked as the request is read in, before the patient source is called,
 * and a refusal quotes nothing the caller sent ({@link QueryParameters}):
 *
 * <ul>
 *   <li>{@code patientId} binds through {@link PatientId}, so one outside FHIR's id grammar is the
 *       binder's {@code 400}.
 *   <li>The filters bind to typed values: {@code outcome}, one code, to a member of the ARMC
 *       Outcome, matched exactly by code ({@link SourceCode#ofParameter}); and
 *       {@code startDateFrom}, {@code startDateTo}, {@code endDateFrom} and {@code endDateTo} to
 *       the ranges over the history's two date columns ({@link ArmcHistory#DATE_COLUMNS}),
 *       calendar dates, {@code YYYY-MM-DD}. Any other value is a {@code 400}, as a from-date
 *       after its to-date is, and as an Outcome given twice is.
 *   <li>{@code sortBy} names one of those columns, {@code startDate} or {@code endDate}, and
 *       {@code sortOrder} binds to {@code ascending} or {@code descending} ({@link HistorySort});
 *       any other value is a {@code 400}. A {@code sortOrder} with no {@code sortBy} names a
 *       direction for no order, and the read takes the source's own.
 *   <li>{@code startIndex} and {@code count} are read by ADR 0017's rules
 *       ({@link PageRequest#of}): coerced, never refused, and a value that is no integer is the
 *       binder's {@code 400}. The page they come to is the source's to read — {@code count=0} and
 *       a page past the end too (ADR 0018, D3) — and the answer names its {@code startIndex} as it
 *       was asked for.
 * </ul>
 *
 * <p>The filters, the sort and the page are handed to the source, which filters, sorts, pages and
 * counts, and breaks the sort's ties. A patient the source holds none under is the {@code 404},
 * which the SPA reads as "No patient in context"; a failure of the source is the app-wide
 * handler's {@code 502}. Nothing here logs or audits: an ARMC History is Restricted data, and its
 * read is no more audited than a read of one Patient by id is.
 */
@RestController
@RequestMapping("/api/patient-armc")
public class PatientArmcController {

    private final ArmcHistoryService boards;

    public PatientArmcController(ArmcHistoryService boards) {
        this.boards = boards;
    }

    /**
     * The Outcome and the sort's direction read in their own spellings, which are no Java
     * constant's name ({@link QueryParameters#sortOrder} for the direction): a value that spells
     * none of them fails the binding. The column and the dates are read by
     * {@link QueryParameters#sort} and {@link QueryParameters#range}.
     */
    @InitBinder
    void spellings(WebDataBinder binder) {
        binder.registerCustomEditor(ArmcOutcome.class,
                spelled(code -> SourceCode.ofParameter(ArmcOutcome.class, code)));
        sortOrder(binder);
    }

    @GetMapping("/{patientId}")
    @PreAuthorize("hasAuthority('patient:read')")
    public ArmcHistoryPage read(@PathVariable PatientId patientId,
            @RequestParam(required = false) ArmcOutcome outcome,
            @RequestParam(required = false) DateParameter startDateFrom,
            @RequestParam(required = false) DateParameter startDateTo,
            @RequestParam(required = false) DateParameter endDateFrom,
            @RequestParam(required = false) DateParameter endDateTo,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) SortOrder sortOrder,
            @RequestParam(required = false) PageParameter startIndex,
            @RequestParam(required = false) PageParameter count) {
        ArmcFilter filter = new ArmcFilter(outcome,
                range(ArmcHistory.START_DATE, startDateFrom, startDateTo),
                range(ArmcHistory.END_DATE, endDateFrom, endDateTo));
        HistorySort sort = sort(ArmcHistory.DATE_COLUMNS, sortBy, sortOrder);
        PageRequest page = PageRequest.of(startIndex, count);
        return boards.find(patientId, filter, sort, page)
                .map(history -> ArmcHistoryPage.of(history, page.startIndex()))
                .orElseThrow(NoSuchPatientException::new);
    }
}
