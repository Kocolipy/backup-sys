/**
 * The endpoint the FFI page reads — the patient in context's FFI History —
 * `/frontend/AGENTS.md`'s page-owned `api.ts`, named for its page.
 *
 * Nothing here requests anything: the FFI page sends {@link FFI_HISTORY}
 * through `usePagedRead` (behind `useSessionRequest`), so a `401` still ends
 * the session in one place. This file says what a response looks like, names
 * each code of an FFI's five closed sets, and declares the read — its path,
 * decoder and Permission — once.
 */

import type { ReadDeclaration } from "@/auth/declarations";
import { DecodeError, jsonDecoder, readObject } from "@/lib/decode";
import { pageDecoder, pagedPath, type Page, type PageAt } from "@/lib/paging";
import { isCalendarDate } from "@/lib/time";

// An FFI's five closed sets, each as the API carries it, by its codes. Each is
// exported for the SPA–API contract check (`/scripts/spa-contract/`), which
// holds it to its enum in `/backend/docs/openapi.yaml` from outside this app's
// import graph; nothing in `src/` imports them.

/** An FFI's FFI Type. */
// fallow-ignore-next-line unused-export
export const FFI_TYPES = [
  "overseas-exercises",
  "cat-a-ocs-scs",
  "ippt",
  "dental",
  "ord-release-from-service",
  "vocational",
] as const;

/** An FFI's Outcome. */
// fallow-ignore-next-line unused-export
export const FFI_OUTCOMES = ["fit", "request-waiver", "unfit"] as const;

/** An FFI's Higher Authority. */
// fallow-ignore-next-line unused-export
export const FFI_HIGHER_AUTHORITIES = ["camo", "carmo", "cnmo", "comd-mmi", "dis"] as const;

/** An FFI's Submitting MC. */
// fallow-ignore-next-line unused-export
export const FFI_SUBMITTING_MCS = [
  "nee-soon-camp",
  "kranji-camp",
  "hendon-camp",
  "mandai-hill-camp",
] as const;

/**
 * An FFI's Status: the patient source's own, never derived from the Expiry
 * Date, so an FFI past it may still be `active`.
 */
// fallow-ignore-next-line unused-export
export const FFI_STATUSES = ["active", "inactive"] as const;

export type FfiType = (typeof FFI_TYPES)[number];
export type FfiOutcome = (typeof FFI_OUTCOMES)[number];
export type FfiHigherAuthority = (typeof FFI_HIGHER_AUTHORITIES)[number];
export type FfiSubmittingMc = (typeof FFI_SUBMITTING_MCS)[number];
export type FfiStatus = (typeof FFI_STATUSES)[number];

/** What the page calls each FFI Type. */
export const FFI_TYPE_LABELS: Readonly<Record<FfiType, string>> = {
  "cat-a-ocs-scs": "CAT A - OCS/SCS",
  dental: "Dental FFI",
  ippt: "IPPT FFI",
  "ord-release-from-service": "ORD / Release from Service",
  "overseas-exercises": "Overseas Exercises",
  vocational: "Vocational FFI",
};

/** What the page calls each Outcome. */
export const FFI_OUTCOME_LABELS: Readonly<Record<FfiOutcome, string>> = {
  fit: "FIT",
  "request-waiver": "REQUEST WAIVER",
  unfit: "UNFIT",
};

/** What the page calls each Higher Authority. */
export const FFI_HIGHER_AUTHORITY_LABELS: Readonly<Record<FfiHigherAuthority, string>> = {
  camo: "CAMO",
  carmo: "CARMO",
  cnmo: "CNMO",
  "comd-mmi": "Comd MMI",
  dis: "DIS",
};

/** What the page calls each Submitting MC. */
export const FFI_SUBMITTING_MC_LABELS: Readonly<Record<FfiSubmittingMc, string>> = {
  "hendon-camp": "Hendon Camp MC",
  "kranji-camp": "Kranji Camp MC",
  "mandai-hill-camp": "Mandai Hill Camp MC",
  "nee-soon-camp": "Nee Soon Camp MC",
};

/** What the page calls each Status. */
export const FFI_STATUS_LABELS: Readonly<Record<FfiStatus, string>> = {
  active: "Active",
  inactive: "Inactive",
};

/**
 * One FFI (Fit for Instruction) of a patient's FFI History, as the backend
 * answers it: its id, which the page keeps but never shows, each closed set's
 * code, and its Issue Date and Expiry Date, each `YYYY-MM-DD`.
 */
export interface Ffi {
  id: string;
  type: FfiType;
  outcome: FfiOutcome;
  issueDate: string;
  expiryDate: string;
  higherAuthority: FfiHigherAuthority;
  submittingMc: FfiSubmittingMc;
  status: FfiStatus;
}

const FFI_FIELDS = [
  "id",
  "type",
  "outcome",
  "issueDate",
  "expiryDate",
  "higherAuthority",
  "submittingMc",
  "status",
] as const satisfies readonly (keyof Ffi)[];

/** The page size the FFI page asks for, and its pager moves by. */
export const FFI_HISTORY_PAGE_SIZE = 5;

/** A calendar date, `YYYY-MM-DD`, as the backend sends an FFI's dates. */
function calendarDate(value: string, what: string): string {
  if (!isCalendarDate(value)) throw new DecodeError(`${what} is not a date`);
  return value;
}

/**
 * An FFI, read strictly: every field is required, each code must be one of
 * its set's, each date a calendar date, and a field the FFI does not have is
 * refused rather than dropped.
 */
export const decodeFfi = (value: unknown): Ffi => {
  const ffi = readObject(value, "Ffi");
  const decoded: Ffi = {
    id: ffi.string("id"),
    type: ffi.oneOf("type", FFI_TYPES),
    outcome: ffi.oneOf("outcome", FFI_OUTCOMES),
    issueDate: calendarDate(ffi.string("issueDate"), "Ffi.issueDate"),
    expiryDate: calendarDate(ffi.string("expiryDate"), "Ffi.expiryDate"),
    higherAuthority: ffi.oneOf("higherAuthority", FFI_HIGHER_AUTHORITIES),
    submittingMc: ffi.oneOf("submittingMc", FFI_SUBMITTING_MCS),
    status: ffi.oneOf("status", FFI_STATUSES),
  };
  ffi.onlyFields(FFI_FIELDS);
  return decoded;
};

/**
 * The FFI History of the patient `patientId`, a page at a time,
 * `GET /api/patient-ffi/{patientId}`: the patient source's FFIs, in its own
 * order. A `404` is an ordinary answer — the source holds no patient under the
 * id — so it reads as `absent`.
 */
export const FFI_HISTORY: ReadDeclaration<Page<Ffi>, [patientId: string, at: PageAt], true> = {
  absentOnNotFound: true,
  decode: jsonDecoder(pageDecoder("FfiHistoryPage", "ffis", decodeFfi)),
  path: (patientId, at) => pagedPath(`/api/patient-ffi/${encodeURIComponent(patientId)}`, at),
  permission: "patient:read",
};
