import { describe, expect, it } from "vitest";

import { checkDateRanges, dateRangeRefusal } from "./date-range";

const ISSUE_DATE = { label: "Issue Date", precision: "date" } as const;
const EXPIRY_DATE = { label: "Expiry Date", precision: "date" } as const;
const TAKEN_AT = { label: "Taken At", precision: "date-time" } as const;

describe("dateRangeRefusal, over calendar dates", () => {
  it.each([
    ["both ends", "2024-01-01", "2024-12-31"],
    ["one day", "2024-01-01", "2024-01-01"],
    ["a from-date alone", "2024-01-01", ""],
    ["a to-date alone", "", "2024-12-31"],
    ["neither end", "", ""],
  ])("passes a range with %s", (_, from, to) => {
    expect(dateRangeRefusal(ISSUE_DATE, from, to)).toBeNull();
  });

  it("refuses a from-date after its to-date, naming the range", () => {
    expect(dateRangeRefusal(ISSUE_DATE, "2024-01-02", "2024-01-01")).toBe(
      "The Issue Date from-date must not be after its to-date.",
    );
  });

  it.each([
    ["from-date", "275760-02-30", ""],
    ["to-date", "", "2024-02-30"],
    ["date-time", "2024-01-01T09:30", ""],
  ])("refuses a %s the calendar does not have, naming the range", (_, from, to) => {
    expect(dateRangeRefusal(EXPIRY_DATE, from, to)).toBe(
      "Enter the Expiry Date dates as real dates.",
    );
  });
});

describe("dateRangeRefusal, over date-times to the minute", () => {
  it.each([
    ["both ends", "2024-01-01T09:30", "2024-12-31T17:00"],
    ["one minute", "2024-01-01T09:30", "2024-01-01T09:30"],
    ["a from-time alone", "2024-01-01T09:30", ""],
    ["a to-time alone", "", "2024-12-31T17:00"],
    ["neither end", "", ""],
  ])("passes a range with %s", (_, from, to) => {
    expect(dateRangeRefusal(TAKEN_AT, from, to)).toBeNull();
  });

  it.each([
    ["a minute later on the same day", "2024-01-01T09:31", "2024-01-01T09:30"],
    ["an earlier time on a later day", "2024-01-02T08:00", "2024-01-01T23:59"],
  ])("refuses a from-time after its to-time, %s, naming the range", (_, from, to) => {
    expect(dateRangeRefusal(TAKEN_AT, from, to)).toBe(
      "The Taken At from-date must not be after its to-date.",
    );
  });

  it.each([
    ["a date alone", "2024-01-01", ""],
    ["seconds", "", "2024-01-01T09:30:00"],
    ["an offset", "2024-01-01T09:30+08:00", ""],
    ["a day the calendar does not have", "", "2024-02-30T09:30"],
  ])("refuses %s, naming the range", (_, from, to) => {
    expect(dateRangeRefusal(TAKEN_AT, from, to)).toBe(
      "Enter the Taken At dates and times as real dates and times.",
    );
  });
});

describe("checkDateRanges", () => {
  const COLUMNS = [
    { column: "issueDate", ...ISSUE_DATE },
    { column: "expiryDate", ...EXPIRY_DATE },
  ] as const;
  const OPEN = { expiryDateFrom: "", expiryDateTo: "", issueDateFrom: "", issueDateTo: "" };

  it("passes filters with every end open", () => {
    expect(checkDateRanges(COLUMNS, OPEN)).toEqual({ ok: true });
  });

  it("refuses a date the calendar does not have with the range it is in", () => {
    expect(checkDateRanges(COLUMNS, { ...OPEN, issueDateTo: "2024-02-30" })).toEqual({
      column: "issueDate",
      message: "Enter the Issue Date dates as real dates.",
      ok: false,
    });
  });

  it("passes filters whose every range passes", () => {
    expect(
      checkDateRanges(COLUMNS, {
        ...OPEN,
        expiryDateTo: "2025-01-01",
        issueDateFrom: "2024-01-01",
      }),
    ).toEqual({ ok: true });
  });

  it("refuses with the range at fault and why", () => {
    expect(
      checkDateRanges(COLUMNS, {
        ...OPEN,
        expiryDateFrom: "2025-01-02",
        expiryDateTo: "2025-01-01",
      }),
    ).toEqual({
      column: "expiryDate",
      message: "The Expiry Date from-date must not be after its to-date.",
      ok: false,
    });
  });

  it("refuses with the first range at fault, in the order the ranges are given", () => {
    expect(
      checkDateRanges(COLUMNS, {
        expiryDateFrom: "2025-01-02",
        expiryDateTo: "2025-01-01",
        issueDateFrom: "2024-01-02",
        issueDateTo: "2024-01-01",
      }),
    ).toMatchObject({ column: "issueDate", ok: false });
  });

  it("checks each range at its own column's precision", () => {
    expect(
      checkDateRanges([{ column: "takenAt", ...TAKEN_AT }], {
        takenAtFrom: "2024-01-01T09:31",
        takenAtTo: "2024-01-01T09:30",
      }),
    ).toEqual({
      column: "takenAt",
      message: "The Taken At from-date must not be after its to-date.",
      ok: false,
    });
  });
});
