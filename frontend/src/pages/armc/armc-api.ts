/**
 * The endpoint the ARMC page reads — the patient in context's ARMC History —
 * `/frontend/AGENTS.md`'s page-owned `api.ts`, named for its page.
 *
 * Nothing here requests anything: the ARMC page sends {@link ARMC_HISTORY}
 * through `usePagedRead` (behind `useSessionRequest`), so a `401` still ends
 * the session in one place. This file says what a response looks like, names
 * each code of an ARMC Board's two closed sets, says what the Filter panel's
 * filters and the sort are and which of them may be sent, and declares the
 * read — its path, decoder and Permission — once.
 */

import type { ReadDeclaration } from "@/auth/declarations";
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

// An ARMC Board's two closed sets, each as the API carries it, by its codes,
// in the order the Filter panel lists the Outcome's and its labels and badges
// name them. Each is exported for the SPA–API contract check
// (`/scripts/spa-contract/`), which holds it to its enum in
// `/backend/docs/openapi.yaml` from outside this app's import graph. A board
// may carry a code outside its set, shown as it came (ADR 0020); only the
// Filter panel is limited to these. The ARMC Outcome is a set of its own,
// apart from an FFI's Outcome and a NUMC Record's NUMC Outcome.

/** An ARMC Board's ARMC Board Type: the kind of aeromedical fitness board. */
// fallow-ignore-next-line unused-export
export const ARMC_BOARD_TYPES = ["aces"] as const;

/** An ARMC Board's ARMC Outcome. */
export const ARMC_OUTCOMES = ["fit", "unfit"] as const;

export type ArmcBoardType = (typeof ARMC_BOARD_TYPES)[number];
export type ArmcOutcome = (typeof ARMC_OUTCOMES)[number];

/** What the page calls each ARMC Board Type. */
export const ARMC_BOARD_TYPE_LABELS: Readonly<Record<ArmcBoardType, string>> = {
  aces: "ACES",
};

/** What the page calls each ARMC Outcome. */
export const ARMC_OUTCOME_LABELS: Readonly<Record<ArmcOutcome, string>> = {
  fit: "FIT",
  unfit: "UNFIT",
};

/**
 * One ARMC Board of a patient's ARMC History, shown as the patient source
 * sent it (ADR 0020): each element present, missing or in the wrong format —
 * its id, which the page keys its row by but never shows, each closed set's
 * code, known to the set or not, its Start Date and End Date, each
 * `YYYY-MM-DD` — the End Date missing for a board still open — and its two
 * Medical Officers' names as the source gives them.
 */
export interface ArmcBoard {
  id: SourceField<string>;
  boardType: SourceField<string>;
  outcome: SourceField<string>;
  startDate: SourceField<string>;
  endDate: SourceField<string>;
  medicalOfficer1: SourceField<string>;
  medicalOfficer2: SourceField<string>;
}

/** Every field the backend sends of an ARMC Board: its elements, and which were invalid. */
const ARMC_BOARD_FIELDS = [
  "id",
  "boardType",
  "outcome",
  "startDate",
  "endDate",
  "medicalOfficer1",
  "medicalOfficer2",
  "invalid",
] as const satisfies readonly (keyof ArmcBoard | "invalid")[];

/** The page size the ARMC page asks for, and its pager moves by. */
export const ARMC_HISTORY_PAGE_SIZE = 5;

/**
 * An ARMC Board, its shape read strictly — every field there, and a field the
 * board does not have refused rather than dropped — and each element's value
 * leniently, by the lenient field decoder: a code is any string, a date a
 * calendar date or invalid, and nothing an element holds refuses the board.
 */
export const decodeArmcBoard = (value: unknown): ArmcBoard => {
  const board = readObject(value, "ArmcBoard");
  const fields = board.sourceFields();
  const decoded: ArmcBoard = {
    id: fields.read("id"),
    boardType: fields.read("boardType"),
    outcome: fields.read("outcome"),
    startDate: fields.read("startDate", isCalendarDate),
    endDate: fields.read("endDate", isCalendarDate),
    medicalOfficer1: fields.read("medicalOfficer1"),
    medicalOfficer2: fields.read("medicalOfficer2"),
  };
  board.onlyFields(ARMC_BOARD_FIELDS);
  return decoded;
};

/**
 * The ARMC History's date columns, both calendar dates, in the order the
 * Filter panel lists their ranges: each its own range filter and a column the
 * history sorts by.
 */
export const ARMC_DATE_COLUMNS = [
  { column: "startDate", label: "Start Date", precision: "date" },
  { column: "endDate", label: "End Date", precision: "date" },
] as const satisfies HistoryDateColumns<string>;

/** A column the ARMC History sorts by, as the backend's `sortBy` names it. */
export type ArmcSortColumn = DateColumnOf<typeof ARMC_DATE_COLUMNS>;

/** How the ARMC History is sorted: one date, in one direction. */
export type ArmcSort = ColumnSort<ArmcSortColumn>;

/**
 * The Filter panel's filters, each as its control holds it: the ARMC Outcome,
 * `all` for either, and a range over each of {@link ARMC_DATE_COLUMNS}. Every
 * Outcome the panel offers is one of its set's, so only the dates are checked
 * before sending (`checkDateRanges`). The ARMC Board Type takes no filter
 * while ACES is its one code.
 */
export interface ArmcFilters extends HistoryDates<ArmcSortColumn> {
  outcome: "all" | ArmcOutcome;
}

/** No filter: every ARMC Board of the patient. */
export const NO_ARMC_FILTERS: ArmcFilters = {
  ...noHistoryDates(ARMC_DATE_COLUMNS),
  outcome: "all",
};

/** How many of `filters` narrow the history: the Outcome, when one is chosen, and each date set. */
export function countArmcFilters(filters: ArmcFilters): number {
  return (filters.outcome === "all" ? 0 : 1) + countHistoryDates(ARMC_DATE_COLUMNS, filters);
}

/** The query string `filters` and `sort` ask for, each filter only when set. */
function historyQuery(filters: ArmcFilters, sort: ArmcSort | null): string {
  return [
    ...(filters.outcome === "all" ? [] : [`outcome=${filters.outcome}`]),
    ...historyDateParameters(ARMC_DATE_COLUMNS, filters),
    ...sortParameters(sort),
  ].join("&");
}

/**
 * The ARMC History of the patient `patientId` that `filters` narrow, a page
 * at a time, in the order `sort` names, `GET /api/patient-armc/{patientId}`:
 * the patient source's ARMC Boards, in its own order when `sort` is `null`.
 * The source filters and sorts, and breaks the sort's ties; the backend passes
 * both through. A `404` is an ordinary answer — the source holds no patient
 * under the id — so it reads as `absent`.
 */
export const ARMC_HISTORY: ReadDeclaration<
  Page<ArmcBoard>,
  [patientId: string, filters: ArmcFilters, sort: ArmcSort | null, at: PageAt],
  true
> = {
  absentOnNotFound: true,
  decode: jsonDecoder(pageDecoder("ArmcHistoryPage", "records", decodeArmcBoard)),
  path: (patientId, filters, sort, at) =>
    pagedPath(
      `/api/patient-armc/${encodeURIComponent(patientId)}`,
      at,
      historyQuery(filters, sort),
    ),
  permission: "patient:read",
};
