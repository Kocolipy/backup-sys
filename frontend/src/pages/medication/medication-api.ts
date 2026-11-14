/**
 * The endpoint the Medication page reads — the patient in context's
 * Medication History — `/frontend/AGENTS.md`'s page-owned `api.ts`, named for
 * its page.
 *
 * Nothing here requests anything: the Medication page sends
 * {@link MEDICATION_HISTORY} through `usePagedRead` (behind
 * `useSessionRequest`), so a `401` still ends the session in one place. This
 * file says what a response looks like, names each code of the Dispense
 * Status — the Dispensary draws on the shared Medical Centre
 * (`@/pages/medical-centre-api`) — and declares the read — its path, decoder and
 * Permission — once.
 */

import type { ReadDeclaration } from "@/auth/declarations";
import { dateTime, jsonDecoder, readObject } from "@/lib/decode";
import { pageDecoder, pagedPath, type Page, type PageAt } from "@/lib/paging";
import { MEDICAL_CENTRES, type MedicalCentre } from "@/pages/medical-centre-api";

/**
 * A Medication Record's Dispense Status, as the API carries it, by its codes.
 * Exported for the SPA–API contract check (`/scripts/spa-contract/`), which
 * holds it to its enum in `/backend/docs/openapi.yaml` from outside this app's
 * import graph; nothing in `src/` but the decoder below reads it.
 */
// fallow-ignore-next-line unused-export
export const DISPENSE_STATUSES = ["pending", "dispensed", "uncollected"] as const;

export type DispenseStatus = (typeof DISPENSE_STATUSES)[number];

/** What the page calls each Dispense Status. */
export const DISPENSE_STATUS_LABELS: Readonly<Record<DispenseStatus, string>> = {
  dispensed: "DISPENSED",
  pending: "PENDING",
  uncollected: "UNCOLLECTED",
};

/**
 * One Medication Record of a patient's Medication History, as the backend
 * answers it: its id, which the page keeps but never shows, its free text as
 * the source gives it, each closed set's code, and its Start Date and End Date,
 * each ISO-8601 with an offset. An optional element the source lacks is
 * absent.
 */
export interface MedicationRecord {
  id: string;
  medication: string;
  dosage?: string;
  route?: string;
  frequency?: string;
  step?: string;
  quantity?: string;
  dispenseStatus: DispenseStatus;
  duration?: string;
  startDate: string;
  endDate: string;
  dispensary: MedicalCentre;
  prescribingDoctor: string;
}

const MEDICATION_RECORD_FIELDS = [
  "id",
  "medication",
  "dosage",
  "route",
  "frequency",
  "step",
  "quantity",
  "dispenseStatus",
  "duration",
  "startDate",
  "endDate",
  "dispensary",
  "prescribingDoctor",
] as const satisfies readonly (keyof MedicationRecord)[];

/** The six optional elements: free text the backend leaves out when the source has none. */
const OPTIONAL_TEXT = ["dosage", "route", "frequency", "step", "quantity", "duration"] as const;

/** The page size the Medication page asks for, and its pager moves by. */
export const MEDICATION_HISTORY_PAGE_SIZE = 5;

/**
 * A Medication Record, read strictly: six elements are required, and each of
 * the six optional ones, when there, a string — never `null`; each code must
 * be one of its set's, each date a date-time with an offset, and a field the
 * record does not have is refused rather than dropped.
 */
export const decodeMedicationRecord = (value: unknown): MedicationRecord => {
  const record = readObject(value, "MedicationRecord");
  const optional: Partial<Record<(typeof OPTIONAL_TEXT)[number], string>> = {};
  for (const field of OPTIONAL_TEXT) {
    const text = record.optionalString(field);
    if (text !== undefined) optional[field] = text;
  }
  const decoded: MedicationRecord = {
    id: record.string("id"),
    medication: record.string("medication"),
    ...optional,
    dispenseStatus: record.oneOf("dispenseStatus", DISPENSE_STATUSES),
    startDate: dateTime(record.string("startDate"), "MedicationRecord.startDate"),
    endDate: dateTime(record.string("endDate"), "MedicationRecord.endDate"),
    dispensary: record.oneOf("dispensary", MEDICAL_CENTRES),
    prescribingDoctor: record.string("prescribingDoctor"),
  };
  record.onlyFields(MEDICATION_RECORD_FIELDS);
  return decoded;
};

/**
 * The Medication History of the patient `patientId`, a page at a time,
 * `GET /api/patient-medication/{patientId}`: the patient source's Medication
 * Records, in its own order, with no sort and no filter. A `404` is an
 * ordinary answer — the source holds no patient under the id — so it reads as
 * `absent`.
 */
export const MEDICATION_HISTORY: ReadDeclaration<
  Page<MedicationRecord>,
  [patientId: string, at: PageAt],
  true
> = {
  absentOnNotFound: true,
  decode: jsonDecoder(pageDecoder("MedicationHistoryPage", "records", decodeMedicationRecord)),
  path: (patientId, at) =>
    pagedPath(`/api/patient-medication/${encodeURIComponent(patientId)}`, at),
  permission: "patient:read",
};
