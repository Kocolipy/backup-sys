import { describe, expect, it } from "vitest";

import { codeParameters } from "./code-parameters";

const COLOURS = ["red", "amber", "green"] as const;

describe("codeParameters", () => {
  it("carries each code chosen as the parameter repeated, in the set's own order", () => {
    expect(codeParameters("colour", COLOURS, ["green", "red"])).toEqual([
      "colour=red",
      "colour=green",
    ]);
  });

  it("carries nothing when no code is chosen", () => {
    expect(codeParameters("colour", COLOURS, [])).toEqual([]);
  });

  it("carries a code chosen twice once", () => {
    expect(codeParameters("colour", COLOURS, ["amber", "amber"])).toEqual(["colour=amber"]);
  });
});
