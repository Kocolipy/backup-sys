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

/** The Filter panel's filter as its control holds it: the gender, `all` for no filter. */
export interface PatientFilters {
  gender: "all" | "male" | "female";
}

/** No filter: every Patient the query matches. */
export const NO_PATIENT_FILTERS: PatientFilters = { gender: "all" };

/** How many of `filters` narrow the results: the gender, when it is set. */
export function countPatientFilters(filters: PatientFilters): number {
  return filters.gender === "all" ? 0 : 1;
}

/** The query string `query` and `filters` ask for, the gender only when set. */
function searchQuery(query: string, filters: PatientFilters): string {
  const parts = [`q=${encodeURIComponent(query)}`];
  if (filters.gender !== "all") parts.push(`gender=${filters.gender}`);
  return parts.join("&");
}

/**
 * The Patients `query` matches and `filters` narrow, a page at a time,
 * `GET /api/patient-search?q=…`: the Patient source's matches, in its own
 * order. The source filters and orders; the backend passes the search through.
 */
export const PATIENT_SEARCH: ReadDeclaration<
  Page<PatientMatch>,
  [query: string, filters: PatientFilters, at: PageAt]
> = {
  decode: jsonDecoder(pageDecoder("PatientSearchPage", "patients", decodePatientMatch)),
  path: (query, filters, at) => pagedPath(PATIENT_SEARCH_PATH, at, searchQuery(query, filters)),
  permission: "patient:read",
};
