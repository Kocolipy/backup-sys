package com.example.backend.patient.controller;

import static com.example.backend.patient.controller.QueryParameters.codes;
import static com.example.backend.patient.controller.QueryParameters.range;
import static com.example.backend.patient.controller.QueryParameters.sort;
import static com.example.backend.patient.controller.QueryParameters.sortOrder;

import com.example.backend.paging.domain.PageParameter;
import com.example.backend.paging.domain.PageRequest;
import com.example.backend.patient.application.NumcHistoryService;
import com.example.backend.patient.controller.PatientController.NoSuchPatientException;
import com.example.backend.patient.domain.HistorySort;
import com.example.backend.patient.domain.NumcFilter;
import com.example.backend.patient.domain.NumcHistory;
import com.example.backend.patient.domain.NumcOutcome;
import com.example.backend.patient.domain.NumcType;
import com.example.backend.patient.domain.PatientId;
import com.example.backend.patient.domain.SortOrder;
import com.example.backend.patient.domain.SourceCode;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound HTTP adapter for a patient's NUMC History (ADR 0019, D5):
 * {@code GET /api/patient-numc/{patientId}}.
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
 *   <li>The filters bind to typed values: {@code type} and {@code outcome}, each repeated for any
 *       of its codes, to the members of their closed sets, the NUMC Type and the NUMC Outcome,
 *       matched exactly by code ({@link SourceCode#ofParameter}); and {@code issueDateFrom},
 *       {@code issueDateTo}, {@code expiryDateFrom} and {@code expiryDateTo} to the ranges over
 *       the history's two date columns ({@link NumcHistory#DATE_COLUMNS}), calendar dates,
 *       {@code YYYY-MM-DD}. Any other value is a {@code 400}, as a from-date after its to-date
 *       is.
 *   <li>{@code sortBy} names one of those columns, {@code issueDate} or {@code expiryDate}, and
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
 * counts. A patient the source holds none under is the {@code 404}, which the SPA reads as "No
 * patient in context"; a failure of the source is the app-wide handler's {@code 502}. Nothing here
 * logs or audits: a NUMC History is Restricted data, and its read is no more audited than a read
 * of one Patient by id is.
 */
@RestController
@RequestMapping("/api/patient-numc")
public class PatientNumcController {

    private final NumcHistoryService records;

    public PatientNumcController(NumcHistoryService records) {
        this.records = records;
    }

    /**
     * The sort's direction read in its own spellings ({@link QueryParameters#sortOrder}): a value
     * that spells neither fails the binding. The column, the dates and the repeated codes are read
     * by {@link QueryParameters#sort}, {@link QueryParameters#range} and
     * {@link QueryParameters#codes}.
     */
    @InitBinder
    void spellings(WebDataBinder binder) {
        sortOrder(binder);
    }

    @GetMapping("/{patientId}")
    @PreAuthorize("hasAuthority('patient:read')")
    public NumcHistoryPage read(@PathVariable PatientId patientId,
            @RequestParam(required = false) List<String> type,
            @RequestParam(required = false) List<String> outcome,
            @RequestParam(required = false) DateParameter issueDateFrom,
            @RequestParam(required = false) DateParameter issueDateTo,
            @RequestParam(required = false) DateParameter expiryDateFrom,
            @RequestParam(required = false) DateParameter expiryDateTo,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) SortOrder sortOrder,
            @RequestParam(required = false) PageParameter startIndex,
            @RequestParam(required = false) PageParameter count) {
        NumcFilter filter = new NumcFilter(codes(NumcType.class, type),
                codes(NumcOutcome.class, outcome),
                range(NumcHistory.ISSUE_DATE, issueDateFrom, issueDateTo),
                range(NumcHistory.EXPIRY_DATE, expiryDateFrom, expiryDateTo));
        HistorySort sort = sort(NumcHistory.DATE_COLUMNS, sortBy, sortOrder);
        PageRequest page = PageRequest.of(startIndex, count);
        return records.find(patientId, filter, sort, page)
                .map(history -> NumcHistoryPage.of(history, page.startIndex()))
                .orElseThrow(NoSuchPatientException::new);
    }
}
