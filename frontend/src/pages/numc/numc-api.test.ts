import { describe, expect, it } from "vitest";

import { DecodeError } from "@/lib/decode";
import { describeDecoder, STRING } from "@/lib/decode.testHelpers";

import { decodeNumcRecord, NUMC_HISTORY } from "./numc-api";

/** One NUMC Record exactly as the backend serialises it. */
const SUBMARINER = {
  id: "numc-tan-wei-ming-2",
  type: "submariner",
  outcome: "waiver-rejected",
  issueDate: "2024-01-10",
  expiryDate: "2025-01-09",
};

/** `decode(body)`'s refusal, read through the declaration as a `Response`. */
async function refused(body: unknown) {
  return Promise.resolve(NUMC_HISTORY.decode(new Response(JSON.stringify(body)))).then(
    () => null,
    (error: unknown) => error,
  );
}

// Every element is required, and each is read as a string first. A code is
// matched exactly, so a label, another case, another spelling or another
// set's code is outside its set; which strings are calendar dates is
// `isCalendarDate`'s (lib/time.test.ts).
describeDecoder(
  "NumcRecord",
  decodeNumcRecord,
  SUBMARINER,
  {
    id: STRING,
    type: ["Submariner Duties", "hyperbaric-chamber | submariner | diving", "a string"],
    outcome: ["request-waiver", "fit | unfit | waiver-granted | waiver-rejected", "a string"],
    issueDate: ["10 Jan 2024", "a date", "a string"],
    expiryDate: ["2025-02-30", "a date", "a string"],
  },
  { strict: true },
);

describe("NUMC_HISTORY", () => {
  it("reads a patient's NUMC History a page at a time, by the patient's id", () => {
    expect(NUMC_HISTORY.path("eq081-VQEgP8drUUqCWzHfw3", { count: 5, startIndex: 6 })).toBe(
      "/api/patient-numc/eq081-VQEgP8drUUqCWzHfw3?startIndex=6&count=5",
    );
  });

  it("sends the patient's id encoded as a path segment", () => {
    expect(NUMC_HISTORY.path("a/b c", { count: 5, startIndex: 1 })).toBe(
      "/api/patient-numc/a%2Fb%20c?startIndex=1&count=5",
    );
  });

  it("reads with patient:read, a 404 being no patient rather than a failure", () => {
    expect([NUMC_HISTORY.permission, NUMC_HISTORY.absentOnNotFound]).toEqual([
      "patient:read",
      true,
    ]);
  });

  it("decodes a page of NUMC Records", async () => {
    await expect(
      NUMC_HISTORY.decode(
        new Response(JSON.stringify({ records: [SUBMARINER], startIndex: 1, totalResults: 4 })),
      ),
    ).resolves.toStrictEqual({ rows: [SUBMARINER], startIndex: 1, totalResults: 4 });
  });

  it("refuses a page holding a NUMC Record it cannot read", async () => {
    expect(
      await refused({
        records: [{ ...SUBMARINER, outcome: "expired" }],
        startIndex: 1,
        totalResults: 1,
      }),
    ).toStrictEqual(
      new DecodeError("NumcRecord.outcome is not fit | unfit | waiver-granted | waiver-rejected"),
    );
  });

  // A NUMC Record has no Status: one sent is a field the record does not have.
  it("refuses a NUMC Record carrying a Status", async () => {
    expect(
      await refused({
        records: [{ ...SUBMARINER, status: "active" }],
        startIndex: 1,
        totalResults: 1,
      }),
    ).toStrictEqual(new DecodeError("NumcRecord.status is not expected"));
  });

  it("refuses a page holding a field it does not know", async () => {
    expect(
      await refused({ records: [], startIndex: 1, totalResults: 0, nextPage: null }),
    ).toStrictEqual(new DecodeError("NumcHistoryPage.nextPage is not expected"));
  });
});
