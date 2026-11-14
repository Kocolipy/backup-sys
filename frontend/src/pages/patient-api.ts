/**
 * The endpoint the patient in context is read from — the patient Home's
 * Patient card and the top bar show, and whose id the FFI, NUMC and ARMC pages read
 * their histories by — shared by those pages, so it sits beside them in `src/pages/`.
 *
 * Nothing here requests anything: the one reader of the patient in context
 * (`patient-in-context.ts`) sends the declaration through `useGatedRead` (behind `useSessionRequest`), so a `401` still ends
 * the session in one place. This file says what a response looks like and
 * declares the endpoint — its path, its decoder and the Permission it
 * requires — once.
 */

import type { ReadDeclaration } from "@/auth/declarations";
import { calendarDate, DecodeError, jsonDecoder, readObject } from "@/lib/decode";
import { PATIENT_GENDERS, type PatientGender } from "@/lib/patient-gender";

/** Synapxe's NRIC NamingSystem, the `system` of the identifier that holds a patient's NRIC. */
const NRIC_SYSTEM = "https://fhir.synapxe.sg/NamingSystem/nric";

/**
 * The part of an R5 `Patient` Home shows, read off the wire (its suite reads
 * `fhir5.Patient` fixtures through the decoder): the official
 * name's `text`, which is authoritative (its `family` and `given` parts are
 * optional and never read), the NRIC, the gender code and the birth date,
 * and the id the patient's FFI, NUMC and ARMC Histories are read by. No age: age is derived where it is shown, never sent.
 */
export interface PatientSummary {
  birthDate: string;
  /** Its FHIR id, which the patient's other reads (its FFI, NUMC and ARMC Histories) name it by. */
  id: string;
  gender: PatientGender;
  name: string;
  nric: string;
}

/** The value of the identifier under the NRIC system, among a patient's identifiers. */
const nricOf = (identifiers: unknown[]): string => {
  const nric = identifiers
    .map((identifier) => readObject(identifier, "Identifier"))
    .find((identifier) => identifier.string("system") === NRIC_SYSTEM);
  if (nric === undefined) throw new DecodeError("Patient.identifier has no NRIC");
  return nric.string("value");
};

/** The first name's `text`. */
const nameTextOf = (names: unknown[]): string => {
  if (names.length === 0) throw new DecodeError("Patient.name has no entry");
  return readObject(names[0], "HumanName").string("text");
};

const decodePatient = jsonDecoder((body: unknown): PatientSummary => {
  const patient = readObject(body, "Patient");
  if (patient.string("resourceType") !== "Patient") {
    throw new DecodeError("Patient.resourceType is not Patient");
  }
  // A FHIR `date` to the day, the precision the backend serves a birth date at.
  const birthDate = calendarDate(patient.string("birthDate"), "Patient.birthDate");
  return {
    birthDate,
    id: patient.string("id"),
    gender: patient.oneOf("gender", PATIENT_GENDERS),
    name: nameTextOf(patient.array("name", (name) => name)),
    nric: nricOf(patient.array("identifier", (identifier) => identifier)),
  };
});

/**
 * A patient by id, `GET /api/patient/{id}`, served as an R5 `Patient`. A
 * `404` is an ordinary answer — no patient there — so it reads as `absent`.
 */
export const PATIENT: ReadDeclaration<PatientSummary, [id: string], true> = {
  absentOnNotFound: true,
  decode: decodePatient,
  path: (id) => `/api/patient/${encodeURIComponent(id)}`,
  permission: "patient:read",
};
