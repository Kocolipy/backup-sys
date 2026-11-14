import { describe, expect, it } from "vitest";

import { DecodeError } from "@/lib/decode";
import { describeDecoder, STRING, without } from "@/lib/decode.testHelpers";

import { decodeMedicationRecord, MEDICATION_HISTORY } from "./medication-api";

/** One Medication Record exactly as the backend serialises it, every optional element present. */
const AMOXICILLIN = {
  id: "medication-tan-wei-ming-2",
  medication: "Amoxicillin",
  dosage: "300 mg",
  route: "PO",
  frequency: "TDS",
  step: "1",
  quantity: "20 tablets",
  dispenseStatus: "pending",
  duration: "5 days",
  startDate: "2026-10-18T17:31:00+08:00",
  endDate: "2026-10-22T17:31:00+08:00",
  dispensary: "khatib-camp",
  prescribingDoctor: "Dr Lim Wei Jie",
};

/** The six optional elements, which the backend leaves out when the source has none. */
const OPTIONAL = ["dosage", "route", "frequency", "step", "quantity", "duration"];

/** `decode(body)`'s refusal, read through the declaration as a `Response`. */
async function refused(body: unknown) {
  return Promise.resolve(MEDICATION_HISTORY.decode(new Response(JSON.stringify(body)))).then(
    () => null,
    (error: unknown) => error,
  );
}

// Six elements are required and six optional, and each is read as a string
// first. A code is matched exactly, so a label, another case or another set's
// code is outside its set; which strings are date-times with an offset is
// `isOffsetDateTime`'s (lib/time.test.ts).
describeDecoder(
  "MedicationRecord",
  decodeMedicationRecord,
  AMOXICILLIN,
  {
    id: STRING,
    medication: STRING,
    dosage: STRING,
    route: STRING,
    frequency: STRING,
    step: STRING,
    quantity: STRING,
    dispenseStatus: ["DISPENSED", "pending | dispensed | uncollected", "a string"],
    duration: STRING,
    startDate: ["2026-10-18", "a date-time", "a string"],
    endDate: ["2026-10-22T17:31:00", "a date-time", "a string"],
    dispensary: [
      "Khatib Camp MC",
      "nee-soon-camp | kranji-camp | hendon-camp | mandai-hill-camp | khatib-camp",
      "a string",
    ],
    prescribingDoctor: STRING,
  },
  { optional: OPTIONAL, strict: true },
);

describe("MEDICATION_HISTORY", () => {
  it("reads a patient's Medication History a page at a time, by the patient's id", () => {
    expect(MEDICATION_HISTORY.path("eq081-VQEgP8drUUqCWzHfw3", { count: 5, startIndex: 6 })).toBe(
      "/api/patient-medication/eq081-VQEgP8drUUqCWzHfw3?startIndex=6&count=5",
    );
  });

  it("sends the patient's id encoded as a path segment", () => {
    expect(MEDICATION_HISTORY.path("a/b c", { count: 5, startIndex: 1 })).toBe(
      "/api/patient-medication/a%2Fb%20c?startIndex=1&count=5",
    );
  });

  it("reads with patient:read, a 404 being no patient rather than a failure", () => {
    expect([MEDICATION_HISTORY.permission, MEDICATION_HISTORY.absentOnNotFound]).toEqual([
      "patient:read",
      true,
    ]);
  });

  it("decodes a page of Medication Records, one with none of its optional elements", async () => {
    const bare = OPTIONAL.reduce<object>((record, field) => without(record, field), {
      ...AMOXICILLIN,
      id: "medication-bare",
    });

    await expect(
      MEDICATION_HISTORY.decode(
        new Response(
          JSON.stringify({ records: [AMOXICILLIN, bare], startIndex: 1, totalResults: 2 }),
        ),
      ),
    ).resolves.toStrictEqual({ rows: [AMOXICILLIN, bare], startIndex: 1, totalResults: 2 });
  });

  // An absent optional element is one the source lacks; a null one is no text at all.
  it("refuses a Medication Record whose optional element is null", async () => {
    expect(
      await refused({ records: [{ ...AMOXICILLIN, step: null }], startIndex: 1, totalResults: 1 }),
    ).toStrictEqual(new DecodeError("MedicationRecord.step is not a string"));
  });

  it("refuses a page holding a Medication Record it cannot read", async () => {
    expect(
      await refused({
        records: [{ ...AMOXICILLIN, dispensary: "sembawang-camp" }],
        startIndex: 1,
        totalResults: 1,
      }),
    ).toStrictEqual(
      new DecodeError(
        "MedicationRecord.dispensary is not nee-soon-camp | kranji-camp | hendon-camp | mandai-hill-camp | khatib-camp",
      ),
    );
  });

  it("refuses a page holding a field it does not know", async () => {
    expect(
      await refused({ records: [], startIndex: 1, totalResults: 0, nextPage: null }),
    ).toStrictEqual(new DecodeError("MedicationHistoryPage.nextPage is not expected"));
  });
});
