import { describe, expect, it } from "vitest";

import { checkDateRanges } from "@/lib/date-range";
import { DecodeError } from "@/lib/decode";

import {
  countNumcFilters,
  decodeNumcRecord,
  NO_NUMC_FILTERS,
  NUMC_DATE_COLUMNS,
  NUMC_HISTORY,
  type NumcFilters,
} from "./numc-api";

/** One NUMC Record exactly as the backend serialises it, every field present. */
const SUBMARINER = {
  id: "numc-tan-wei-ming-2",
  type: "submariner",
  outcome: "waiver-rejected",
  issueDate: "2024-01-10",
  expiryDate: "2025-01-09",
  invalid: [],
};

/** {@link SUBMARINER} as the page reads it. */
const SUBMARINER_READ = {
  id: { state: "present", value: "numc-tan-wei-ming-2" },
  type: { state: "present", value: "submariner" },
  outcome: { state: "present", value: "waiver-rejected" },
  issueDate: { state: "present", value: "2024-01-10" },
  expiryDate: { state: "present", value: "2025-01-09" },
};

/** `decode(body)`'s refusal, read through the declaration as a `Response`. */
async function refused(body: unknown) {
  return Promise.resolve(NUMC_HISTORY.decode(new Response(JSON.stringify(body)))).then(
    () => null,
    (error: unknown) => error,
  );
}

// A NUMC Record is shown as received (ADR 0020): each field is read by the
// lenient field decoder (`readObject(…).sourceFields()`, lib/decode.test.ts),
// so its value never refuses the record — only its shape does.
describe("decodeNumcRecord", () => {
  it("reads every field present as it came", () => {
    expect(decodeNumcRecord(SUBMARINER)).toStrictEqual(SUBMARINER_READ);
  });

  it("reads a null field as missing, and one the row names invalid as invalid", () => {
    expect(
      decodeNumcRecord({
        ...SUBMARINER,
        id: null,
        outcome: null,
        expiryDate: null,
        invalid: ["expiryDate"],
      }),
    ).toStrictEqual({
      ...SUBMARINER_READ,
      id: { state: "missing" },
      outcome: { state: "missing" },
      expiryDate: { state: "invalid" },
    });
  });

  it.each(["id", "type", "outcome", "issueDate", "expiryDate"])(
    "reads %s invalid when the row names it",
    (field) => {
      expect(decodeNumcRecord({ ...SUBMARINER, [field]: null, invalid: [field] })).toStrictEqual({
        ...SUBMARINER_READ,
        [field]: { state: "invalid" },
      });
    },
  );

  // Which strings are calendar dates is `isCalendarDate`'s (lib/time.test.ts).
  it.each(["issueDate", "expiryDate"] as const)(
    "reads %s invalid when it is no calendar date",
    (field) => {
      expect(decodeNumcRecord({ ...SUBMARINER, [field]: "2025-02-30" })[field]).toStrictEqual({
        state: "invalid",
      });
    },
  );

  // A code is matched by the page, not the decoder: a label, another case or
  // another set's code is kept as the source sent it.
  it.each([
    ["type", "Submariner Duties"],
    ["type", "parachuting"],
    ["outcome", "WAIVER GRANTED"],
    ["outcome", "request-waiver"],
  ])("reads %s outside its closed set as present, as it came: %s", (field, code) => {
    expect(decodeNumcRecord({ ...SUBMARINER, [field]: code })).toStrictEqual({
      ...SUBMARINER_READ,
      [field]: { state: "present", value: code },
    });
  });

  it("reads an id outside FHIR's id grammar as present: ids are not checked", () => {
    expect(decodeNumcRecord({ ...SUBMARINER, id: "numc tan/1" }).id).toStrictEqual({
      state: "present",
      value: "numc tan/1",
    });
  });

  it.each(["id", "type", "outcome", "issueDate", "expiryDate"])(
    "refuses a record without %s, which the backend always sends",
    (field) => {
      expect(() =>
        decodeNumcRecord(
          Object.fromEntries(Object.entries(SUBMARINER).filter(([key]) => key !== field)),
        ),
      ).toThrow(new DecodeError(`NumcRecord.${field} is not a string or null`));
    },
  );

  it("refuses a record without invalid", () => {
    expect(() =>
      decodeNumcRecord(
        Object.fromEntries(Object.entries(SUBMARINER).filter(([key]) => key !== "invalid")),
      ),
    ).toThrow(new DecodeError("NumcRecord.invalid is not an array"));
  });

  // A NUMC Record has no Status: one sent is a field the record does not have.
  it("refuses a field the record does not have", () => {
    expect(() => decodeNumcRecord({ ...SUBMARINER, status: "active" })).toThrow(
      new DecodeError("NumcRecord.status is not expected"),
    );
  });

  it.each([
    ["an array", [SUBMARINER]],
    ["null", null],
    ["a string", "record"],
  ])("refuses %s as a record", (_, value) => {
    expect(() => decodeNumcRecord(value)).toThrow(new DecodeError("NumcRecord is not an object"));
  });
});

// How a date range is checked is `checkDateRanges`' (lib/date-range.test.ts):
// here, that the NUMC History's two ranges are the ones checked, by name.
describe("the NUMC History's date ranges", () => {
  it("declares the Issue Date and the Expiry Date, both calendar dates, in that order", () => {
    expect(NUMC_DATE_COLUMNS).toEqual([
      { column: "issueDate", label: "Issue Date", precision: "date" },
      { column: "expiryDate", label: "Expiry Date", precision: "date" },
    ]);
  });

  it("checks its filters' ranges by the columns it declares", () => {
    expect(
      checkDateRanges(NUMC_DATE_COLUMNS, {
        ...NO_NUMC_FILTERS,
        expiryDateFrom: "2025-01-02",
        expiryDateTo: "2025-01-01",
      }),
    ).toEqual({
      column: "expiryDate",
      message: "The Expiry Date from-date must not be after its to-date.",
      ok: false,
    });
  });
});

describe("countNumcFilters", () => {
  it("counts none for no filter", () => {
    expect(countNumcFilters(NO_NUMC_FILTERS)).toBe(0);
  });

  it.each([
    ["the NUMC Types, however many", { types: ["submariner", "diving"] }],
    ["the NUMC Outcomes", { outcomes: ["waiver-granted"] }],
    ["an Issue Date from-date", { issueDateFrom: "2024-01-01" }],
    ["an Issue Date to-date", { issueDateTo: "2024-12-31" }],
    ["an Expiry Date from-date", { expiryDateFrom: "2025-01-01" }],
    ["an Expiry Date to-date", { expiryDateTo: "2025-12-31" }],
  ] as const)("counts %s as one", (_, set) => {
    expect(countNumcFilters({ ...NO_NUMC_FILTERS, ...set })).toBe(1);
  });

  it("counts every filter set", () => {
    expect(countNumcFilters(EVERY_FILTER)).toBe(6);
  });
});

/** Every filter set, the codes of each set given out of their set's order. */
const EVERY_FILTER: NumcFilters = {
  expiryDateFrom: "2025-01-01",
  expiryDateTo: "2025-12-31",
  issueDateFrom: "2024-01-01",
  issueDateTo: "2024-12-31",
  outcomes: ["waiver-rejected", "fit"],
  types: ["diving", "hyperbaric-chamber"],
};

const FIRST_PAGE = { count: 5, startIndex: 1 };

describe("NUMC_HISTORY", () => {
  it("reads a patient's NUMC History a page at a time, by the patient's id", () => {
    expect(
      NUMC_HISTORY.path("eq081-VQEgP8drUUqCWzHfw3", NO_NUMC_FILTERS, null, {
        count: 5,
        startIndex: 6,
      }),
    ).toBe("/api/patient-numc/eq081-VQEgP8drUUqCWzHfw3?startIndex=6&count=5");
  });

  it("sends the patient's id encoded as a path segment", () => {
    expect(NUMC_HISTORY.path("a/b c", NO_NUMC_FILTERS, null, FIRST_PAGE)).toBe(
      "/api/patient-numc/a%2Fb%20c?startIndex=1&count=5",
    );
  });

  // Each code is a parameter of its own, repeated, in its closed set's order.
  it("asks for every filter set, and the sort, before the page", () => {
    expect(
      NUMC_HISTORY.path(
        "dev-patient-05",
        EVERY_FILTER,
        { by: "issueDate", order: "descending" },
        FIRST_PAGE,
      ),
    ).toBe(
      "/api/patient-numc/dev-patient-05?type=hyperbaric-chamber&type=diving" +
        "&outcome=fit&outcome=waiver-rejected" +
        "&issueDateFrom=2024-01-01&issueDateTo=2024-12-31" +
        "&expiryDateFrom=2025-01-01&expiryDateTo=2025-12-31" +
        "&sortBy=issueDate&sortOrder=descending&startIndex=1&count=5",
    );
  });

  it.each([
    ["a NUMC Type", { types: ["submariner"] }, "type=submariner"],
    ["a NUMC Outcome", { outcomes: ["waiver-granted"] }, "outcome=waiver-granted"],
    ["an Issue Date from-date", { issueDateFrom: "2024-01-01" }, "issueDateFrom=2024-01-01"],
    ["an Issue Date to-date", { issueDateTo: "2024-12-31" }, "issueDateTo=2024-12-31"],
    ["an Expiry Date from-date", { expiryDateFrom: "2025-01-01" }, "expiryDateFrom=2025-01-01"],
    ["an Expiry Date to-date", { expiryDateTo: "2025-12-31" }, "expiryDateTo=2025-12-31"],
  ] as const)("asks for %s alone when it is the one filter set", (_, set, asked) => {
    expect(NUMC_HISTORY.path("p1", { ...NO_NUMC_FILTERS, ...set }, null, FIRST_PAGE)).toBe(
      `/api/patient-numc/p1?${asked}&startIndex=1&count=5`,
    );
  });

  it.each([
    ["issueDate", "ascending"],
    ["issueDate", "descending"],
    ["expiryDate", "ascending"],
    ["expiryDate", "descending"],
  ] as const)("asks for the sort by %s, %s", (by, order) => {
    expect(NUMC_HISTORY.path("p1", NO_NUMC_FILTERS, { by, order }, FIRST_PAGE)).toBe(
      `/api/patient-numc/p1?sortBy=${by}&sortOrder=${order}&startIndex=1&count=5`,
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
    ).resolves.toStrictEqual({ rows: [SUBMARINER_READ], startIndex: 1, totalResults: 4 });
  });

  it("refuses a page holding a NUMC Record whose shape it cannot read", async () => {
    expect(
      await refused({
        records: [{ ...SUBMARINER, invalid: "outcome" }],
        startIndex: 1,
        totalResults: 1,
      }),
    ).toStrictEqual(new DecodeError("NumcRecord.invalid is not an array"));
  });

  it("refuses a page holding a field it does not know", async () => {
    expect(
      await refused({ records: [], startIndex: 1, totalResults: 0, nextPage: null }),
    ).toStrictEqual(new DecodeError("NumcHistoryPage.nextPage is not expected"));
  });
});
