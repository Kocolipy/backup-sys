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

/** Tan Wei Ming, the R5 `Patient` the backend serves for `TAN_WEI_MING_READ`. */
export const TAN_WEI_MING = {
  birthDate: "1985-03-12",
  gender: "male",
  id: "eq081-VQEgP8drUUqCWzHfw3",
  identifier: [{ system: "https://fhir.synapxe.sg/NamingSystem/nric", value: "S1234567A" }],
  name: [{ family: "Tan", given: ["Wei", "Ming"], text: "Tan Wei Ming", use: "official" }],
  resourceType: "Patient",
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
