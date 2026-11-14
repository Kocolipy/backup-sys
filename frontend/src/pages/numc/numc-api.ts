/**
 * The endpoint the NUMC page reads — the patient in context's NUMC History —
 * `/frontend/AGENTS.md`'s page-owned `api.ts`, named for its page.
 *
 * Nothing here requests anything: the NUMC page sends {@link NUMC_HISTORY}
 * through `usePagedRead` (behind `useSessionRequest`), so a `401` still ends
 * the session in one place. This file says what a response looks like, names
 * each code of a NUMC Record's two closed sets, says what the Filter panel's
 * filters and the sort are and which of them may be sent, and declares the
 * read — its path, decoder and Permission — once.
 */

import type { ReadDeclaration } from "@/auth/declarations";
import { codeParameters } from "@/lib/code-parameters";
import { calendarDate, jsonDecoder, readObject } from "@/lib/decode";
import { pageDecoder, pagedPath, type Page, type PageAt } from "@/lib/paging";
import { sortParameters, type ColumnSort } from "@/lib/sorting";
import {
  countHistoryDates,
  type DateColumnOf,
  historyDateParameters,
  type HistoryDateColumns,
  type HistoryDates,
  noHistoryDates,
} from "@/pages/history-api";

// A NUMC Record's two closed sets, each as the API carries it, by its codes,
// in the order the Filter panel lists them. Each is exported for the SPA–API
// contract check (`/scripts/spa-contract/`), which holds it to its enum in
// `/backend/docs/openapi.yaml` from outside this app's import graph. The NUMC
// Outcome is a set of its own, apart from an FFI's Outcome.

/** A NUMC Record's NUMC Type: the underwater duty. */
export const NUMC_TYPES = ["hyperbaric-chamber", "submariner", "diving"] as const;

/** A NUMC Record's NUMC Outcome. */
export const NUMC_OUTCOMES = ["fit", "unfit", "waiver-granted", "waiver-rejected"] as const;

export type NumcType = (typeof NUMC_TYPES)[number];
export type NumcOutcome = (typeof NUMC_OUTCOMES)[number];

/** What the page calls each NUMC Type. */
export const NUMC_TYPE_LABELS: Readonly<Record<NumcType, string>> = {
  diving: "Diving Duties",
  "hyperbaric-chamber": "Hyperbaric Chamber Duties",
  submariner: "Submariner Duties",
};

/** What the page calls each NUMC Outcome. */
export const NUMC_OUTCOME_LABELS: Readonly<Record<NumcOutcome, string>> = {
  fit: "FIT",
  unfit: "UNFIT",
  "waiver-granted": "WAIVER GRANTED",
  "waiver-rejected": "WAIVER REJECTED",
};

/**
 * One NUMC Record of a patient's NUMC History, as the backend answers it: its
 * id, which the page keeps but never shows, each closed set's code, and its
 * Issue Date and Expiry Date, each `YYYY-MM-DD`. It has no Status.
 */
export interface NumcRecord {
  id: string;
  type: NumcType;
  outcome: NumcOutcome;
  issueDate: string;
  expiryDate: string;
}

const NUMC_RECORD_FIELDS = [
  "id",
  "type",
  "outcome",
  "issueDate",
  "expiryDate",
] as const satisfies readonly (keyof NumcRecord)[];

/** The page size the NUMC page asks for, and its pager moves by. */
export const NUMC_HISTORY_PAGE_SIZE = 5;

/**
 * A NUMC Record, read strictly: every element is required, each code must be
 * one of its set's, each date a calendar date, and a field the record does not
 * have is refused rather than dropped.
 */
export const decodeNumcRecord = (value: unknown): NumcRecord => {
  const record = readObject(value, "NumcRecord");
  const decoded: NumcRecord = {
    id: record.string("id"),
    type: record.oneOf("type", NUMC_TYPES),
    outcome: record.oneOf("outcome", NUMC_OUTCOMES),
    issueDate: calendarDate(record.string("issueDate"), "NumcRecord.issueDate"),
    expiryDate: calendarDate(record.string("expiryDate"), "NumcRecord.expiryDate"),
  };
  record.onlyFields(NUMC_RECORD_FIELDS);
  return decoded;
};

/**
 * The NUMC History's date columns, both calendar dates, in the order the
 * Filter panel lists their ranges: each its own range filter and a column the
 * history sorts by.
 */
export const NUMC_DATE_COLUMNS = [
  { column: "issueDate", label: "Issue Date", precision: "date" },
  { column: "expiryDate", label: "Expiry Date", precision: "date" },
] as const satisfies HistoryDateColumns<string>;

/** A column the NUMC History sorts by, as the backend's `sortBy` names it. */
export type NumcSortColumn = DateColumnOf<typeof NUMC_DATE_COLUMNS>;

/** How the NUMC History is sorted: one date, in one direction. */
export type NumcSort = ColumnSort<NumcSortColumn>;

/**
 * The Filter panel's filters, each as its control holds it: the codes chosen
 * of each closed set, none for no filter, and a range over each of
 * {@link NUMC_DATE_COLUMNS}. Every code the panel offers is one of its set's,
 * so only the dates are checked before sending (`checkDateRanges`).
 */
export interface NumcFilters extends HistoryDates<NumcSortColumn> {
  types: readonly NumcType[];
  outcomes: readonly NumcOutcome[];
}

/** No filter: every NUMC Record of the patient. */
export const NO_NUMC_FILTERS: NumcFilters = {
  ...noHistoryDates(NUMC_DATE_COLUMNS),
  outcomes: [],
  types: [],
};

/** How many of `filters` narrow the history: each set of codes chosen, as one, and each date set. */
export function countNumcFilters(filters: NumcFilters): number {
  const chosen = [filters.types.length > 0, filters.outcomes.length > 0].filter(Boolean).length;
  return chosen + countHistoryDates(NUMC_DATE_COLUMNS, filters);
}

/** The query string `filters` and `sort` ask for, each filter only when set. */
function historyQuery(filters: NumcFilters, sort: NumcSort | null): string {
  return [
    ...codeParameters("type", NUMC_TYPES, filters.types),
    ...codeParameters("outcome", NUMC_OUTCOMES, filters.outcomes),
    ...historyDateParameters(NUMC_DATE_COLUMNS, filters),
    ...sortParameters(sort),
  ].join("&");
}

/**
 * The NUMC History of the patient `patientId` that `filters` narrow, a page
 * at a time, in the order `sort` names, `GET /api/patient-numc/{patientId}`:
 * the patient source's NUMC Records, in its own order when `sort` is `null`.
 * The source filters and sorts; the backend passes both through. A `404` is
 * an ordinary answer — the source holds no patient under the id — so it
 * reads as `absent`.
 */
export const NUMC_HISTORY: ReadDeclaration<
  Page<NumcRecord>,
  [patientId: string, filters: NumcFilters, sort: NumcSort | null, at: PageAt],
  true
> = {
  absentOnNotFound: true,
  decode: jsonDecoder(pageDecoder("NumcHistoryPage", "records", decodeNumcRecord)),
  path: (patientId, filters, sort, at) =>
    pagedPath(
      `/api/patient-numc/${encodeURIComponent(patientId)}`,
      at,
      historyQuery(filters, sort),
    ),
  permission: "patient:read",
};
