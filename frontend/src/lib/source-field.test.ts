import { describe, expect, it } from "vitest";

import { byCode, orElse, shown, type SourceField } from "./source-field";

const shout = (value: string) => value.toUpperCase();

describe("shown", () => {
  it("shows a present field's value as `show` renders it", () => {
    expect(shown({ state: "present", value: "fit" }, shout)).toBe("FIT");
  });

  it('shows a missing field as "—", the em dash of every empty cell', () => {
    expect(shown<string>({ state: "missing" }, shout)).toBe("—");
  });

  it('shows a field in the wrong format as "Error"', () => {
    expect(shown<string>({ state: "invalid" }, shout)).toBe("Error");
  });

  it("renders nothing of a field that is not present", () => {
    const fields: SourceField<string>[] = [{ state: "missing" }, { state: "invalid" }];
    const rendered: string[] = [];

    fields.forEach((field) =>
      shown(field, (value) => {
        rendered.push(value);
        return value;
      }),
    );

    expect(rendered).toEqual([]);
  });
});

describe("orElse", () => {
  it("gives a present field's value", () => {
    expect(orElse({ state: "present", value: "numc-1" }, 3)).toBe("numc-1");
  });

  it("gives the other value for a field missing or in the wrong format", () => {
    const fields: SourceField<string>[] = [{ state: "missing" }, { state: "invalid" }];

    expect(fields.map((field) => orElse(field, 3))).toEqual([3, 3]);
  });
});

describe("byCode", () => {
  const LABELS: Readonly<Record<"fit" | "unfit", string>> = { fit: "FIT", unfit: "UNFIT" };

  it("looks a known code up", () => {
    expect([byCode(LABELS, "fit", "?"), byCode(LABELS, "unfit", "?")]).toEqual(["FIT", "UNFIT"]);
  });

  it("falls back for a code outside the set, whatever its case", () => {
    expect([byCode(LABELS, "request-waiver", "raw"), byCode(LABELS, "FIT", "raw")]).toEqual([
      "raw",
      "raw",
    ]);
  });

  // A code is data from the patient source: one spelling an Object member is no member of the set.
  it.each(["constructor", "toString", "__proto__", "hasOwnProperty"])(
    "falls back for %s, which every object has but no set holds",
    (code) => {
      expect(byCode(LABELS, code, "raw")).toBe("raw");
    },
  );
});
