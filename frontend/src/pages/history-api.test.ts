import { describe, expect, it } from "vitest";

import { countHistoryDates, historyDateParameters, noHistoryDates } from "./history-api";

// Each is read over a history declaring one column of each precision; each
// history's own columns are its own API module's (ffi-api.test.ts,
// numc-api.test.ts, armc-api.test.ts), and how a range is checked is
// `checkDateRanges`' (lib/date-range.test.ts).
const COLUMNS = [
  { column: "shipped", label: "Shipped", precision: "date" },
  { column: "takenAt", label: "Taken At", precision: "date-time" },
] as const;

const OPEN = { shippedFrom: "", shippedTo: "", takenAtFrom: "", takenAtTo: "" };

describe("noHistoryDates", () => {
  it("leaves both ends of every declared column open", () => {
    expect(noHistoryDates(COLUMNS)).toEqual(OPEN);
  });
});

describe("countHistoryDates", () => {
  it("counts none for no date", () => {
    expect(countHistoryDates(COLUMNS, OPEN)).toBe(0);
  });

  it.each([
    ["a date column's from-end", { shippedFrom: "2024-01-01" }],
    ["a date column's to-end", { shippedTo: "2024-12-31" }],
    ["a date-time column's from-end", { takenAtFrom: "2025-01-01T09:30" }],
    ["a date-time column's to-end", { takenAtTo: "2025-12-31T17:00" }],
  ] as const)("counts %s as one", (_, set) => {
    expect(countHistoryDates(COLUMNS, { ...OPEN, ...set })).toBe(1);
  });

  it("counts each end of every range", () => {
    expect(
      countHistoryDates(COLUMNS, {
        shippedFrom: "2024-01-01",
        shippedTo: "2024-12-31",
        takenAtFrom: "2025-01-01T09:30",
        takenAtTo: "2025-12-31T17:00",
      }),
    ).toBe(4);
  });
});

describe("historyDateParameters", () => {
  it("sends no parameter for no date", () => {
    expect(historyDateParameters(COLUMNS, OPEN)).toEqual([]);
  });

  it("sends each end set as its own parameter, in the order the columns are declared", () => {
    expect(
      historyDateParameters(COLUMNS, {
        shippedFrom: "",
        shippedTo: "2025-12-31",
        takenAtFrom: "2024-01-01T09:30",
        takenAtTo: "",
      }),
    ).toEqual(["shippedTo=2025-12-31", "takenAtFrom=2024-01-01T09:30"]);
  });

  it("sends a date-time end as its Singapore minute, with no offset", () => {
    expect(
      historyDateParameters(COLUMNS, {
        ...OPEN,
        takenAtFrom: "2024-01-01T00:00",
        takenAtTo: "2024-01-01T23:59",
      }),
    ).toEqual(["takenAtFrom=2024-01-01T00:00", "takenAtTo=2024-01-01T23:59"]);
  });
});
