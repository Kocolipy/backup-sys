import { describe, expect, it } from "vitest";

import { sortParameters } from "./sorting";

describe("sortParameters", () => {
  it.each([
    ["ascending", "sortBy=displayName", "sortOrder=ascending"],
    ["descending", "sortBy=displayName", "sortOrder=descending"],
  ] as const)("carries a sort %s as its column and its order", (order, sortBy, sortOrder) => {
    expect(sortParameters({ by: "displayName", order })).toEqual([sortBy, sortOrder]);
  });

  it("carries nothing for the default order", () => {
    expect(sortParameters(null)).toEqual([]);
  });

  it("encodes the column as one query value", () => {
    expect(sortParameters({ by: "a&b=c d", order: "ascending" })).toEqual([
      "sortBy=a%26b%3Dc%20d",
      "sortOrder=ascending",
    ]);
  });
});
