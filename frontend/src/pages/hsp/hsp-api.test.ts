import { describe, expect, it } from "vitest";

import { checkDateRanges } from "@/lib/date-range";
import { DecodeError } from "@/lib/decode";

import {
  countHspFilters,
  decodeHspRecord,
  HSP_DATE_COLUMNS,
  HSP_HISTORY,
  NO_HSP_FILTERS,
  type HspFilters,
} from "./hsp-api";

/** One HSP Record exactly as the backend serialises it, every field present. */
const LOW = {
  id: "hsp-tan-wei-ming-1",
  outcome: "low",
  startDate: "2024-03-11",
  endDate: "2025-03-10",
  submittingMc: "nee-soon-camp",
  status: "active",
  invalid: [],
};

/** {@link LOW} as the page reads it. */
const LOW_READ = {
  id: { state: "present", value: "hsp-tan-wei-ming-1" },
  outcome: { state: "present", value: "low" },
  startDate: { state: "present", value: "2024-03-11" },
  endDate: { state: "present", value: "2025-03-10" },
  submittingMc: { state: "present", value: "nee-soon-camp" },
  status: { state: "present", value: "active" },
};

/** Every field of an HSP Record's row the patient source sent. */
const SOURCE_FIELDS = ["id", "outcome", "startDate", "endDate", "submittingMc", "status"] as const;

/** `decode(body)`'s refusal, read through the declaration as a `Response`. */
async function refused(body: unknown) {
  return Promise.resolve(HSP_HISTORY.decode(new Response(JSON.stringify(body)))).then(
    () => null,
    (error: unknown) => error,
  );
}

// An HSP Record is shown as received (ADR 0020): each field is read by the
// lenient field decoder (`readObject(…).sourceFields()`, lib/decode.test.ts),
// so its value never refuses the record — only its shape does.
describe("decodeHspRecord", () => {
  it("reads every field present as it came", () => {
    expect(decodeHspRecord(LOW)).toStrictEqual(LOW_READ);
  });

  it("reads a null field as missing, and one the row names invalid as invalid", () => {
    expect(
      decodeHspRecord({ ...LOW, id: null, outcome: null, status: null, invalid: ["status"] }),
    ).toStrictEqual({
      ...LOW_READ,
      id: { state: "missing" },
      outcome: { state: "missing" },
      status: { state: "invalid" },
    });
  });

  it.each(SOURCE_FIELDS)("reads %s invalid when the row names it", (field) => {
    expect(decodeHspRecord({ ...LOW, [field]: null, invalid: [field] })).toStrictEqual({
      ...LOW_READ,
      [field]: { state: "invalid" },
    });
  });

  // Which strings are calendar dates is `isCalendarDate`'s (lib/time.test.ts).
  it.each(["startDate", "endDate"] as const)(
    "reads %s invalid when it is no calendar date",
    (field) => {
      expect(decodeHspRecord({ ...LOW, [field]: "2025-02-30" })[field]).toStrictEqual({
        state: "invalid",
      });
    },
  );

  // A code is matched by the page, not the decoder: a label, another case or
  // a code of no set is kept as the source sent it, in each of the three sets.
  it.each([
    ["outcome", "LOW"],
    ["outcome", "critical"],
    ["submittingMc", "Nee Soon Camp MC"],
    ["submittingMc", "tengah-air-base"],
    ["status", "expired"],
  ])("reads %s outside its closed set as present, as it came: %s", (field, code) => {
    expect(decodeHspRecord({ ...LOW, [field]: code })).toStrictEqual({
      ...LOW_READ,
      [field]: { state: "present", value: code },
    });
  });

  it.each(SOURCE_FIELDS)(
    "refuses an HSP Record without %s, which the backend always sends",
    (field) => {
      expect(() =>
        decodeHspRecord(Object.fromEntries(Object.entries(LOW).filter(([key]) => key !== field))),
      ).toThrow(new DecodeError(`HspRecord.${field} is not a string or null`));
    },
  );

  it("refuses an HSP Record without invalid", () => {
    expect(() =>
      decodeHspRecord(Object.fromEntries(Object.entries(LOW).filter(([key]) => key !== "invalid"))),
    ).toThrow(new DecodeError("HspRecord.invalid is not an array"));
  });

  it("refuses a field the HSP Record does not have", () => {
    expect(() => decodeHspRecord({ ...LOW, type: "ippt" })).toThrow(
      new DecodeError("HspRecord.type is not expected"),
    );
  });

  it.each([
    ["an array", [LOW]],
    ["null", null],
    ["a string", "hsp"],
  ])("refuses %s as an HSP Record", (_, value) => {
    expect(() => decodeHspRecord(value)).toThrow(new DecodeError("HspRecord is not an object"));
  });
});

// How a date range is checked is `checkDateRanges`' (lib/date-range.test.ts):
// here, that the HSP History's two ranges are the ones checked, by name.
describe("the HSP History's date ranges", () => {
  it("declares the Start Date and the End Date, both calendar dates, in that order", () => {
    expect(HSP_DATE_COLUMNS).toEqual([
      { column: "startDate", label: "Start Date", precision: "date" },
      { column: "endDate", label: "End Date", precision: "date" },
    ]);
  });

  it("checks its filters' ranges by the columns it declares", () => {
    expect(
      checkDateRanges(HSP_DATE_COLUMNS, {
        ...NO_HSP_FILTERS,
        endDateFrom: "2025-01-02",
        endDateTo: "2025-01-01",
      }),
    ).toEqual({
      column: "endDate",
      message: "The End Date from-date must not be after its to-date.",
      ok: false,
    });
  });
});

describe("countHspFilters", () => {
  it("counts none for no filter", () => {
    expect(countHspFilters(NO_HSP_FILTERS)).toBe(0);
  });

  it.each([
    ["the Outcomes, however many", { outcomes: ["low", "high"] }],
    ["a Start Date from-date", { startDateFrom: "2024-01-01" }],
    ["a Start Date to-date", { startDateTo: "2024-12-31" }],
    ["an End Date from-date", { endDateFrom: "2025-01-01" }],
    ["an End Date to-date", { endDateTo: "2025-12-31" }],
    ["the Submitting MCs", { submittingMcs: ["kranji-camp"] }],
    ["the Status", { status: "inactive" }],
  ] as const)("counts %s as one", (_, set) => {
    expect(countHspFilters({ ...NO_HSP_FILTERS, ...set })).toBe(1);
  });

  it("counts every filter set", () => {
    expect(countHspFilters(EVERY_FILTER)).toBe(7);
  });
});

/** Every filter set, the codes of each set given out of their set's order. */
const EVERY_FILTER: HspFilters = {
  endDateFrom: "2025-01-01",
  endDateTo: "2025-12-31",
  outcomes: ["high", "low"],
  startDateFrom: "2024-01-01",
  startDateTo: "2024-12-31",
  status: "active",
  submittingMcs: ["mandai-hill-camp", "nee-soon-camp"],
};

const FIRST_PAGE = { count: 5, startIndex: 1 };

describe("HSP_HISTORY", () => {
  it("reads a patient's HSP History a page at a time, by the patient's id", () => {
    expect(
      HSP_HISTORY.path("eq081-VQEgP8drUUqCWzHfw3", NO_HSP_FILTERS, null, {
        count: 5,
        startIndex: 6,
      }),
    ).toBe("/api/patient-hsp/eq081-VQEgP8drUUqCWzHfw3?startIndex=6&count=5");
  });

  // Each code is a parameter of its own, repeated, in its closed set's order.
  it("asks for every filter set, and the sort, before the page", () => {
    expect(
      HSP_HISTORY.path(
        "dev-patient-10",
        EVERY_FILTER,
        { by: "endDate", order: "descending" },
        FIRST_PAGE,
      ),
    ).toBe(
      "/api/patient-hsp/dev-patient-10?outcome=low&outcome=high" +
        "&startDateFrom=2024-01-01&startDateTo=2024-12-31" +
        "&endDateFrom=2025-01-01&endDateTo=2025-12-31" +
        "&submittingMc=nee-soon-camp&submittingMc=mandai-hill-camp&status=active" +
        "&sortBy=endDate&sortOrder=descending&startIndex=1&count=5",
    );
  });

  it.each([
    ["an Outcome", { outcomes: ["moderate"] }, "outcome=moderate"],
    ["a Start Date from-date", { startDateFrom: "2024-01-01" }, "startDateFrom=2024-01-01"],
    ["a Start Date to-date", { startDateTo: "2024-12-31" }, "startDateTo=2024-12-31"],
    ["an End Date from-date", { endDateFrom: "2025-01-01" }, "endDateFrom=2025-01-01"],
    ["an End Date to-date", { endDateTo: "2025-12-31" }, "endDateTo=2025-12-31"],
    ["a Submitting MC", { submittingMcs: ["hendon-camp"] }, "submittingMc=hendon-camp"],
    ["the Status Active", { status: "active" }, "status=active"],
    ["the Status Inactive", { status: "inactive" }, "status=inactive"],
  ] as const)("asks for %s alone when it is the one filter set", (_, set, asked) => {
    expect(HSP_HISTORY.path("p1", { ...NO_HSP_FILTERS, ...set }, null, FIRST_PAGE)).toBe(
      `/api/patient-hsp/p1?${asked}&startIndex=1&count=5`,
    );
  });

  it.each([
    ["startDate", "ascending"],
    ["startDate", "descending"],
    ["endDate", "ascending"],
  ] as const)("asks for the sort by %s, %s", (by, order) => {
    expect(HSP_HISTORY.path("p1", NO_HSP_FILTERS, { by, order }, FIRST_PAGE)).toBe(
      `/api/patient-hsp/p1?sortBy=${by}&sortOrder=${order}&startIndex=1&count=5`,
    );
  });

  it("encodes the patient's id as one path segment", () => {
    expect(HSP_HISTORY.path("a/b c", NO_HSP_FILTERS, null, FIRST_PAGE)).toBe(
      "/api/patient-hsp/a%2Fb%20c?startIndex=1&count=5",
    );
  });

  it("reads with patient:read, a 404 being no patient rather than a failure", () => {
    expect([HSP_HISTORY.permission, HSP_HISTORY.absentOnNotFound]).toEqual(["patient:read", true]);
  });

  it("decodes a page of HSP Records", async () => {
    await expect(
      HSP_HISTORY.decode(
        new Response(JSON.stringify({ records: [LOW], startIndex: 1, totalResults: 3 })),
      ),
    ).resolves.toStrictEqual({ rows: [LOW_READ], startIndex: 1, totalResults: 3 });
  });

  it("refuses a page holding an HSP Record whose shape it cannot read", async () => {
    expect(
      await refused({ records: [{ ...LOW, invalid: "status" }], startIndex: 1, totalResults: 1 }),
    ).toStrictEqual(new DecodeError("HspRecord.invalid is not an array"));
  });

  it("refuses a page holding a field it does not know", async () => {
    expect(
      await refused({ records: [], startIndex: 1, totalResults: 0, nextPage: null }),
    ).toStrictEqual(new DecodeError("HspHistoryPage.nextPage is not expected"));
  });
});
