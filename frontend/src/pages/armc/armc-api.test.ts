import { describe, expect, it } from "vitest";

import { checkDateRanges } from "@/lib/date-range";
import { DecodeError } from "@/lib/decode";

import {
  ARMC_DATE_COLUMNS,
  ARMC_HISTORY,
  type ArmcFilters,
  countArmcFilters,
  decodeArmcBoard,
  NO_ARMC_FILTERS,
} from "./armc-api";

/** One ARMC Board exactly as the backend serialises it, every field present. */
const ACES_FIT = {
  id: "armc-tan-wei-ming-1",
  boardType: "aces",
  outcome: "fit",
  startDate: "2024-01-01",
  endDate: "2024-02-10",
  medicalOfficer1: "Medical Officer 1",
  medicalOfficer2: "Medical Officer 2",
  invalid: [],
};

/** {@link ACES_FIT} as the page reads it. */
const ACES_FIT_READ = {
  id: { state: "present", value: "armc-tan-wei-ming-1" },
  boardType: { state: "present", value: "aces" },
  outcome: { state: "present", value: "fit" },
  startDate: { state: "present", value: "2024-01-01" },
  endDate: { state: "present", value: "2024-02-10" },
  medicalOfficer1: { state: "present", value: "Medical Officer 1" },
  medicalOfficer2: { state: "present", value: "Medical Officer 2" },
};

/** Every element of an ARMC Board, as the backend names it. */
const ELEMENTS = [
  "id",
  "boardType",
  "outcome",
  "startDate",
  "endDate",
  "medicalOfficer1",
  "medicalOfficer2",
] as const;

/** `decode(body)`'s refusal, read through the declaration as a `Response`. */
async function refused(body: unknown) {
  return Promise.resolve(ARMC_HISTORY.decode(new Response(JSON.stringify(body)))).then(
    () => null,
    (error: unknown) => error,
  );
}

// An ARMC Board is shown as received (ADR 0020): each field is read by the
// lenient field decoder (`readObject(…).sourceFields()`, lib/decode.test.ts),
// so its value never refuses the board — only its shape does.
describe("decodeArmcBoard", () => {
  it("reads every field present as it came", () => {
    expect(decodeArmcBoard(ACES_FIT)).toStrictEqual(ACES_FIT_READ);
  });

  // A board still open has no End Date: the backend sends it null.
  it("reads a null field as missing, and one the row names invalid as invalid", () => {
    expect(
      decodeArmcBoard({
        ...ACES_FIT,
        id: null,
        endDate: null,
        medicalOfficer2: null,
        startDate: null,
        invalid: ["startDate"],
      }),
    ).toStrictEqual({
      ...ACES_FIT_READ,
      id: { state: "missing" },
      endDate: { state: "missing" },
      medicalOfficer2: { state: "missing" },
      startDate: { state: "invalid" },
    });
  });

  it.each(ELEMENTS)("reads %s invalid when the row names it", (field) => {
    expect(decodeArmcBoard({ ...ACES_FIT, [field]: null, invalid: [field] })).toStrictEqual({
      ...ACES_FIT_READ,
      [field]: { state: "invalid" },
    });
  });

  // Which strings are calendar dates is `isCalendarDate`'s (lib/time.test.ts).
  it.each(["startDate", "endDate"] as const)(
    "reads %s invalid when it is no calendar date",
    (field) => {
      expect(decodeArmcBoard({ ...ACES_FIT, [field]: "2024-02-30" })[field]).toStrictEqual({
        state: "invalid",
      });
    },
  );

  // A code is matched by the page, not the decoder: another case or another
  // set's code is kept as the source sent it.
  it.each([
    ["boardType", "ACES"],
    ["boardType", "aircrew"],
    ["outcome", "FIT"],
    ["outcome", "waiver-granted"],
  ])("reads %s outside its closed set as present, as it came: %s", (field, code) => {
    expect(decodeArmcBoard({ ...ACES_FIT, [field]: code })).toStrictEqual({
      ...ACES_FIT_READ,
      [field]: { state: "present", value: code },
    });
  });

  it("reads an id outside FHIR's id grammar as present: ids are not checked", () => {
    expect(decodeArmcBoard({ ...ACES_FIT, id: "armc tan/1" }).id).toStrictEqual({
      state: "present",
      value: "armc tan/1",
    });
  });

  // No date's meaning is checked: an End Date before the Start Date is a date.
  it("reads an End Date before the Start Date as present", () => {
    expect(decodeArmcBoard({ ...ACES_FIT, endDate: "2023-12-31" }).endDate).toStrictEqual({
      state: "present",
      value: "2023-12-31",
    });
  });

  it.each(ELEMENTS)("refuses a board without %s, which the backend always sends", (field) => {
    expect(() =>
      decodeArmcBoard(
        Object.fromEntries(Object.entries(ACES_FIT).filter(([key]) => key !== field)),
      ),
    ).toThrow(new DecodeError(`ArmcBoard.${field} is not a string or null`));
  });

  it("refuses a board without invalid", () => {
    expect(() =>
      decodeArmcBoard(
        Object.fromEntries(Object.entries(ACES_FIT).filter(([key]) => key !== "invalid")),
      ),
    ).toThrow(new DecodeError("ArmcBoard.invalid is not an array"));
  });

  it("refuses a field the board does not have", () => {
    expect(() => decodeArmcBoard({ ...ACES_FIT, status: "active" })).toThrow(
      new DecodeError("ArmcBoard.status is not expected"),
    );
  });

  it.each([
    ["an array", [ACES_FIT]],
    ["null", null],
    ["a string", "board"],
  ])("refuses %s as a board", (_, value) => {
    expect(() => decodeArmcBoard(value)).toThrow(new DecodeError("ArmcBoard is not an object"));
  });
});

// How a date range is checked is `checkDateRanges`' (lib/date-range.test.ts):
// here, that the ARMC History's two ranges are the ones checked, by name.
describe("the ARMC History's date ranges", () => {
  it("declares the Start Date and the End Date, both calendar dates, in that order", () => {
    expect(ARMC_DATE_COLUMNS).toEqual([
      { column: "startDate", label: "Start Date", precision: "date" },
      { column: "endDate", label: "End Date", precision: "date" },
    ]);
  });

  it.each([
    ["startDate", "Start Date"],
    ["endDate", "End Date"],
  ] as const)("refuses a %s from-date after its to-date, naming the range", (column, label) => {
    expect(
      checkDateRanges(ARMC_DATE_COLUMNS, {
        ...NO_ARMC_FILTERS,
        [`${column}From`]: "2024-01-02",
        [`${column}To`]: "2024-01-01",
      }),
    ).toEqual({
      column,
      message: `The ${label} from-date must not be after its to-date.`,
      ok: false,
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

  it("decodes a page of ARMC Boards", async () => {
    await expect(
      ARMC_HISTORY.decode(
        new Response(JSON.stringify({ records: [ACES_FIT], startIndex: 1, totalResults: 2 })),
      ),
    ).resolves.toStrictEqual({ rows: [ACES_FIT_READ], startIndex: 1, totalResults: 2 });
  });

  it("refuses a page holding an ARMC Board whose shape it cannot read", async () => {
    expect(
      await refused({
        records: [{ ...ACES_FIT, invalid: "outcome" }],
        startIndex: 1,
        totalResults: 1,
      }),
    ).toStrictEqual(new DecodeError("ArmcBoard.invalid is not an array"));
  });

  it("refuses a page holding a field it does not know", async () => {
    expect(
      await refused({ records: [], startIndex: 1, totalResults: 0, nextPage: null }),
    ).toStrictEqual(new DecodeError("ArmcHistoryPage.nextPage is not expected"));
  });
});
