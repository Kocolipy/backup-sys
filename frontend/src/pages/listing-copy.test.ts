import { describe, expect, it } from "vitest";

import { listingCopyOf, recordsCounted } from "./listing-copy";

describe("listingCopyOf", () => {
  it.each([
    ["none", "No records found."],
    ["filters", "No records match the filters."],
    ["search", "No records match your search."],
    ["patient-search", "No records match your search."],
  ] as const)("a listing narrowed by %s says, when empty, %s", (narrowing, empty) => {
    expect(listingCopyOf(narrowing).empty).toBe(empty);
  });

  it.each([
    ["none", "Loading records…"],
    ["filters", "Loading records…"],
    ["search", "Loading records…"],
    ["patient-search", "Searching records…"],
  ] as const)("a listing narrowed by %s says, while loading, %s", (narrowing, loading) => {
    expect(listingCopyOf(narrowing).loading).toBe(loading);
  });
});

describe("recordsCounted", () => {
  it.each([
    [0, "0 records"],
    [1, "1 record"],
    [2, "2 records"],
    [45, "45 records"],
  ])("counts %i as %s", (total, counted) => {
    expect(recordsCounted(total)).toBe(counted);
  });
});
