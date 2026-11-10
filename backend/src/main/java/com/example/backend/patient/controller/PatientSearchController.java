package com.example.backend.patient.controller;

import com.example.backend.paging.domain.PageParameter;
import com.example.backend.paging.domain.PageRequest;
import com.example.backend.patient.application.PatientSearchService;
import com.example.backend.patient.domain.AdministrativeGender;
import com.example.backend.patient.domain.DateRange;
import com.example.backend.patient.domain.PatientFilter;
import com.example.backend.patient.domain.PatientQuery;
import com.example.backend.patient.domain.PatientSort;
import java.beans.PropertyEditorSupport;
import java.time.LocalDate;
import java.util.function.Function;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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
 *   <li>The filters bind to typed values: {@code gender} to male or female
 *       ({@link PatientFilter#genderOf}), and {@code birthDateFrom}, {@code birthDateTo},
 *       {@code lastVisitFrom} and {@code lastVisitTo} to calendar dates, {@code YYYY-MM-DD}; any
 *       other value is the binder's {@code 400}. A from-date after its to-date is the same
 *       {@code 400} ({@link DateRange}), the refusal its cause.
 *   <li>{@code sortBy} binds to {@code birthDate} or {@code lastVisit} and {@code sortOrder} to
 *       {@code ascending} or {@code descending} ({@link PatientSort}); any other value is the
 *       binder's {@code 400}. A {@code sortOrder} with no {@code sortBy} names a direction for no
 *       order, and the search takes its default, newest Last visit first.
 *   <li>{@code startIndex} and {@code count} are read by ADR 0017's rules
 *       ({@link PageRequest#of}): coerced, never refused, and a value that is no integer is the
 *       binder's {@code 400}.
 * </ul>
 *
 * <p>The search, its filters, its sort and its page are handed to the source, which matches,
 * filters, sorts, pages and counts; the answer names the page's {@code startIndex} as it was
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

    /**
     * The gender filter and the sort read in their own spellings, which are no Java constant's
     * name: a value that spells none of them fails the binding, as a malformed date does.
     */
    @InitBinder
    void spellings(WebDataBinder binder) {
        binder.registerCustomEditor(AdministrativeGender.class, spelled(PatientFilter::genderOf));
        binder.registerCustomEditor(PatientSort.Field.class, spelled(PatientSort.Field::of));
        binder.registerCustomEditor(PatientSort.Order.class, spelled(PatientSort.Order::of));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('patient:read')")
    public PatientSearchPage search(
            @RequestParam PatientQuery q,
            @RequestParam(required = false) AdministrativeGender gender,
            @RequestParam(required = false) @DateTimeFormat(pattern = DATE) LocalDate birthDateFrom,
            @RequestParam(required = false) @DateTimeFormat(pattern = DATE) LocalDate birthDateTo,
            @RequestParam(required = false) @DateTimeFormat(pattern = DATE) LocalDate lastVisitFrom,
            @RequestParam(required = false) @DateTimeFormat(pattern = DATE) LocalDate lastVisitTo,
            @RequestParam(required = false) PatientSort.Field sortBy,
            @RequestParam(required = false) PatientSort.Order sortOrder,
            @RequestParam(required = false) PageParameter startIndex,
            @RequestParam(required = false) PageParameter count) {
        PatientFilter filter = new PatientFilter(gender, range(birthDateFrom, birthDateTo),
                range(lastVisitFrom, lastVisitTo));
        PageRequest page = PageRequest.of(startIndex, count);
        return PatientSearchPage.of(
                searches.search(q, filter, PatientSort.of(sortBy, sortOrder), page),
                page.startIndex());
    }

    /** A calendar date, exactly: four digits of year, two of month and two of day. */
    private static final String DATE = "yyyy-MM-dd";

    /** The range from {@code from} to {@code to}, or this request's {@code 400} for an inverted one. */
    private static DateRange range(LocalDate from, LocalDate to) {
        try {
            return new DateRange(from, to);
        } catch (IllegalArgumentException inverted) {
            throw new InvertedDateRangeException(inverted);
        }
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

    /**
     * A from-date after its to-date: the caller's error, answered as a parameter that cannot be
     * bound is — the application API's generic {@code 400}, with its one refusal record. The
     * refusal travels as the cause, so the record names it as the reason; it carries no message of
     * its own and nothing the caller sent.
     */
    static final class InvertedDateRangeException extends ResponseStatusException {

        InvertedDateRangeException(IllegalArgumentException inverted) {
            super(HttpStatus.BAD_REQUEST, null, inverted);
        }
    }
}
