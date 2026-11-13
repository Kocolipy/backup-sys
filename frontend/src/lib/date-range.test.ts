import { describe, expect, it } from "vitest";

import { checkDateRanges, dateRangeRefusal } from "./date-range";

describe("dateRangeRefusal", () => {
  it.each([
    ["both ends", "2024-01-01", "2024-12-31"],
    ["one day", "2024-01-01", "2024-01-01"],
    ["a from-date alone", "2024-01-01", ""],
    ["a to-date alone", "", "2024-12-31"],
    ["neither end", "", ""],
  ])("passes a range with %s", (_, from, to) => {
    expect(dateRangeRefusal("Issue Date", from, to)).toBeNull();
  });

  it("refuses a from-date after its to-date, naming the range", () => {
    expect(dateRangeRefusal("Issue Date", "2024-01-02", "2024-01-01")).toBe(
      "The Issue Date from-date must not be after its to-date.",
    );
  });

  it.each([
    ["from-date", "275760-02-30", ""],
    ["to-date", "", "2024-02-30"],
  ])("refuses a %s the calendar does not have, naming the range", (_, from, to) => {
    expect(dateRangeRefusal("Expiry Date", from, to)).toBe(
      "Enter the Expiry Date dates as real dates.",
    );
  });
});

describe("checkDateRanges", () => {
  const RANGES = [
    { label: "Issue Date", range: "issueDate" },
    { label: "Expiry Date", range: "expiryDate" },
  ] as const;
  const OPEN = { expiryDateFrom: "", expiryDateTo: "", issueDateFrom: "", issueDateTo: "" };

  it("passes filters whose every range passes", () => {
    expect(
      checkDateRanges(RANGES, { ...OPEN, expiryDateTo: "2025-01-01", issueDateFrom: "2024-01-01" }),
    ).toEqual({ ok: true });
  });

  it("refuses with the range at fault and why", () => {
    expect(
      checkDateRanges(RANGES, {
        ...OPEN,
        expiryDateFrom: "2025-01-02",
        expiryDateTo: "2025-01-01",
      }),
    ).toEqual({
      message: "The Expiry Date from-date must not be after its to-date.",
      ok: false,
      range: "expiryDate",
    });
  });

  it("refuses with the first range at fault, in the order the ranges are given", () => {
    expect(
      checkDateRanges(RANGES, {
        expiryDateFrom: "2025-01-02",
        expiryDateTo: "2025-01-01",
        issueDateFrom: "2024-01-02",
        issueDateTo: "2024-01-01",
      }),
    ).toMatchObject({ ok: false, range: "issueDate" });
  });
});
