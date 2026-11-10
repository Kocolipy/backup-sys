package com.example.backend.patient.controller;

import com.example.backend.paging.domain.PageParameter;
import com.example.backend.paging.domain.PageRequest;
import com.example.backend.patient.application.PatientSearchService;
import com.example.backend.patient.domain.PatientQuery;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound HTTP adapter for a Patient search (ADR 0018): {@code GET /api/patient-search}.
 *
 * <p>Requires {@code patient:read}, declared here with method security and repeated by the chain
 * as a backstop (ADR 0015). Outside {@code /api/patient}, so it answers plain JSON and its
 * refusals the application API's {@code ApiError}, never a FHIR {@code OperationOutcome}.
 *
 * <p>{@code q} binds through {@link PatientQuery#valueOf}: a full NRIC is an NRIC search, anything
 * else a name search, and a name under three characters — or no {@code q} at all — is the
 * binder's {@code 400}, before the patient source is called. {@code startIndex} and {@code count}
 * are read by ADR 0017's rules ({@link PageRequest#of}): coerced, never refused, and a value that
 * is no integer is the binder's {@code 400}. The page they resolve to is handed to the source,
 * which matches, sorts, pages and counts; the answer names the page's {@code startIndex} as it was
 * asked for. A failure of the source is the app-wide handler's {@code 502}.
 *
 * <p>Nothing here logs: a query is Restricted data, and the request's own record names the route
 * template, never its query string.
 */
@RestController
@RequestMapping("/api/patient-search")
public class PatientSearchController {

    private final PatientSearchService searches;

    public PatientSearchController(PatientSearchService searches) {
        this.searches = searches;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('patient:read')")
    public PatientSearchPage search(
            @RequestParam PatientQuery q,
            @RequestParam(required = false) PageParameter startIndex,
            @RequestParam(required = false) PageParameter count) {
        PageRequest page = PageRequest.of(startIndex, count);
        return PatientSearchPage.of(searches.search(q, page), page.startIndex());
    }
}
