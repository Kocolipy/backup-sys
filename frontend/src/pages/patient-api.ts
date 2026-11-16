/**
 * The endpoint the patient in context is read through — the patient Home and
 * the Demographic page show — an `api.ts` the pages share, so it sits at
 * `src/pages/` itself rather than in either page's folder.
 *
 * Nothing here requests anything: the one reader of the patient in context
 * (`patient-in-context.ts`) sends the declaration through `useGatedRead` (behind `useSessionRequest`), so a `401` still ends
 * the session in one place. This file says what a response looks like and
 * declares the endpoint — its path, its decoder and the Permission it
 * requires — once.
 */

import type { ReadDeclaration } from "@/auth/declarations";
import { decodeArray, DecodeError, jsonDecoder, readObject } from "@/lib/decode";
import { isCalendarDate } from "@/lib/time";
import { PATIENT_GENDERS, type PatientGender } from "@/lib/patient-gender";

/** Synapxe's NRIC NamingSystem, the `system` of the identifier that holds a patient's NRIC. */
const NRIC_SYSTEM = "https://fhir.synapxe.sg/NamingSystem/nric";

/** The extension a patient's Military history is served under (`/docs/domain/patient.md`). */
export const MILITARY_HISTORY_EXTENSION =
  "http://example.org/fhir/StructureDefinition/military-service";

/** HL7 v2 table 0131, whose code `N` marks a contact as the patient's Next of kin. */
const CONTACT_ROLE_SYSTEM = "http://terminology.hl7.org/CodeSystem/v2-0131";

/**
 * The part of an R5 `Patient` the pages show, read off the wire (its suite reads
 * `fhir5.Patient` fixtures through the decoder): the official
 * name's `text`, which is authoritative (its `family` and `given` parts are
 * optional and never read), the NRIC, the gender code and the birth date;
 * and, where the patient has them, the Demographic details: the phone and
 * email, the `home` address's text, the Next of kin (the first contact coded
 * v2-0131 `N`) and the Military history. No age: age is derived where it is
 * shown, never sent.
 */
export interface PatientSummary {
  birthDate: string;
  email?: string;
  gender: PatientGender;
  militaryHistory?: MilitaryHistory;
  name: string;
  nextOfKin?: NextOfKin;
  phone?: string;
  nric: string;
  residentialAddress?: string;
}

/** One service record of a patient's Military history; any field may be unknown. */
export interface ServiceRecord {
  rank?: string;
  service?: string;
  serviceType?: string;
  unit?: string;
  vocation?: string;
}

const SERVICE_RECORD_FIELDS = ["rank", "service", "serviceType", "unit", "vocation"] as const;

/** A patient's Military history: their service records in order, and its dates, each `YYYY-MM-DD`. */
export interface MilitaryHistory {
  enlistmentDate?: string;
  mrDate?: string;
  ordDate?: string;
  rodDate?: string;
  serviceRecords: ServiceRecord[];
}

const SERVICE_DATES = ["enlistmentDate", "mrDate", "ordDate", "rodDate"] as const;

/** The contact the patient names as their Next of kin. */
export interface NextOfKin {
  name: string;
  phone?: string;
  relationship?: string;
}

/**
 * `value`'s field `key` as it came off the wire, `undefined` when it is not
 * there: FHIR leaves an element out rather than sending it empty. `value`
 * itself must be an object, `what` naming it in the refusal.
 */
const fieldOf = (value: unknown, key: string, what: string): unknown => {
  readObject(value, what);
  return Reflect.get(Object(value), key);
};

/** The array under `key`, each element still raw; none when FHIR left the element out. */
const optionalArray = (value: unknown, key: string, what: string): unknown[] => {
  const field = fieldOf(value, key, what);
  return field === undefined ? [] : decodeArray(field, (item) => item, `${what}.${key}`);
};

/** The string under `key`, or `undefined` when FHIR left it out. */
const optionalString = (value: unknown, key: string, what: string): string | undefined =>
  fieldOf(value, key, what) === undefined ? undefined : readObject(value, what).string(key);

/** The value of the first of `contactPoints` under `system`. */
const contactPointOf = (contactPoints: unknown[], system: "email" | "phone") =>
  contactPoints
    .map((contactPoint) => readObject(contactPoint, "ContactPoint"))
    .find((contactPoint) => contactPoint.string("system") === system)
    ?.string("value");

/** The text of the first `home` address. */
const residentialAddressOf = (addresses: unknown[]): string | undefined =>
  addresses
    .map((address) => readObject(address, "Address"))
    .find((address) => address.string("use") === "home")
    ?.string("text");

/** Whether `concept` is coded as v2-0131's next of kin, `N`. */
const marksNextOfKin = (concept: unknown): boolean =>
  optionalArray(concept, "coding", "CodeableConcept").some((coding) => {
    const read = readObject(coding, "Coding");
    return read.string("system") === CONTACT_ROLE_SYSTEM && read.string("code") === "N";
  });

/**
 * The first contact marked as Next of kin: its name's text, the text of the
 * first relationship that has one, and its phone.
 */
const nextOfKinOf = (contacts: unknown[]): NextOfKin | undefined => {
  const kin = contacts
    .map((contact) => ({
      contact,
      relationships: optionalArray(contact, "relationship", "Patient.contact"),
    }))
    .find(({ relationships }) => relationships.some(marksNextOfKin));
  if (kin === undefined) return undefined;
  const { contact, relationships } = kin;
  const relationship = relationships
    .map((concept) => optionalString(concept, "text", "CodeableConcept"))
    .find((text) => text !== undefined);
  const phone = contactPointOf(optionalArray(contact, "telecom", "Patient.contact"), "phone");
  return {
    name: readObject(contact, "Patient.contact").object("name").string("text"),
    ...(phone === undefined ? {} : { phone }),
    ...(relationship === undefined ? {} : { relationship }),
  };
};

/** `extension`'s own sub-extensions, each with its url. */
const subExtensionsOf = (extension: unknown, what: string) =>
  optionalArray(extension, "extension", what).map((sub) => ({
    sub,
    url: readObject(sub, "Extension").string("url"),
  }));

const decodeServiceRecord = (extension: unknown): ServiceRecord => {
  const record: ServiceRecord = {};
  for (const { sub, url } of subExtensionsOf(extension, "serviceRecord")) {
    const field = SERVICE_RECORD_FIELDS.find((candidate) => candidate === url);
    if (field !== undefined) {
      record[field] = readObject(sub, `serviceRecord.${url}`).string("valueString");
    }
  }
  return record;
};

/**
 * The Military history extension, if the patient has one: its service records
 * in order, and its dates, each to the day. A sub-extension it does not know is
 * left unread.
 */
const militaryHistoryOf = (extensions: unknown[]): MilitaryHistory | undefined => {
  const extension = extensions.find(
    (candidate) => readObject(candidate, "Extension").string("url") === MILITARY_HISTORY_EXTENSION,
  );
  if (extension === undefined) return undefined;
  const history: MilitaryHistory = { serviceRecords: [] };
  for (const { sub, url } of subExtensionsOf(extension, "military-service")) {
    if (url === "serviceRecord") {
      history.serviceRecords.push(decodeServiceRecord(sub));
      continue;
    }
    const date = SERVICE_DATES.find((candidate) => candidate === url);
    if (date === undefined) continue;
    const value = readObject(sub, `military-service.${url}`).string("valueDate");
    if (!isCalendarDate(value)) throw new DecodeError(`military-service.${url} is not a date`);
    history[date] = value;
  }
  return history;
};

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
  const birthDate = patient.string("birthDate");
  // A FHIR `date` to the day, the precision the backend serves a birth date at.
  if (!isCalendarDate(birthDate)) throw new DecodeError("Patient.birthDate is not a date");
  const telecom = optionalArray(body, "telecom", "Patient");
  const email = contactPointOf(telecom, "email");
  const phone = contactPointOf(telecom, "phone");
  const residentialAddress = residentialAddressOf(optionalArray(body, "address", "Patient"));
  const nextOfKin = nextOfKinOf(optionalArray(body, "contact", "Patient"));
  const militaryHistory = militaryHistoryOf(optionalArray(body, "extension", "Patient"));
  // A detail the patient has none of is left off, as the wire leaves it out.
  return {
    birthDate,
    gender: patient.oneOf("gender", PATIENT_GENDERS),
    name: nameTextOf(patient.array("name", (name) => name)),
    nric: nricOf(patient.array("identifier", (identifier) => identifier)),
    ...(email === undefined ? {} : { email }),
    ...(militaryHistory === undefined ? {} : { militaryHistory }),
    ...(nextOfKin === undefined ? {} : { nextOfKin }),
    ...(phone === undefined ? {} : { phone }),
    ...(residentialAddress === undefined ? {} : { residentialAddress }),
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
