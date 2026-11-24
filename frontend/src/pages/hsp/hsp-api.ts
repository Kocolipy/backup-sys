/**
 * The endpoint the HSP page reads — the patient in context's HSP History —
 * `/frontend/AGENTS.md`'s page-owned `api.ts`, named for its page.
 *
 * Nothing here requests anything: the HSP page sends {@link HSP_HISTORY}
 * through `usePagedRead` (behind `useSessionRequest`), so a `401` still ends
 * the session in one place. This file says what a response looks like, names
 * each code of the two closed sets an HSP Record has of its own — its
 * Submitting MC draws on the shared Medical Centre
 * (`@/pages/medical-centre-api`) — says what the Filter panel's filters and the
 * sort are and which of them may be sent, and declares the read — its path,
 * decoder and Permission — once.
 */

import type { ReadDeclaration } from "@/auth/declarations";
import { codeParameters } from "@/lib/code-parameters";
import { jsonDecoder, readObject } from "@/lib/decode";
import { pageDecoder, pagedPath, type Page, type PageAt } from "@/lib/paging";
import { sortParameters, type ColumnSort } from "@/lib/sorting";
import type { SourceField } from "@/lib/source-field";
import { isCalendarDate } from "@/lib/time";
import {
  countHistoryDates,
  type DateColumnOf,
  historyDateParameters,
  type HistoryDateColumns,
  type HistoryDates,
  noHistoryDates,
} from "@/pages/history-api";
import { MEDICAL_CENTRES, type MedicalCentre } from "@/pages/medical-centre-api";

// The two closed sets an HSP Record has of its own, each as the API carries
// it, by its codes, in the order the Filter panel lists them and its labels
// name them. Each is exported for the SPA–API contract check
// (`/scripts/spa-contract/`), which holds it to its enum in
// `/backend/docs/openapi.yaml` from outside this app's import graph, and the
// Filter panel lists both from it. A record may carry a code outside either
// set, shown as it came (ADR 0020); only the Filter panel is limited to these.

/** An HSP Record's Outcome. */
export const HSP_OUTCOMES = ["low", "moderate", "high"] as const;

/**
 * An HSP Record's Status: the patient source's own, never derived from the End
 * Date, so a record past it may still be `active`.
 */
export const HSP_STATUSES = ["active", "inactive"] as const;

export type HspOutcome = (typeof HSP_OUTCOMES)[number];
export type HspStatus = (typeof HSP_STATUSES)[number];

/** What the page calls each Outcome. */
export const HSP_OUTCOME_LABELS: Readonly<Record<HspOutcome, string>> = {
  high: "HIGH",
  low: "LOW",
  moderate: "MODERATE",
};

/** What the page calls each Status. */
export const HSP_STATUS_LABELS: Readonly<Record<HspStatus, string>> = {
  active: "Active",
  inactive: "Inactive",
};

/**
 * One HSP Record of a patient's HSP History, shown as the patient source sent
 * it (ADR 0020): each field present, missing or in the wrong format — its id,
 * which the page keys its row by but never shows, each closed set's code,
 * known to the set or not, and its Start Date and End Date, each `YYYY-MM-DD`.
 */
export interface HspRecord {
  id: SourceField<string>;
  outcome: SourceField<string>;
  startDate: SourceField<string>;
  endDate: SourceField<string>;
  submittingMc: SourceField<string>;
  status: SourceField<string>;
}

/** Every field the backend sends of an HSP Record: its own, and which were invalid. */
const HSP_FIELDS = [
  "id",
  "outcome",
  "startDate",
  "endDate",
  "submittingMc",
  "status",
  "invalid",
] as const satisfies readonly (keyof HspRecord | "invalid")[];

/** The page size the HSP page asks for, and its pager moves by. */
export const HSP_HISTORY_PAGE_SIZE = 5;

/**
 * An HSP Record, its shape read strictly — every field there, and a field the
 * record does not have refused rather than dropped — and each field's value
 * leniently, by the lenient field decoder: a code is any string, a date a
 * calendar date or invalid, and nothing a field holds refuses the record.
 */
export const decodeHspRecord = (value: unknown): HspRecord => {
  const record = readObject(value, "HspRecord");
  const fields = record.sourceFields();
  const decoded: HspRecord = {
    id: fields.read("id"),
    outcome: fields.read("outcome"),
    startDate: fields.read("startDate", isCalendarDate),
    endDate: fields.read("endDate", isCalendarDate),
    submittingMc: fields.read("submittingMc"),
    status: fields.read("status"),
  };
  record.onlyFields(HSP_FIELDS);
  return decoded;
};

/**
 * The HSP History's date columns, both calendar dates, in the order the Filter
 * panel lists their ranges: each its own range filter and a column the
 * history sorts by.
 */
export const HSP_DATE_COLUMNS = [
  { column: "startDate", label: "Start Date", precision: "date" },
  { column: "endDate", label: "End Date", precision: "date" },
] as const satisfies HistoryDateColumns<string>;

/** A column the HSP History sorts by, as the backend's `sortBy` names it. */
export type HspSortColumn = DateColumnOf<typeof HSP_DATE_COLUMNS>;

/** How the HSP History is sorted: one date, in one direction. */
export type HspSort = ColumnSort<HspSortColumn>;

/**
 * The Filter panel's filters, each as its control holds it: the Outcomes and
 * the Submitting MCs chosen, none for no filter; the Status, `all` for either;
 * and a range over each of {@link HSP_DATE_COLUMNS}. Every code the panel
 * offers is one of its set's, so only the dates are checked before sending
 * (`checkDateRanges`).
 */
export interface HspFilters extends HistoryDates<HspSortColumn> {
  outcomes: readonly HspOutcome[];
  submittingMcs: readonly MedicalCentre[];
  status: "all" | HspStatus;
}

/** No filter: every HSP Record of the patient. */
export const NO_HSP_FILTERS: HspFilters = {
  ...noHistoryDates(HSP_DATE_COLUMNS),
  outcomes: [],
  status: "all",
  submittingMcs: [],
};

/**
 * How many of `filters` narrow the history: each set of codes chosen, as one,
 * the Status, and each date set.
 */
export function countHspFilters(filters: HspFilters): number {
  const chosen = [
    filters.outcomes.length > 0,
    filters.submittingMcs.length > 0,
    filters.status !== "all",
  ].filter(Boolean).length;
  return chosen + countHistoryDates(HSP_DATE_COLUMNS, filters);
}

/** The query string `filters` and `sort` ask for, each filter only when set. */
function historyQuery(filters: HspFilters, sort: HspSort | null): string {
  return [
    ...codeParameters("outcome", HSP_OUTCOMES, filters.outcomes),
    ...historyDateParameters(HSP_DATE_COLUMNS, filters),
    ...codeParameters("submittingMc", MEDICAL_CENTRES, filters.submittingMcs),
    ...(filters.status === "all" ? [] : [`status=${filters.status}`]),
    ...sortParameters(sort),
  ].join("&");
}

/**
 * The HSP History of the patient `patientId` that `filters` narrow, a page at
 * a time, in the order `sort` names, `GET /api/patient-hsp/{patientId}`: the
 * patient source's HSP Records, in its own order when `sort` is `null`. The
 * source filters and sorts; the backend passes both through. A `404` is an
 * ordinary answer — the source holds no patient under the id — so it reads as
 * `absent`.
 */
export const HSP_HISTORY: ReadDeclaration<
  Page<HspRecord>,
  [patientId: string, filters: HspFilters, sort: HspSort | null, at: PageAt],
  true
> = {
  absentOnNotFound: true,
  decode: jsonDecoder(pageDecoder("HspHistoryPage", "records", decodeHspRecord)),
  path: (patientId, filters, sort, at) =>
    pagedPath(`/api/patient-hsp/${encodeURIComponent(patientId)}`, at, historyQuery(filters, sort)),
  permission: "patient:read",
};
