import { describe, expect, it } from "vitest";

import { checkDateRanges } from "@/lib/date-range";
import { DecodeError } from "@/lib/decode";

import {
  countMedicationFilters,
  decodeMedicationRecord,
  MEDICATION_DATE_COLUMNS,
  MEDICATION_HISTORY,
  type MedicationFilters,
  NO_MEDICATION_FILTERS,
} from "./medication-api";

/** One Medication Record exactly as the backend serialises it, every field present. */
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
  invalid: [],
};

/** {@link AMOXICILLIN} as the page reads it. */
const AMOXICILLIN_READ = {
  id: { state: "present", value: "medication-tan-wei-ming-2" },
  medication: { state: "present", value: "Amoxicillin" },
  dosage: { state: "present", value: "300 mg" },
  route: { state: "present", value: "PO" },
  frequency: { state: "present", value: "TDS" },
  step: { state: "present", value: "1" },
  quantity: { state: "present", value: "20 tablets" },
  dispenseStatus: { state: "present", value: "pending" },
  duration: { state: "present", value: "5 days" },
  startDate: { state: "present", value: "2026-10-18T17:31:00+08:00" },
  endDate: { state: "present", value: "2026-10-22T17:31:00+08:00" },
  dispensary: { state: "present", value: "khatib-camp" },
  prescribingDoctor: { state: "present", value: "Dr Lim Wei Jie" },
};

/** Every element of a Medication Record, as the backend names it. */
const ELEMENTS = [
  "id",
  "medication",
  "dosage",
  "route",
  "frequency",
  "step",
  "quantity",
  "dispenseStatus",
  "duration",
  "startDate",
  "endDate",
  "dispensary",
  "prescribingDoctor",
];

/** `decode(body)`'s refusal, read through the declaration as a `Response`. */
async function refused(body: unknown) {
  return Promise.resolve(MEDICATION_HISTORY.decode(new Response(JSON.stringify(body)))).then(
    () => null,
    (error: unknown) => error,
  );
}

// A Medication Record is shown as received (ADR 0020): each field is read by
// the lenient field decoder (`readObject(…).sourceFields()`,
// lib/decode.test.ts), so its value never refuses the record — only its shape
// does.
describe("decodeMedicationRecord", () => {
  it("reads every field present as it came", () => {
    expect(decodeMedicationRecord(AMOXICILLIN)).toStrictEqual(AMOXICILLIN_READ);
  });

  it("reads a null field as missing, and one the row names invalid as invalid", () => {
    expect(
      decodeMedicationRecord({
        ...AMOXICILLIN,
        id: null,
        step: null,
        dispenseStatus: null,
        endDate: null,
        invalid: ["endDate"],
      }),
    ).toStrictEqual({
      ...AMOXICILLIN_READ,
      id: { state: "missing" },
      step: { state: "missing" },
      dispenseStatus: { state: "missing" },
      endDate: { state: "invalid" },
    });
  });

  it.each(ELEMENTS)("reads %s invalid when the row names it", (field) => {
    expect(
      decodeMedicationRecord({
        ...AMOXICILLIN,
        [field]: null,
        invalid: [field],
      }),
    ).toStrictEqual({ ...AMOXICILLIN_READ, [field]: { state: "invalid" } });
  });

  // Which strings are date-times with an offset is `isOffsetDateTime`'s
  // (lib/time.test.ts): a date alone, or one with no offset, is none.
  it.each([
    ["startDate", "2026-10-18"],
    ["startDate", "2026-10-18T17:31:00"],
    ["endDate", "2026-02-30T17:31:00+08:00"],
  ] as const)("reads %s invalid when it is no date-time with an offset: %s", (field, text) => {
    expect(decodeMedicationRecord({ ...AMOXICILLIN, [field]: text })[field]).toStrictEqual({
      state: "invalid",
    });
  });

  // Free text is any string: one that reads as a date-time is free text still.
  it("reads free text that looks like a date as present, as it came", () => {
    expect(decodeMedicationRecord({ ...AMOXICILLIN, duration: "2026-10-18" }).duration).toEqual({
      state: "present",
      value: "2026-10-18",
    });
  });

  // A code is matched by the page, not the decoder: a label, another case or
  // a code of no set is kept as the source sent it.
  it.each([
    ["dispenseStatus", "DISPENSED"],
    ["dispenseStatus", "on-hold"],
    ["dispensary", "Khatib Camp MC"],
    ["dispensary", "tengah-air-base"],
  ])("reads %s outside its closed set as present, as it came: %s", (field, code) => {
    expect(decodeMedicationRecord({ ...AMOXICILLIN, [field]: code })).toStrictEqual({
      ...AMOXICILLIN_READ,
      [field]: { state: "present", value: code },
    });
  });

  it("reads an id outside FHIR's id grammar as present: ids are not checked", () => {
    expect(decodeMedicationRecord({ ...AMOXICILLIN, id: "medication tan/1" }).id).toStrictEqual({
      state: "present",
      value: "medication tan/1",
    });
  });

  it.each(ELEMENTS)("refuses a record without %s, which the backend always sends", (field) => {
    expect(() =>
      decodeMedicationRecord(
        Object.fromEntries(Object.entries(AMOXICILLIN).filter(([key]) => key !== field)),
      ),
    ).toThrow(new DecodeError(`MedicationRecord.${field} is not a string or null`));
  });

  it("refuses a record without invalid", () => {
    expect(() =>
      decodeMedicationRecord(
        Object.fromEntries(Object.entries(AMOXICILLIN).filter(([key]) => key !== "invalid")),
      ),
    ).toThrow(new DecodeError("MedicationRecord.invalid is not an array"));
  });

  // A Medication Record has no Outcome: one sent is a field the record does not have.
  it("refuses a field the record does not have", () => {
    expect(() => decodeMedicationRecord({ ...AMOXICILLIN, outcome: "fit" })).toThrow(
      new DecodeError("MedicationRecord.outcome is not expected"),
    );
  });

  it.each([
    ["an array", [AMOXICILLIN]],
    ["null", null],
    ["a string", "record"],
  ])("refuses %s as a record", (_, value) => {
    expect(() => decodeMedicationRecord(value)).toThrow(
      new DecodeError("MedicationRecord is not an object"),
    );
  });
});

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

  it("decodes a page of Medication Records as received, one with fields missing and invalid", async () => {
    const received = {
      ...AMOXICILLIN,
      id: null,
      quantity: null,
      dispenseStatus: "on-hold",
      startDate: null,
      invalid: ["quantity", "startDate"],
    };

    await expect(
      MEDICATION_HISTORY.decode(
        new Response(
          JSON.stringify({ records: [AMOXICILLIN, received], startIndex: 1, totalResults: 2 }),
        ),
      ),
    ).resolves.toStrictEqual({
      rows: [
        AMOXICILLIN_READ,
        {
          ...AMOXICILLIN_READ,
          id: { state: "missing" },
          quantity: { state: "invalid" },
          dispenseStatus: { state: "present", value: "on-hold" },
          startDate: { state: "invalid" },
        },
      ],
      startIndex: 1,
      totalResults: 2,
    });
  });

  it("refuses a page holding a Medication Record whose shape is wrong", async () => {
    expect(
      await refused({
        records: [{ ...AMOXICILLIN, invalid: "quantity" }],
        startIndex: 1,
        totalResults: 1,
      }),
    ).toStrictEqual(new DecodeError("MedicationRecord.invalid is not an array"));
  });

  it("refuses a page holding a field it does not know", async () => {
    expect(
      await refused({ records: [], startIndex: 1, totalResults: 0, nextPage: null }),
    ).toStrictEqual(new DecodeError("MedicationHistoryPage.nextPage is not expected"));
  });
});
