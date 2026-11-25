import { screen, within } from "@testing-library/react";
import type { Patient } from "fhir/r5";

import { setEhrContext } from "@/auth/ehr-context";

/**
 * The read of Tan Wei Ming, one of the seeder's development fixtures, as an
 * answer-table key. Written out rather than built from `PATIENT.path`, so a
 * suite asserting the request sent checks the path against the contract and
 * not against the code under test.
 */
export const TAN_WEI_MING_READ = "GET /api/patient/eq081-VQEgP8drUUqCWzHfw3";

/** Tan Wei Ming with none of the Demographic details: the five elements every Patient has. */
export const TAN_WEI_MING_BARE = {
  birthDate: "1985-03-12",
  gender: "male",
  id: "eq081-VQEgP8drUUqCWzHfw3",
  identifier: [{ system: "https://fhir.synapxe.sg/NamingSystem/nric", value: "S1234567A" }],
  name: [{ family: "Tan", given: ["Wei", "Ming"], text: "Tan Wei Ming", use: "official" }],
  resourceType: "Patient",
} satisfies Patient;

/**
 * Tan Wei Ming, the R5 `Patient` the backend serves for `TAN_WEI_MING_READ`,
 * his Demographic details as the patient source's mock holds them.
 */
export const TAN_WEI_MING = {
  ...TAN_WEI_MING_BARE,
  address: [{ text: "Blk 123 Bishan Street 12 #10-456, Singapore 570123", use: "home" }],
  contact: [
    {
      name: { text: "Mdm Lee Siew Kuen" },
      relationship: [
        { coding: [{ code: "N", system: "http://terminology.hl7.org/CodeSystem/v2-0131" }] },
        { text: "Mother" },
      ],
      telecom: [{ system: "phone", value: "+65 8234 5678" }],
    },
  ],
  extension: [
    {
      extension: [
        {
          extension: [
            { url: "serviceType", valueString: "NSman" },
            { url: "rank", valueString: "CPT" },
            { url: "unit", valueString: "3 SIR" },
            { url: "service", valueString: "Army" },
            { url: "vocation", valueString: "Infantry Officer" },
            { url: "vocationType", valueString: "Combat" },
            { url: "primaryMc", valueString: "Selarang Medical Centre" },
            { url: "dentalCentre", valueString: "Selarang Dental Centre" },
          ],
          url: "serviceRecord",
        },
        {
          extension: [
            { url: "serviceType", valueString: "DXO" },
            { url: "rank", valueString: "ME2" },
            { url: "unit", valueString: "RSS Supreme" },
            { url: "service", valueString: "Navy" },
            { url: "vocation", valueString: "Naval Safety Officer" },
          ],
          url: "serviceRecord",
        },
        { url: "enlistmentDate", valueDate: "2018-01-15" },
        { url: "ordDate", valueDate: "2020-01-14" },
        { url: "rodDate", valueDate: "2037-09-08" },
      ],
      url: "http://example.org/fhir/StructureDefinition/military-service",
    },
  ],
  telecom: [
    { system: "phone", value: "+65 9123 4567" },
    { system: "email", value: "tan.hw@email.com" },
  ],
} satisfies Patient;

/** The tab holds Patient `patientId` in its EHR context. */
export const withPatientInContext = (patientId: string) => setEhrContext({ patient: patientId });

/**
 * The patient reads among `sent`, in order, as answer-table keys to compare
 * with `TAN_WEI_MING_READ`.
 */
export const patientReads = (sent: readonly { method: string; path: string }[]) =>
  sent
    .map(({ method, path }) => `${method} ${path}`)
    .filter((key) => key.startsWith("GET /api/patient/"));

/** The page beneath the top bar, which names the patient in context too. */
export const page = () => within(screen.getByRole("main"));

/** The extension naming one element of a Patient received in the wrong format, by its path. */
const invalidField = (path: string) => ({
  url: "http://example.org/fhir/StructureDefinition/invalid-field",
  valueString: path,
});

/**
 * Tan Wei Ming with the Demographic details of the patient source's mock's
 * `received-demographics`, as the backend serves them: each missing, in the
 * wrong format or present, apart from the others — no residential address, a
 * phone in the wrong format beside a present email, a Next of kin with no name
 * and a relationship in the wrong format, a service record whose rank is in the
 * wrong format and which has no unit, an Enlistment Date and a ROD Date in the
 * wrong format, an ORD Date present and no MR Date.
 */
export const TAN_WEI_MING_MALFORMED = {
  ...TAN_WEI_MING_BARE,
  contact: [
    {
      relationship: [
        { coding: [{ code: "N", system: "http://terminology.hl7.org/CodeSystem/v2-0131" }] },
      ],
      telecom: [{ system: "phone", value: "+65 8111 2222" }],
    },
  ],
  extension: [
    {
      extension: [
        {
          extension: [
            { url: "serviceType", valueString: "NSF" },
            { url: "service", valueString: "Army" },
            { url: "vocation", valueString: "Signaller" },
          ],
          url: "serviceRecord",
        },
        { url: "ordDate", valueDate: "2001-03-01" },
      ],
      url: "http://example.org/fhir/StructureDefinition/military-service",
    },
    invalidField("telecom[0].value"),
    invalidField("contact[0].relationship[1].text"),
    invalidField("extension[0].extension[0].extension('rank').valueString"),
    invalidField("extension[0].extension('enlistmentDate').valueDate"),
    invalidField("extension[0].extension('rodDate').valueDate"),
  ],
  telecom: [{ system: "phone" }, { system: "email", value: "lim.kl@email.com" }],
} satisfies Patient;
