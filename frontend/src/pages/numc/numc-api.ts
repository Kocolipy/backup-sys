/**
 * The endpoint the NUMC page reads — the patient in context's NUMC History —
 * `/frontend/AGENTS.md`'s page-owned `api.ts`, named for its page.
 *
 * Nothing here requests anything: the NUMC page sends {@link NUMC_HISTORY}
 * through `usePagedRead` (behind `useSessionRequest`), so a `401` still ends
 * the session in one place. This file says what a response looks like, names
 * each code of a NUMC Record's two closed sets, and declares the read — its
 * path, decoder and Permission — once.
 */

import type { ReadDeclaration } from "@/auth/declarations";
import { calendarDate, jsonDecoder, readObject } from "@/lib/decode";
import { pageDecoder, pagedPath, type Page, type PageAt } from "@/lib/paging";

// A NUMC Record's two closed sets, each as the API carries it, by its codes.
// Each is exported for the SPA–API contract check (`/scripts/spa-contract/`),
// which holds it to its enum in `/backend/docs/openapi.yaml` from outside this
// app's import graph, and nothing in `src/` but the decoder below reads them.
// The NUMC Outcome is a set of its own, apart from an FFI's Outcome.

/** A NUMC Record's NUMC Type: the underwater duty. */
// fallow-ignore-next-line unused-export
export const NUMC_TYPES = ["hyperbaric-chamber", "submariner", "diving"] as const;

/** A NUMC Record's NUMC Outcome. */
// fallow-ignore-next-line unused-export
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
 * The NUMC History of the patient `patientId`, a page at a time,
 * `GET /api/patient-numc/{patientId}`: the patient source's NUMC Records, in
 * its own order, with no sort and no filter. A `404` is an ordinary answer —
 * the source holds no patient under the id — so it reads as `absent`.
 */
export const NUMC_HISTORY: ReadDeclaration<
  Page<NumcRecord>,
  [patientId: string, at: PageAt],
  true
> = {
  absentOnNotFound: true,
  decode: jsonDecoder(pageDecoder("NumcHistoryPage", "records", decodeNumcRecord)),
  path: (patientId, at) => pagedPath(`/api/patient-numc/${encodeURIComponent(patientId)}`, at),
  permission: "patient:read",
};
