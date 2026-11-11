/**
 * The endpoint the Search page reads — the Patients a search matches —
 * `/frontend/AGENTS.md`'s page-owned `api.ts`, named for its page.
 *
 * Nothing here requests anything: the Search page sends
 * {@link PATIENT_SEARCH} through `usePagedRead` (behind `useSessionRequest`),
 * so a `401` still ends the session in one place. This file says what a
 * response looks like, which text is a search at all, and declares the read —
 * its path, decoder and Permission — once.
 */

import type { ReadDeclaration } from "@/auth/declarations";
import { DecodeError, jsonDecoder, readObject } from "@/lib/decode";
import { pageDecoder, pagedPath, type Page, type PageAt } from "@/lib/paging";
import { isCalendarDate } from "@/lib/time";
import { PATIENT_GENDERS, type PatientGender } from "@/lib/patient-gender";
import { sortParameters, type ColumnSort } from "@/lib/sorting";

/**
 * One Patient a search matched, as the backend answers it: the Patient's id
 * (its EHR's FHIR id, the id `GET /api/patient/{id}` reads it by), which the
 * page keeps but never shows, the official name's text, the full NRIC, the
 * gender code, the date of birth and the Last visit, each date `YYYY-MM-DD`,
 * and the Last visit `null` for a Patient with none.
 */
export interface PatientMatch {
  id: string;
  name: string;
  nric: string;
  gender: PatientGender;
  birthDate: string;
  lastVisit: string | null;
}

const PATIENT_SEARCH_PATH = "/api/patient-search";

/** The page size the Search page asks for, and its pager moves by. */
export const PATIENT_SEARCH_PAGE_SIZE = 5;

/** The fewest characters a name search takes. */
const SHORTEST_NAME = 3;

/** What the box typed is, as a search: the text sent, or why nothing is sent. */
export type ParsedPatientQuery = { ok: true; query: string } | { ok: false; message: string };

/**
 * The search `typed` asks for, trimmed, refused here — so nothing is sent —
 * when it is under three characters, counted as the backend counts them, by
 * code point. A full NRIC is nine characters, so every one is sent; which
 * kind of search the text is, NRIC or name, the backend alone decides, and a
 * partial NRIC is a name search like any other text.
 */
export function parsePatientQuery(typed: string): ParsedPatientQuery {
  const query = typed.trim();
  if ([...query].length >= SHORTEST_NAME) return { ok: true, query };
  return { message: `Enter at least ${SHORTEST_NAME} characters to search by name.`, ok: false };
}

/** A FHIR `date` to the day, as the backend sends a Patient's dates. */
function calendarDate(value: string | null, what: string): string | null {
  if (value !== null && !isCalendarDate(value)) throw new DecodeError(`${what} is not a date`);
  return value;
}

export const decodePatientMatch = (value: unknown): PatientMatch => {
  const patient = readObject(value, "PatientMatch");
  const birthDate = patient.string("birthDate");
  calendarDate(birthDate, "PatientMatch.birthDate");
  return {
    id: patient.string("id"),
    name: patient.string("name"),
    nric: patient.string("nric"),
    gender: patient.oneOf("gender", PATIENT_GENDERS),
    birthDate,
    lastVisit: calendarDate(patient.nullableString("lastVisit"), "PatientMatch.lastVisit"),
  };
};

/** A column the results sort by, as the backend's `sortBy` names it. */
export type PatientSortColumn = "birthDate" | "lastVisit";

/** How the results are sorted: one column, in one direction. */
export type PatientSort = ColumnSort<PatientSortColumn>;

/**
 * The Filter panel's filters, each as its control holds it: the gender, `all`
 * for no filter, and the four `<input type="date">` values, `YYYY-MM-DD` or
 * `""` for an end left open.
 */
export interface PatientFilters {
  gender: "all" | "male" | "female";
  birthDateFrom: string;
  birthDateTo: string;
  lastVisitFrom: string;
  lastVisitTo: string;
}

/** No filter: every Patient the query matches. */
export const NO_PATIENT_FILTERS: PatientFilters = {
  birthDateFrom: "",
  birthDateTo: "",
  gender: "all",
  lastVisitFrom: "",
  lastVisitTo: "",
};

/** A date range the panel offers, by the prefix of its two fields, and its label. */
export const PATIENT_DATE_RANGES = [
  { label: "Date of Birth", range: "birthDate" },
  { label: "Last Visit", range: "lastVisit" },
] as const;

/** Which range a refusal is about. */
export type PatientDateRange = (typeof PATIENT_DATE_RANGES)[number]["range"];

/** The filters either passed, or refused with the range at fault and why. */
export type CheckedPatientFilters =
  { ok: true } | { ok: false; range: PatientDateRange; message: string };

/**
 * Whether `filters` may be sent: each date set is one the calendar has, and
 * no from-date is after its to-date — both ends are inclusive, so a range of
 * one day passes. The backend refuses either with a `400`, so the panel
 * refuses it first and sends nothing.
 */
export function checkPatientFilters(filters: PatientFilters): CheckedPatientFilters {
  for (const { label, range } of PATIENT_DATE_RANGES) {
    const from = filters[`${range}From`];
    const to = filters[`${range}To`];
    if ([from, to].some((end) => end !== "" && !isCalendarDate(end))) {
      return { message: `Enter the ${label} dates as real dates.`, ok: false, range };
    }
    // Both are `YYYY-MM-DD`, so their text orders as their days do; an open
    // from-date, "", is before every to-date, and an open to-date bounds nothing.
    if (to !== "" && from > to) {
      return { message: `The ${label} from-date must not be after its to-date.`, ok: false, range };
    }
  }
  return { ok: true };
}

/** How many of `filters` narrow the results: the gender, and each date set. */
export function countPatientFilters(filters: PatientFilters): number {
  return [
    filters.gender !== "all",
    filters.birthDateFrom !== "",
    filters.birthDateTo !== "",
    filters.lastVisitFrom !== "",
    filters.lastVisitTo !== "",
  ].filter(Boolean).length;
}

/** The query string `query`, `filters` and `sort` ask for, each filter only when set. */
function searchQuery(query: string, filters: PatientFilters, sort: PatientSort | null): string {
  const parts = [`q=${encodeURIComponent(query)}`];
  if (filters.gender !== "all") parts.push(`gender=${filters.gender}`);
  for (const field of ["birthDateFrom", "birthDateTo", "lastVisitFrom", "lastVisitTo"] as const) {
    if (filters[field] !== "") parts.push(`${field}=${filters[field]}`);
  }
  return [...parts, ...sortParameters(sort)].join("&");
}

/**
 * The Patients `query` matches and `filters` narrow, a page at a time, in the
 * order `sort` names, `GET /api/patient-search?q=…`: the Patient source's
 * matches, in its order — newest Last visit first when `sort` is `null`. The
 * source filters and sorts; the backend passes both through.
 */
export const PATIENT_SEARCH: ReadDeclaration<
  Page<PatientMatch>,
  [query: string, filters: PatientFilters, sort: PatientSort | null, at: PageAt]
> = {
  decode: jsonDecoder(pageDecoder("PatientSearchPage", "patients", decodePatientMatch)),
  path: (query, filters, sort, at) =>
    pagedPath(PATIENT_SEARCH_PATH, at, searchQuery(query, filters, sort)),
  permission: "patient:read",
};
