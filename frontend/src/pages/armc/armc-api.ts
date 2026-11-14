/**
 * The endpoint the ARMC page reads — the patient in context's ARMC History —
 * `/frontend/AGENTS.md`'s page-owned `api.ts`, named for its page.
 *
 * Nothing here requests anything: the ARMC page sends {@link ARMC_HISTORY}
 * through `usePagedRead` (behind `useSessionRequest`), so a `401` still ends
 * the session in one place. This file says what a response looks like, names
 * each code of an ARMC Board's two closed sets, and declares the read — its
 * path, decoder and Permission — once.
 */

import type { ReadDeclaration } from "@/auth/declarations";
import { calendarDate, jsonDecoder, readObject } from "@/lib/decode";
import { pageDecoder, pagedPath, type Page, type PageAt } from "@/lib/paging";

// An ARMC Board's two closed sets, each as the API carries it, by its codes.
// Each is exported for the SPA–API contract check (`/scripts/spa-contract/`),
// which holds it to its enum in `/backend/docs/openapi.yaml` from outside this
// app's import graph, and nothing in `src/` but the decoder below reads them.
// The ARMC Outcome is a set of its own, apart from an FFI's Outcome and a
// NUMC Record's NUMC Outcome.

/** An ARMC Board's ARMC Board Type: the kind of aeromedical fitness board. */
// fallow-ignore-next-line unused-export
export const ARMC_BOARD_TYPES = ["aces"] as const;

/** An ARMC Board's ARMC Outcome. */
// fallow-ignore-next-line unused-export
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
 * One ARMC Board of a patient's ARMC History, as the backend answers it: its
 * id, which the page keeps but never shows, each closed set's code, its Start
 * Date and — unless the board is still open — its End Date, each
 * `YYYY-MM-DD`, and its two Medical Officers' names as the source gives them.
 */
export interface ArmcBoard {
  id: string;
  boardType: ArmcBoardType;
  outcome: ArmcOutcome;
  startDate: string;
  /** Absent for a board still open. */
  endDate?: string;
  medicalOfficer1: string;
  medicalOfficer2: string;
}

const ARMC_BOARD_FIELDS = [
  "id",
  "boardType",
  "outcome",
  "startDate",
  "endDate",
  "medicalOfficer1",
  "medicalOfficer2",
] as const satisfies readonly (keyof ArmcBoard)[];

/** The page size the ARMC page asks for, and its pager moves by. */
export const ARMC_HISTORY_PAGE_SIZE = 5;

/**
 * An ARMC Board, read strictly: every element is required but the End Date,
 * which the backend leaves out for a board still open; each code must be one
 * of its set's, each date a calendar date, and a field the board does not have
 * is refused rather than dropped.
 */
export const decodeArmcBoard = (value: unknown): ArmcBoard => {
  const board = readObject(value, "ArmcBoard");
  const endDate = board.optionalString("endDate");
  const decoded: ArmcBoard = {
    id: board.string("id"),
    boardType: board.oneOf("boardType", ARMC_BOARD_TYPES),
    outcome: board.oneOf("outcome", ARMC_OUTCOMES),
    startDate: calendarDate(board.string("startDate"), "ArmcBoard.startDate"),
    ...(endDate === undefined ? {} : { endDate: calendarDate(endDate, "ArmcBoard.endDate") }),
    medicalOfficer1: board.string("medicalOfficer1"),
    medicalOfficer2: board.string("medicalOfficer2"),
  };
  board.onlyFields(ARMC_BOARD_FIELDS);
  return decoded;
};

/**
 * The ARMC History of the patient `patientId`, a page at a time,
 * `GET /api/patient-armc/{patientId}`: the patient source's ARMC Boards, in
 * its own order, with no sort and no filter. A `404` is an ordinary answer —
 * the source holds no patient under the id — so it reads as `absent`.
 */
export const ARMC_HISTORY: ReadDeclaration<
  Page<ArmcBoard>,
  [patientId: string, at: PageAt],
  true
> = {
  absentOnNotFound: true,
  decode: jsonDecoder(pageDecoder("ArmcHistoryPage", "records", decodeArmcBoard)),
  path: (patientId, at) => pagedPath(`/api/patient-armc/${encodeURIComponent(patientId)}`, at),
  permission: "patient:read",
};
