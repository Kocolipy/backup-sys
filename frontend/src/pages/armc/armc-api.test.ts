import { describe, expect, it } from "vitest";

import { DecodeError } from "@/lib/decode";
import { describeDecoder, STRING, without } from "@/lib/decode.testHelpers";

import { ARMC_HISTORY, decodeArmcBoard } from "./armc-api";

/** One ARMC Board exactly as the backend serialises it, its End Date present. */
const ACES_FIT = {
  id: "armc-tan-wei-ming-1",
  boardType: "aces",
  outcome: "fit",
  startDate: "2024-01-01",
  endDate: "2024-02-10",
  medicalOfficer1: "Medical Officer 1",
  medicalOfficer2: "Medical Officer 2",
};

/** `decode(body)`'s refusal, read through the declaration as a `Response`. */
async function refused(body: unknown) {
  return Promise.resolve(ARMC_HISTORY.decode(new Response(JSON.stringify(body)))).then(
    () => null,
    (error: unknown) => error,
  );
}

// Every element is required but the End Date, which the backend leaves out
// for a board still open, and each is read as a string first. A code is
// matched exactly, so a label, another case or another set's code is outside
// its set; which strings are calendar dates is `isCalendarDate`'s
// (lib/time.test.ts).
describeDecoder(
  "ArmcBoard",
  decodeArmcBoard,
  ACES_FIT,
  {
    id: STRING,
    boardType: ["ACES", "aces", "a string"],
    outcome: ["waiver-granted", "fit | unfit", "a string"],
    startDate: ["01 Jan 2024", "a date", "a string"],
    endDate: ["2024-02-30", "a date", "a string"],
    medicalOfficer1: STRING,
    medicalOfficer2: STRING,
  },
  { optional: ["endDate"], strict: true },
);

describe("ARMC_HISTORY", () => {
  it("reads a patient's ARMC History a page at a time, by the patient's id", () => {
    expect(ARMC_HISTORY.path("eq081-VQEgP8drUUqCWzHfw3", { count: 5, startIndex: 6 })).toBe(
      "/api/patient-armc/eq081-VQEgP8drUUqCWzHfw3?startIndex=6&count=5",
    );
  });

  it("sends the patient's id encoded as a path segment", () => {
    expect(ARMC_HISTORY.path("a/b c", { count: 5, startIndex: 1 })).toBe(
      "/api/patient-armc/a%2Fb%20c?startIndex=1&count=5",
    );
  });

  it("reads with patient:read, a 404 being no patient rather than a failure", () => {
    expect([ARMC_HISTORY.permission, ARMC_HISTORY.absentOnNotFound]).toEqual([
      "patient:read",
      true,
    ]);
  });

  it("decodes a page of ARMC Boards, one still open with no End Date", async () => {
    const open = without({ ...ACES_FIT, id: "armc-open" }, "endDate");

    await expect(
      ARMC_HISTORY.decode(
        new Response(JSON.stringify({ records: [ACES_FIT, open], startIndex: 1, totalResults: 2 })),
      ),
    ).resolves.toStrictEqual({ rows: [ACES_FIT, open], startIndex: 1, totalResults: 2 });
  });

  // An absent End Date is a board still open; a null one is no date at all.
  it("refuses an ARMC Board whose End Date is null", async () => {
    expect(
      await refused({ records: [{ ...ACES_FIT, endDate: null }], startIndex: 1, totalResults: 1 }),
    ).toStrictEqual(new DecodeError("ArmcBoard.endDate is not a string"));
  });

  it("refuses a page holding an ARMC Board it cannot read", async () => {
    expect(
      await refused({
        records: [{ ...ACES_FIT, outcome: "waiver-granted" }],
        startIndex: 1,
        totalResults: 1,
      }),
    ).toStrictEqual(new DecodeError("ArmcBoard.outcome is not fit | unfit"));
  });

  it("refuses a page holding a field it does not know", async () => {
    expect(
      await refused({ records: [], startIndex: 1, totalResults: 0, nextPage: null }),
    ).toStrictEqual(new DecodeError("ArmcHistoryPage.nextPage is not expected"));
  });
});
