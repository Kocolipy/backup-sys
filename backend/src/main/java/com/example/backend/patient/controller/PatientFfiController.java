package com.example.backend.patient.controller;

import static com.example.backend.patient.controller.QueryParameters.DATE;
import static com.example.backend.patient.controller.QueryParameters.codes;
import static com.example.backend.patient.controller.QueryParameters.range;
import static com.example.backend.patient.controller.QueryParameters.spelled;

import com.example.backend.paging.domain.PageParameter;
import com.example.backend.paging.domain.PageRequest;
import com.example.backend.patient.application.FfiHistoryService;
import com.example.backend.patient.controller.PatientController.NoSuchPatientException;
import com.example.backend.patient.domain.FfiFilter;
import com.example.backend.patient.domain.FfiHigherAuthority;
import com.example.backend.patient.domain.FfiOutcome;
import com.example.backend.patient.domain.FfiSort;
import com.example.backend.patient.domain.FfiStatus;
import com.example.backend.patient.domain.FfiSubmittingMc;
import com.example.backend.patient.domain.FfiType;
import com.example.backend.patient.domain.PatientId;
import com.example.backend.patient.domain.SortOrder;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound HTTP adapter for a patient's FFI History (ADR 0019):
 * {@code GET /api/patient-ffi/{patientId}}.
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
 *   <li>The filters bind to typed values: {@code type}, {@code outcome}, {@code higherAuthority}
 *       and {@code submittingMc}, each repeated for any of its codes, and {@code status}, to the
 *       members of their closed sets, matched exactly by code ({@link FfiFilter#codeOf}); and
 *       {@code issueDateFrom}, {@code issueDateTo}, {@code expiryDateFrom} and
 *       {@code expiryDateTo} to calendar dates, {@code YYYY-MM-DD}. Any other value is the
 *       binder's {@code 400}, as a from-date after its to-date is.
 *   <li>{@code sortBy} binds to {@code issueDate} or {@code expiryDate} and {@code sortOrder} to
 *       {@code ascending} or {@code descending} ({@link FfiSort}); any other value is the binder's
 *       {@code 400}. A {@code sortOrder} with no {@code sortBy} names a direction for no order, and
 *       the read takes the source's own.
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
 * logs or audits: an FFI History is Restricted data, and its read is no more audited than a read
 * of one Patient by id is.
 */
@RestController
@RequestMapping("/api/patient-ffi")
public class PatientFfiController {

    private final FfiHistoryService ffis;

    public PatientFfiController(FfiHistoryService ffis) {
        this.ffis = ffis;
    }

    /**
     * The Status and the sort read in their own spellings, which are no Java constant's name: a
     * value that spells none of them fails the binding, as a malformed date does. The repeated
     * codes are read by {@link QueryParameters#codes}.
     */
    @InitBinder
    void spellings(WebDataBinder binder) {
        binder.registerCustomEditor(FfiStatus.class,
                spelled(code -> FfiFilter.codeOf(FfiStatus.class, code)));
        binder.registerCustomEditor(FfiSort.Field.class, spelled(FfiSort.Field::of));
        binder.registerCustomEditor(SortOrder.class, spelled(SortOrder::of));
    }

    @GetMapping("/{patientId}")
    @PreAuthorize("hasAuthority('patient:read')")
    public FfiHistoryPage read(@PathVariable PatientId patientId,
            @RequestParam(required = false) List<String> type,
            @RequestParam(required = false) List<String> outcome,
            @RequestParam(required = false) @DateTimeFormat(pattern = DATE) LocalDate issueDateFrom,
            @RequestParam(required = false) @DateTimeFormat(pattern = DATE) LocalDate issueDateTo,
            @RequestParam(required = false) @DateTimeFormat(pattern = DATE)
            LocalDate expiryDateFrom,
            @RequestParam(required = false) @DateTimeFormat(pattern = DATE) LocalDate expiryDateTo,
            @RequestParam(required = false) List<String> higherAuthority,
            @RequestParam(required = false) List<String> submittingMc,
            @RequestParam(required = false) FfiStatus status,
            @RequestParam(required = false) FfiSort.Field sortBy,
            @RequestParam(required = false) SortOrder sortOrder,
            @RequestParam(required = false) PageParameter startIndex,
            @RequestParam(required = false) PageParameter count) {
        FfiFilter filter = new FfiFilter(codes(FfiType.class, type),
                codes(FfiOutcome.class, outcome), range(issueDateFrom, issueDateTo),
                range(expiryDateFrom, expiryDateTo),
                codes(FfiHigherAuthority.class, higherAuthority),
                codes(FfiSubmittingMc.class, submittingMc), status);
        PageRequest page = PageRequest.of(startIndex, count);
        return ffis.find(patientId, filter, FfiSort.of(sortBy, sortOrder), page)
                .map(history -> FfiHistoryPage.of(history, page.startIndex()))
                .orElseThrow(NoSuchPatientException::new);
    }
}
