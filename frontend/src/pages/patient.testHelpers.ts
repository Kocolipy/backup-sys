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
