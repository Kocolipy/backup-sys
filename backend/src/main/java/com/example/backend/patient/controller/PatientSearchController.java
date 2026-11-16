package com.example.backend.patient.controller;

import com.example.backend.paging.domain.PageParameter;
import com.example.backend.paging.domain.PageRequest;
import com.example.backend.patient.application.PatientSearchService;
import com.example.backend.patient.domain.AdministrativeGender;
import com.example.backend.patient.domain.PatientFilter;
import com.example.backend.patient.domain.PatientQuery;
import java.beans.PropertyEditorSupport;
import java.util.function.Function;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
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
 * <p>Every parameter is checked as the request is read in, before the patient source is called,
 * and a refusal quotes nothing the caller sent:
 *
 * <ul>
 *   <li>{@code q} binds through {@link PatientQuery#valueOf}: a full NRIC is an NRIC search,
 *       anything else a name search, and a name under three characters — or no {@code q} at all
 *       — is the binder's {@code 400}.
 *   <li>{@code gender} binds to male or female ({@link PatientFilter#genderOf}); any other value
 *       is the binder's {@code 400}.
 *   <li>{@code startIndex} and {@code count} are read by ADR 0017's rules
 *       ({@link PageRequest#of}): coerced, never refused, and a value that is no integer is the
 *       binder's {@code 400}.
 * </ul>
 *
 * <p>Any other parameter is ignored. The search, its filter and its page are handed to the source,
 * which matches, filters, orders, pages and counts; the answer names the page's {@code startIndex}
 * as it was asked for. A failure of the source is the app-wide handler's {@code 502}.
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

    /**
     * The gender filter reads in FHIR's spelling, which is no Java constant's name: a value that
     * spells neither gender fails the binding.
     */
    @InitBinder
    void spellings(WebDataBinder binder) {
        binder.registerCustomEditor(AdministrativeGender.class, spelled(PatientFilter::genderOf));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('patient:read')")
    public PatientSearchPage search(
            @RequestParam PatientQuery q,
            @RequestParam(required = false) AdministrativeGender gender,
            @RequestParam(required = false) PageParameter startIndex,
            @RequestParam(required = false) PageParameter count) {
        PageRequest page = PageRequest.of(startIndex, count);
        return PatientSearchPage.of(searches.search(q, new PatientFilter(gender), page),
                page.startIndex());
    }

    /** An editor reading a parameter's text through {@code read}, whose refusal fails the binding. */
    private static PropertyEditorSupport spelled(Function<String, ?> read) {
        return new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                setValue(read.apply(text));
            }
        };
    }
}
