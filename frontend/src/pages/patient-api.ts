/**
 * The endpoint the patient in context is read from — the patient the Patient
 * card, the top bar and the Demographic page show, and whose id the Medication,
 * FFI, NUMC and ARMC pages read their histories by — shared by those pages, so
 * it sits beside them in `src/pages/`.
 *
 * Nothing here requests anything: the one reader of the patient in context
 * (`patient-in-context.ts`) sends the declaration through `useGatedRead` (behind `useSessionRequest`), so a `401` still ends
 * the session in one place. This file says what a response looks like and
 * declares the endpoint — its path, its decoder and the Permission it
 * requires — once.
 */

import type { ReadDeclaration } from "@/auth/declarations";
import { decodeArray, DecodeError, jsonDecoder, readObject, receivedValue } from "@/lib/decode";
import type { SourceField } from "@/lib/source-field";
import { isCalendarDate } from "@/lib/time";

/** Synapxe's NRIC NamingSystem, the `system` of the identifier that holds a patient's NRIC. */
const NRIC_SYSTEM = "https://fhir.synapxe.sg/NamingSystem/nric";

/** The extension a patient's Military history is served under (`/docs/domain/patient.md`). */
export const MILITARY_HISTORY_EXTENSION =
  "http://example.org/fhir/StructureDefinition/military-service";

/**
 * The extension naming one element of the Patient the patient source sent in
 * the wrong format, by its path in the resource (`name[0].text`): the
 * backend's own (`/docs/domain/patient.md`, "The resource").
 */
export const INVALID_FIELD_EXTENSION = "http://example.org/fhir/StructureDefinition/invalid-field";

/** HL7 v2 table 0131, whose code `N` marks a contact as the patient's Next of kin. */
const CONTACT_ROLE_SYSTEM = "http://terminology.hl7.org/CodeSystem/v2-0131";

/**
 * The part of an R5 `Patient` the pages show, read off the wire (its suite reads
 * `fhir5.Patient` fixtures through the decoder): the official name's `text`,
 * which is authoritative (its `family` and `given` parts are optional and never
 * read), the NRIC, the gender code, the birth date and the Demographic details —
 * the phone and email, the `home` address's text, the Next of kin (the first
 * contact coded v2-0131 `N`) and the Military history — each shown as received
 * (ADR 0020): present, missing or in the wrong format, independently of the
 * others, a gender outside the four codes present as the code it came as. A
 * patient with no Next of kin, or no Military history, has none.
 * No age: age is derived where it is shown, never sent. No id: the patient's
 * other reads go by the id the patient was asked for (`patient-in-context.ts`),
 * never one the Patient carries.
 */
export interface PatientSummary {
  birthDate: SourceField<string>;
  email: SourceField<string>;
  gender: SourceField<string>;
  militaryHistory?: MilitaryHistory;
  name: SourceField<string>;
  nextOfKin?: NextOfKin;
  phone: SourceField<string>;
  nric: SourceField<string>;
  residentialAddress: SourceField<string>;
}

/** One service record of a patient's Military history, each field as received. */
export interface ServiceRecord {
  rank: SourceField<string>;
  service: SourceField<string>;
  serviceType: SourceField<string>;
  unit: SourceField<string>;
  vocation: SourceField<string>;
}

/** A field of a service record, by its key, which is also the url it is served under. */
type ServiceRecordField = keyof ServiceRecord;

/**
 * A patient's Military history: their service records in order, and its dates,
 * each as received, a present one `YYYY-MM-DD`.
 */
export interface MilitaryHistory {
  enlistmentDate: SourceField<string>;
  mrDate: SourceField<string>;
  ordDate: SourceField<string>;
  rodDate: SourceField<string>;
  serviceRecords: ServiceRecord[];
}

/** A service date, by its key, which is also the url it is served under. */
type ServiceDate = Exclude<keyof MilitaryHistory, "serviceRecords">;

/** The contact the patient names as their Next of kin, each detail as received. */
export interface NextOfKin {
  name: SourceField<string>;
  phone: SourceField<string>;
  relationship: SourceField<string>;
}

/** A field the patient source sent nothing for. */
const MISSING: SourceField<never> = { state: "missing" };

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

/**
 * The paths the patient's invalid-field extensions name, read beneath one
 * element of the resource (the whole of it at first): `named` says whether
 * the element at `suffix` beneath it is one of them, and `under` reads them
 * beneath the element at `suffix` (`contact[0]`, then `.telecom`).
 */
interface InvalidPaths {
  named: (suffix: string) => boolean;
  under: (suffix: string) => InvalidPaths;
}

const invalidPaths = (paths: readonly string[], prefix = ""): InvalidPaths => ({
  named: (suffix) => paths.includes(`${prefix}${suffix}`),
  under: (suffix) => invalidPaths(paths, `${prefix}${suffix}`),
});

/**
 * The field `key` of the first of `elements` that `wanted` picks, as
 * received, `invalid` naming it at `[place].key` beneath the array: missing
 * when it picks none. `what` names the element in a refusal.
 */
const firstFieldOf = (
  elements: unknown[],
  wanted: (element: unknown) => boolean,
  key: string,
  what: string,
  invalid: InvalidPaths,
): SourceField<string> => {
  const at = elements.findIndex(wanted);
  if (at < 0) return MISSING;
  return receivedValue(fieldOf(elements[at], key, what), invalid.named(`[${String(at)}].${key}`));
};

/**
 * The value of the first of `contactPoints` under `system`, as received,
 * `invalid` read beneath their array (`telecom`): missing when there is none.
 */
const contactPointOf = (
  contactPoints: unknown[],
  system: "email" | "phone",
  invalid: InvalidPaths,
): SourceField<string> =>
  firstFieldOf(
    contactPoints,
    (contactPoint) => readObject(contactPoint, "ContactPoint").string("system") === system,
    "value",
    "ContactPoint",
    invalid,
  );

/** The text of the first `home` address, as received: missing when there is none. */
const residentialAddressOf = (addresses: unknown[], invalid: InvalidPaths): SourceField<string> =>
  firstFieldOf(
    addresses,
    (address) => readObject(address, "Address").string("use") === "home",
    "text",
    "Address",
    invalid.under("address"),
  );

/** Whether `concept` is coded as v2-0131's next of kin, `N`. */
const marksNextOfKin = (concept: unknown): boolean =>
  optionalArray(concept, "coding", "CodeableConcept").some((coding) => {
    const read = readObject(coding, "Coding");
    return read.string("system") === CONTACT_ROLE_SYSTEM && read.string("code") === "N";
  });

/**
 * The place the backend serves a Next of kin's relationship text at among
 * their relationships: after the coding that marks them, so an invalid one is
 * named `contact[0].relationship[1].text`.
 */
const RELATIONSHIP_TEXT_AT = 1;

/**
 * The first contact marked as Next of kin: its name's text, the text of the
 * first relationship that has one, and its phone, each as received.
 */
const nextOfKinOf = (contacts: unknown[], invalid: InvalidPaths): NextOfKin | undefined => {
  const at = contacts.findIndex((contact) =>
    optionalArray(contact, "relationship", "Patient.contact").some(marksNextOfKin),
  );
  if (at < 0) return undefined;
  const contact = contacts[at];
  const kin = invalid.under(`contact[${String(at)}]`);
  const name = fieldOf(contact, "name", "Patient.contact");
  const relationship = optionalArray(contact, "relationship", "Patient.contact")
    .map((concept) => fieldOf(concept, "text", "CodeableConcept"))
    .find((text) => text !== undefined);
  return {
    name: receivedValue(
      name === undefined ? undefined : fieldOf(name, "text", "Patient.contact.name"),
      kin.named(".name.text"),
    ),
    phone: contactPointOf(
      optionalArray(contact, "telecom", "Patient.contact"),
      "phone",
      kin.under(".telecom"),
    ),
    relationship: receivedValue(
      relationship,
      kin.named(`.relationship[${String(RELATIONSHIP_TEXT_AT)}].text`),
    ),
  };
};

/** `extension`'s own sub-extensions, each with its url. */
const subExtensionsOf = (extension: unknown, what: string) =>
  optionalArray(extension, "extension", what).map((sub) => ({
    sub,
    url: readObject(sub, "Extension").string("url"),
  }));

type SubExtensions = ReturnType<typeof subExtensionsOf>;

/** The value under `key` of the first of `subs` under `url`, as it came: `undefined` when none is. */
const subValueOf = (subs: SubExtensions, url: string, key: string): unknown => {
  const found = subs.find((candidate) => candidate.url === url);
  return found === undefined ? undefined : fieldOf(found.sub, key, `Extension.${url}`);
};

/**
 * A service record, `invalid` read beneath its sub-extension, each field the
 * first of its sub-extensions under the field's url, as received; an invalid
 * one is named by the url it would be served under, as one left out has no
 * place of its own.
 */
const decodeServiceRecord = (extension: unknown, invalid: InvalidPaths): ServiceRecord => {
  const subs = subExtensionsOf(extension, "serviceRecord");
  const field = (url: ServiceRecordField) =>
    receivedValue(
      subValueOf(subs, url, "valueString"),
      invalid.named(`.extension('${url}').valueString`),
    );
  return {
    rank: field("rank"),
    service: field("service"),
    serviceType: field("serviceType"),
    unit: field("unit"),
    vocation: field("vocation"),
  };
};

/** A patient's extensions, each beside its url. */
const extensionsOf = (body: unknown) =>
  optionalArray(body, "extension", "Patient").map((extension) => ({
    extension,
    url: readObject(extension, "Extension").string("url"),
  }));

type Extensions = ReturnType<typeof extensionsOf>;

/**
 * The Military history extension, if the patient has one: its service records
 * in order, and its dates, each the first sub-extension under its url and
 * each as received, a date present only to the day. A sub-extension it does
 * not know is left unread.
 */
const militaryHistoryOf = (
  extensions: Extensions,
  invalid: InvalidPaths,
): MilitaryHistory | undefined => {
  const at = extensions.findIndex(({ url }) => url === MILITARY_HISTORY_EXTENSION);
  if (at < 0) return undefined;
  const military = invalid.under(`extension[${String(at)}]`);
  const subs = subExtensionsOf(extensions[at]?.extension, "military-service");
  const date = (url: ServiceDate) =>
    receivedValue(
      subValueOf(subs, url, "valueDate"),
      military.named(`.extension('${url}').valueDate`),
      isCalendarDate,
    );
  return {
    enlistmentDate: date("enlistmentDate"),
    mrDate: date("mrDate"),
    ordDate: date("ordDate"),
    rodDate: date("rodDate"),
    serviceRecords: subs.flatMap(({ sub, url }, place) =>
      url === "serviceRecord"
        ? [decodeServiceRecord(sub, military.under(`.extension[${String(place)}]`))]
        : [],
    ),
  };
};

/**
 * The paths of the elements an invalid-field extension names, among a
 * patient's extensions.
 */
const invalidElementsOf = (extensions: Extensions): string[] =>
  extensions
    .filter(({ url }) => url === INVALID_FIELD_EXTENSION)
    .map(({ extension }) => readObject(extension, "invalid-field").string("valueString"));

/**
 * The path an invalid-field extension names the official name's text by: the
 * first name's, as the backend serves the official name alone.
 */
const NAME_TEXT_ELEMENT = "name[0].text";

/**
 * The value of the identifier under the NRIC system, among a patient's
 * identifiers, as received, `invalid` naming it by its path — the first
 * identifier's, as the backend serves the NRIC alone: missing when none is
 * under the system.
 */
const nricOf = (identifiers: unknown[], invalid: InvalidPaths): SourceField<string> =>
  firstFieldOf(
    identifiers,
    (identifier) => readObject(identifier, "Identifier").string("system") === NRIC_SYSTEM,
    "value",
    "Identifier",
    invalid.under("identifier"),
  );

/** The first name's `text`, as received: missing when there is no name. */
const nameTextOf = (names: unknown[], invalid: InvalidPaths): SourceField<string> => {
  if (names.length === 0) return MISSING;
  return receivedValue(fieldOf(names[0], "text", "HumanName"), invalid.named(NAME_TEXT_ELEMENT));
};

const decodePatient = jsonDecoder((body: unknown): PatientSummary => {
  const patient = readObject(body, "Patient");
  if (patient.string("resourceType") !== "Patient") {
    throw new DecodeError("Patient.resourceType is not Patient");
  }
  /** The element `key` as it came, `undefined` when the backend left it out. */
  const element = (key: string): unknown => fieldOf(body, key, "Patient");
  const extensions = extensionsOf(body);
  const invalid = invalidPaths(invalidElementsOf(extensions));
  const telecom = optionalArray(body, "telecom", "Patient");
  const nextOfKin = nextOfKinOf(optionalArray(body, "contact", "Patient"), invalid);
  const militaryHistory = militaryHistoryOf(extensions, invalid);
  // A Next of kin or a Military history the patient has none of is left off, as the wire leaves it out.
  return {
    // A FHIR `date` to the day, the precision the backend serves a birth date at.
    birthDate: receivedValue(element("birthDate"), invalid.named("birthDate"), isCalendarDate),
    email: contactPointOf(telecom, "email", invalid.under("telecom")),
    gender: receivedValue(element("gender"), invalid.named("gender")),
    name: nameTextOf(optionalArray(body, "name", "Patient"), invalid),
    nric: nricOf(optionalArray(body, "identifier", "Patient"), invalid),
    phone: contactPointOf(telecom, "phone", invalid.under("telecom")),
    residentialAddress: residentialAddressOf(optionalArray(body, "address", "Patient"), invalid),
    ...(militaryHistory === undefined ? {} : { militaryHistory }),
    ...(nextOfKin === undefined ? {} : { nextOfKin }),
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
