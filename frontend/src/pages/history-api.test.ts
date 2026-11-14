import { describe, expect, it } from "vitest";

import { checkDateRanges } from "@/lib/date-range";

import {
  countHistoryDates,
  historyDateParameters,
  ISSUE_AND_EXPIRY_DATES,
  NO_ISSUE_AND_EXPIRY_DATES,
} from "./history-api";

// Each is read over the FFI and NUMC Histories' two ranges; the ARMC History's
// are its own (armc-api.test.ts).

// How a range is checked is `checkDateRanges`' (lib/date-range.test.ts):
// here, that the FFI and NUMC Histories' two ranges are the ones checked, by name.
describe("the FFI and NUMC Histories' date ranges", () => {
  it("passes no date at all", () => {
    expect(checkDateRanges(ISSUE_AND_EXPIRY_DATES, NO_ISSUE_AND_EXPIRY_DATES)).toEqual({
      ok: true,
    });
  });

  it.each([
    ["issueDate", "Issue Date"],
    ["expiryDate", "Expiry Date"],
  ] as const)("refuses a %s from-date after its to-date, naming the range", (range, label) => {
    expect(
      checkDateRanges(ISSUE_AND_EXPIRY_DATES, {
        ...NO_ISSUE_AND_EXPIRY_DATES,
        [`${range}From`]: "2024-01-02",
        [`${range}To`]: "2024-01-01",
      }),
    ).toEqual({
      message: `The ${label} from-date must not be after its to-date.`,
      ok: false,
      range,
    });
  });

  it.each([
    ["issueDateTo", "issueDate", "Issue Date"],
    ["expiryDateFrom", "expiryDate", "Expiry Date"],
  ] as const)(
    "refuses a %s the calendar does not have, naming the range",
    (field, range, label) => {
      expect(
        checkDateRanges(ISSUE_AND_EXPIRY_DATES, {
          ...NO_ISSUE_AND_EXPIRY_DATES,
          [field]: "2024-02-30",
        }),
      ).toEqual({
        message: `Enter the ${label} dates as real dates.`,
        ok: false,
        range,
      });
    },
  );

  it("passes a range of one day on each date", () => {
    expect(
      checkDateRanges(ISSUE_AND_EXPIRY_DATES, {
        expiryDateFrom: "2025-01-09",
        expiryDateTo: "2025-01-09",
        issueDateFrom: "2024-01-10",
        issueDateTo: "2024-01-10",
      }),
    ).toEqual({ ok: true });
  });
});

describe("countHistoryDates", () => {
  it("counts none for no date", () => {
    expect(countHistoryDates(ISSUE_AND_EXPIRY_DATES, NO_ISSUE_AND_EXPIRY_DATES)).toBe(0);
  });

  it.each([
    ["an Issue Date from-date", { issueDateFrom: "2024-01-01" }],
    ["an Issue Date to-date", { issueDateTo: "2024-12-31" }],
    ["an Expiry Date from-date", { expiryDateFrom: "2025-01-01" }],
    ["an Expiry Date to-date", { expiryDateTo: "2025-12-31" }],
  ] as const)("counts %s as one", (_, set) => {
    expect(
      countHistoryDates(ISSUE_AND_EXPIRY_DATES, { ...NO_ISSUE_AND_EXPIRY_DATES, ...set }),
    ).toBe(1);
  });

  it("counts each end of both ranges", () => {
    expect(
      countHistoryDates(ISSUE_AND_EXPIRY_DATES, {
        expiryDateFrom: "2025-01-01",
        expiryDateTo: "2025-12-31",
        issueDateFrom: "2024-01-01",
        issueDateTo: "2024-12-31",
      }),
    ).toBe(4);
  });
});

describe("historyDateParameters", () => {
  it("sends no parameter for no date", () => {
    expect(historyDateParameters(ISSUE_AND_EXPIRY_DATES, NO_ISSUE_AND_EXPIRY_DATES)).toEqual([]);
  });

  it("sends each end set as its own parameter, the Issue Date's first", () => {
    expect(
      historyDateParameters(ISSUE_AND_EXPIRY_DATES, {
        expiryDateFrom: "",
        expiryDateTo: "2025-12-31",
        issueDateFrom: "2024-01-01",
        issueDateTo: "",
      }),
    ).toEqual(["issueDateFrom=2024-01-01", "expiryDateTo=2025-12-31"]);
  });
});
