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
import { jsonDecoder, readObject } from "@/lib/decode";
import { pageDecoder, pagedPath, type Page, type PageAt } from "@/lib/paging";
import type { SourceField } from "@/lib/source-field";
import { isCalendarDate } from "@/lib/time";

/**
 * One entry a search matched, as the backend answers it, shown as the patient
 * source sent it (ADR 0020): the Patient's id (its EHR's FHIR id, the id
 * `GET /api/patient/{id}` reads it by), which the page keeps but never shows,
 * and the official name's text, the full NRIC, the gender code — any code,
 * the four known or not — the date of birth and the Last visit, each date
 * `YYYY-MM-DD`, and each present, missing or in the wrong format. A match the
 * backend could not give a FHIR id it drops from the page; an entry that is no
 * Patient has no `id` — `null`, so nothing selects it — and every field invalid.
 */
export interface PatientMatch {
  id: string | null;
  name: SourceField<string>;
  nric: SourceField<string>;
  gender: SourceField<string>;
  birthDate: SourceField<string>;
  lastVisit: SourceField<string>;
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

/**
 * A match, its id read strictly — a string, or `null` for an entry that is no
 * Patient — and every other field leniently, by the lenient field decoder:
 * each present, missing or invalid, the dates calendar dates, never refusing
 * the match.
 */
export const decodePatientMatch = (value: unknown): PatientMatch => {
  const patient = readObject(value, "PatientMatch");
  const fields = patient.sourceFields();
  return {
    id: patient.nullableString("id"),
    name: fields.read("name"),
    nric: fields.read("nric"),
    gender: fields.read("gender"),
    birthDate: fields.read("birthDate", isCalendarDate),
    lastVisit: fields.read("lastVisit", isCalendarDate),
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
 * Its page carries the matches shown, where it starts, the source's total and
 * how many of its matches the backend dropped (`pageDecoder`'s `dropsEntries`).
 */
export const PATIENT_SEARCH: ReadDeclaration<
  Page<PatientMatch>,
  [query: string, filters: PatientFilters, at: PageAt]
> = {
  decode: jsonDecoder(
    pageDecoder("PatientSearchPage", "patients", decodePatientMatch, { dropsEntries: true }),
  ),
  path: (query, filters, at) => pagedPath(PATIENT_SEARCH_PATH, at, searchQuery(query, filters)),
  permission: "patient:read",
};
