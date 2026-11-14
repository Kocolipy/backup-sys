import { describe, expect, it } from "vitest";

import { checkDateRanges } from "@/lib/date-range";
import { DecodeError } from "@/lib/decode";
import { describeDecoder, STRING, without } from "@/lib/decode.testHelpers";

import {
  ARMC_DATE_RANGES,
  ARMC_HISTORY,
  type ArmcFilters,
  countArmcFilters,
  decodeArmcBoard,
  NO_ARMC_FILTERS,
} from "./armc-api";

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

// How a date range is checked is `checkDateRanges`' (lib/date-range.test.ts):
// here, that the ARMC History's two ranges are the ones checked, by name.
describe("the ARMC History's date ranges", () => {
  it.each([
    ["startDate", "Start Date"],
    ["endDate", "End Date"],
  ] as const)("refuses a %s from-date after its to-date, naming the range", (range, label) => {
    expect(
      checkDateRanges(ARMC_DATE_RANGES, {
        ...NO_ARMC_FILTERS,
        [`${range}From`]: "2024-01-02",
        [`${range}To`]: "2024-01-01",
      }),
    ).toEqual({
      message: `The ${label} from-date must not be after its to-date.`,
      ok: false,
      range,
    });
  });
});

describe("countArmcFilters", () => {
  it("counts none for no filter, All being no Outcome", () => {
    expect(countArmcFilters(NO_ARMC_FILTERS)).toBe(0);
  });

  it.each([
    ["the Outcome FIT", { outcome: "fit" }],
    ["the Outcome UNFIT", { outcome: "unfit" }],
    ["a Start Date from-date", { startDateFrom: "2023-01-01" }],
    ["a Start Date to-date", { startDateTo: "2023-12-31" }],
    ["an End Date from-date", { endDateFrom: "2024-01-01" }],
    ["an End Date to-date", { endDateTo: "2024-12-31" }],
  ] as const)("counts %s as one", (_, set) => {
    expect(countArmcFilters({ ...NO_ARMC_FILTERS, ...set })).toBe(1);
  });

  it("counts every filter set", () => {
    expect(countArmcFilters(EVERY_FILTER)).toBe(5);
  });
});

/** Every filter set. */
const EVERY_FILTER: ArmcFilters = {
  endDateFrom: "2024-01-01",
  endDateTo: "2024-12-31",
  outcome: "unfit",
  startDateFrom: "2023-01-01",
  startDateTo: "2023-12-31",
};

const FIRST_PAGE = { count: 5, startIndex: 1 };

describe("ARMC_HISTORY", () => {
  it("reads a patient's ARMC History a page at a time, by the patient's id", () => {
    expect(
      ARMC_HISTORY.path("eq081-VQEgP8drUUqCWzHfw3", NO_ARMC_FILTERS, null, {
        count: 5,
        startIndex: 6,
      }),
    ).toBe("/api/patient-armc/eq081-VQEgP8drUUqCWzHfw3?startIndex=6&count=5");
  });

  it("sends the patient's id encoded as a path segment", () => {
    expect(ARMC_HISTORY.path("a/b c", NO_ARMC_FILTERS, null, FIRST_PAGE)).toBe(
      "/api/patient-armc/a%2Fb%20c?startIndex=1&count=5",
    );
  });

  it("asks for every filter set, and the sort, before the page", () => {
    expect(
      ARMC_HISTORY.path(
        "dev-patient-05",
        EVERY_FILTER,
        { by: "endDate", order: "descending" },
        FIRST_PAGE,
      ),
    ).toBe(
      "/api/patient-armc/dev-patient-05?outcome=unfit" +
        "&startDateFrom=2023-01-01&startDateTo=2023-12-31" +
        "&endDateFrom=2024-01-01&endDateTo=2024-12-31" +
        "&sortBy=endDate&sortOrder=descending&startIndex=1&count=5",
    );
  });

  it.each([
    ["the Outcome FIT", { outcome: "fit" }, "outcome=fit"],
    ["the Outcome UNFIT", { outcome: "unfit" }, "outcome=unfit"],
    ["a Start Date from-date", { startDateFrom: "2023-01-01" }, "startDateFrom=2023-01-01"],
    ["a Start Date to-date", { startDateTo: "2023-12-31" }, "startDateTo=2023-12-31"],
    ["an End Date from-date", { endDateFrom: "2024-01-01" }, "endDateFrom=2024-01-01"],
    ["an End Date to-date", { endDateTo: "2024-12-31" }, "endDateTo=2024-12-31"],
  ] as const)("asks for %s alone when it is the one filter set", (_, set, asked) => {
    expect(ARMC_HISTORY.path("p1", { ...NO_ARMC_FILTERS, ...set }, null, FIRST_PAGE)).toBe(
      `/api/patient-armc/p1?${asked}&startIndex=1&count=5`,
    );
  });

  it.each([
    ["startDate", "ascending"],
    ["startDate", "descending"],
    ["endDate", "ascending"],
    ["endDate", "descending"],
  ] as const)("asks for the sort by %s, %s", (by, order) => {
    expect(ARMC_HISTORY.path("p1", NO_ARMC_FILTERS, { by, order }, FIRST_PAGE)).toBe(
      `/api/patient-armc/p1?sortBy=${by}&sortOrder=${order}&startIndex=1&count=5`,
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
