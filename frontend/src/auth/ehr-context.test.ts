import { afterEach, describe, expect, it, vi } from "vitest";

import { clearEhrContext, ehrContext, setEhrContext, subscribeEhrContext } from "./ehr-context";

// The subject is the tab itself: its sessionStorage, jsdom's own.

const TAN_WEI_MING = "eq081-VQEgP8drUUqCWzHfw3";

afterEach(() => {
  clearEhrContext();
});

describe("the EHR context", () => {
  it("keeps the patient and encounter it is set to, for the tab", () => {
    setEhrContext({ encounter: "enc.42-A", patient: TAN_WEI_MING });

    expect(ehrContext()).toEqual({ encounter: "enc.42-A", patient: TAN_WEI_MING });
  });

  it("keeps whichever of the two it is set to", () => {
    setEhrContext({ encounter: "enc-1" });

    expect(ehrContext()).toEqual({ encounter: "enc-1" });
  });

  it("is replaced by every new context, not merged with the one before", () => {
    setEhrContext({ encounter: "enc-1", patient: TAN_WEI_MING });
    setEhrContext({ patient: "other-patient" });

    expect(ehrContext()).toEqual({ patient: "other-patient" });
  });

  it("is kept in the tab's storage, which a reload keeps", () => {
    setEhrContext({ patient: TAN_WEI_MING });

    expect(window.sessionStorage.getItem("ehr-context")).toBe(`{"patient":"${TAN_WEI_MING}"}`);
  });

  it.each([
    ["a slash", "a/b"],
    ["a space", "a b"],
    ["markup", "<b>x</b>"],
    ["65 characters", "a".repeat(65)],
    ["nothing", ""],
    ["an underscore", "a_b"],
  ])("leaves out a value with %s, keeping the other", (_name, value) => {
    setEhrContext({ encounter: "enc-1", patient: value });

    expect(ehrContext()).toEqual({ encounter: "enc-1" });
  });

  it("keeps a value of exactly 64 characters", () => {
    setEhrContext({ patient: "a".repeat(64) });

    expect(ehrContext()).toEqual({ patient: "a".repeat(64) });
  });

  it("is absent in a tab nothing ever set it in", () => {
    expect(ehrContext()).toBeNull();
  });

  it("is absent once cleared", () => {
    setEhrContext({ patient: TAN_WEI_MING });

    clearEhrContext();

    expect(ehrContext()).toBeNull();
  });

  it("is present, naming nothing, once set to nothing", () => {
    setEhrContext({});

    expect(ehrContext()).toEqual({});
  });

  it("is the same value on every read until it changes", () => {
    setEhrContext({ patient: TAN_WEI_MING });

    expect(ehrContext()).toBe(ehrContext());
  });

  it("tells a subscriber when it is set", () => {
    const listener = vi.fn();
    const unsubscribe = subscribeEhrContext(listener);

    setEhrContext({ patient: TAN_WEI_MING });
    unsubscribe();

    expect(listener).toHaveBeenCalledOnce();
  });

  it("tells a subscriber when it is cleared", () => {
    setEhrContext({ patient: TAN_WEI_MING });
    const listener = vi.fn();
    const unsubscribe = subscribeEhrContext(listener);

    clearEhrContext();
    unsubscribe();

    expect(listener).toHaveBeenCalledOnce();
  });

  it("tells a subscriber nothing once it has unsubscribed", () => {
    const listener = vi.fn();
    subscribeEhrContext(listener)();

    setEhrContext({ patient: TAN_WEI_MING });
    clearEhrContext();

    expect(listener).not.toHaveBeenCalled();
  });

  // The tab's storage is the page's to write, so what is read back is decoded
  // rather than trusted.
  it.each([
    ["not JSON", "{patient", {}],
    ["not an object", "42", {}],
    ["null", "null", {}],
    [
      "a value outside the grammar",
      '{"patient":"a/b","encounter":"enc-1"}',
      { encounter: "enc-1" },
    ],
    ["a value that is no string", '{"patient":7,"encounter":"enc-1"}', { encounter: "enc-1" }],
  ])("reads only what the grammar allows from storage holding %s", (_name, stored, expected) => {
    window.sessionStorage.setItem("ehr-context", stored);

    expect(ehrContext()).toEqual(expected);
  });
});
