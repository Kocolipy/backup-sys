import { describe, expect, it } from "vitest";

import { checkDateRanges } from "@/lib/date-range";
import { DecodeError } from "@/lib/decode";
import { describeDecoder, STRING, without } from "@/lib/decode.testHelpers";

import {
  countMedicationFilters,
  decodeMedicationRecord,
  MEDICATION_DATE_COLUMNS,
  MEDICATION_HISTORY,
  type MedicationFilters,
  NO_MEDICATION_FILTERS,
} from "./medication-api";

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

// How a date range is checked at each precision is `checkDateRanges`'
// (lib/date-range.test.ts): here, that the Medication History's two ranges are
// the ones checked, by name, as date-times.
describe("the Medication History's date ranges", () => {
  it("declares the Start Date and the End Date, both date-times, in that order", () => {
    expect(MEDICATION_DATE_COLUMNS).toEqual([
      { column: "startDate", label: "Start Date", precision: "date-time" },
      { column: "endDate", label: "End Date", precision: "date-time" },
    ]);
  });

  it("checks its filters' ranges by the columns it declares, to the minute", () => {
    expect(
      checkDateRanges(MEDICATION_DATE_COLUMNS, {
        ...NO_MEDICATION_FILTERS,
        endDateFrom: "2025-07-21T16:21",
        endDateTo: "2025-07-21T16:20",
      }),
    ).toEqual({
      column: "endDate",
      message: "The End Date from-date must not be after its to-date.",
      ok: false,
    });
  });

  it("refuses a range end that is a date alone", () => {
    expect(
      checkDateRanges(MEDICATION_DATE_COLUMNS, {
        ...NO_MEDICATION_FILTERS,
        startDateFrom: "2025-07-14",
      }),
    ).toEqual({
      column: "startDate",
      message: "Enter the Start Date dates and times as real dates and times.",
      ok: false,
    });
  });
});

describe("countMedicationFilters", () => {
  it("counts none for no filter", () => {
    expect(countMedicationFilters(NO_MEDICATION_FILTERS)).toBe(0);
  });

  it.each([
    ["the Dispense Statuses, however many", { dispenseStatuses: ["pending", "uncollected"] }],
    ["a Start Date from-end", { startDateFrom: "2025-07-14T16:20" }],
    ["a Start Date to-end", { startDateTo: "2025-07-14T16:20" }],
    ["an End Date from-end", { endDateFrom: "2025-01-01T00:00" }],
    ["an End Date to-end", { endDateTo: "2025-12-31T23:59" }],
  ] as const)("counts %s as one", (_, set) => {
    expect(countMedicationFilters({ ...NO_MEDICATION_FILTERS, ...set })).toBe(1);
  });

  it("counts every filter set", () => {
    expect(countMedicationFilters(EVERY_FILTER)).toBe(5);
  });
});

/** Every filter set, the Dispense Statuses given out of their set's order. */
const EVERY_FILTER: MedicationFilters = {
  dispenseStatuses: ["uncollected", "pending"],
  endDateFrom: "2025-01-01T00:00",
  endDateTo: "2025-12-31T23:59",
  startDateFrom: "2025-07-14T16:20",
  startDateTo: "2026-03-01T10:00",
};

const FIRST_PAGE = { count: 5, startIndex: 1 };

describe("MEDICATION_HISTORY", () => {
  it("reads a patient's Medication History a page at a time, by the patient's id", () => {
    expect(
      MEDICATION_HISTORY.path("eq081-VQEgP8drUUqCWzHfw3", NO_MEDICATION_FILTERS, null, {
        count: 5,
        startIndex: 6,
      }),
    ).toBe("/api/patient-medication/eq081-VQEgP8drUUqCWzHfw3?startIndex=6&count=5");
  });

  it("sends the patient's id encoded as a path segment", () => {
    expect(MEDICATION_HISTORY.path("a/b c", NO_MEDICATION_FILTERS, null, FIRST_PAGE)).toBe(
      "/api/patient-medication/a%2Fb%20c?startIndex=1&count=5",
    );
  });

  // Each Dispense Status is a parameter of its own, repeated, in its closed
  // set's order; each date-time end is its Singapore minute, with no offset.
  it("asks for every filter set, and the sort, before the page", () => {
    expect(
      MEDICATION_HISTORY.path(
        "dev-patient-05",
        EVERY_FILTER,
        { by: "endDate", order: "descending" },
        FIRST_PAGE,
      ),
    ).toBe(
      "/api/patient-medication/dev-patient-05?dispenseStatus=pending&dispenseStatus=uncollected" +
        "&startDateFrom=2025-07-14T16:20&startDateTo=2026-03-01T10:00" +
        "&endDateFrom=2025-01-01T00:00&endDateTo=2025-12-31T23:59" +
        "&sortBy=endDate&sortOrder=descending&startIndex=1&count=5",
    );
  });

  it.each([
    ["a Dispense Status", { dispenseStatuses: ["dispensed"] }, "dispenseStatus=dispensed"],
    [
      "a Start Date from-end",
      { startDateFrom: "2026-03-01T10:00" },
      "startDateFrom=2026-03-01T10:00",
    ],
    ["a Start Date to-end", { startDateTo: "2025-07-14T16:20" }, "startDateTo=2025-07-14T16:20"],
    ["an End Date from-end", { endDateFrom: "2025-01-01T00:00" }, "endDateFrom=2025-01-01T00:00"],
    ["an End Date to-end", { endDateTo: "2023-12-31T23:59" }, "endDateTo=2023-12-31T23:59"],
  ] as const)("asks for %s alone when it is the one filter set", (_, set, asked) => {
    expect(
      MEDICATION_HISTORY.path("p1", { ...NO_MEDICATION_FILTERS, ...set }, null, FIRST_PAGE),
    ).toBe(`/api/patient-medication/p1?${asked}&startIndex=1&count=5`);
  });

  it.each([
    ["startDate", "ascending"],
    ["startDate", "descending"],
    ["endDate", "ascending"],
    ["endDate", "descending"],
  ] as const)("asks for the sort by %s, %s", (by, order) => {
    expect(MEDICATION_HISTORY.path("p1", NO_MEDICATION_FILTERS, { by, order }, FIRST_PAGE)).toBe(
      `/api/patient-medication/p1?sortBy=${by}&sortOrder=${order}&startIndex=1&count=5`,
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
