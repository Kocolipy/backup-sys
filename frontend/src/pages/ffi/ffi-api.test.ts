import { describe, expect, it } from "vitest";

import { DecodeError } from "@/lib/decode";
import { without } from "@/lib/decode.testHelpers";

import { decodeFfi, FFI_HISTORY, FFI_HISTORY_PAGE_SIZE } from "./ffi-api";

/** One FFI exactly as the backend serialises it. */
const IPPT = {
  id: "ffi-tan-wei-ming-2",
  type: "ippt",
  outcome: "request-waiver",
  issueDate: "2024-01-10",
  expiryDate: "2025-01-09",
  higherAuthority: "cnmo",
  submittingMc: "kranji-camp",
  status: "active",
};

/** `decode(body)`'s refusal, read through the declaration as a `Response`. */
async function refused(body: unknown) {
  return Promise.resolve(FFI_HISTORY.decode(new Response(JSON.stringify(body)))).then(
    () => null,
    (error: unknown) => error,
  );
}

describe("decodeFfi", () => {
  it("decodes an FFI to the same value", () => {
    expect(decodeFfi(IPPT)).toStrictEqual(IPPT);
  });

  // Every field is required: the backend sends each one, and an FFI missing
  // one is no FFI the page can show. Each is read as a string first.
  it.each(Object.keys(IPPT))("refuses an FFI missing %s", (field) => {
    expect(() => decodeFfi(without(IPPT, field))).toThrow(
      new DecodeError(`Ffi.${field} is not a string`),
    );
  });

  it.each(Object.keys(IPPT))("refuses a wrong-typed %s", (field) => {
    expect(() => decodeFfi({ ...IPPT, [field]: 7 })).toThrow(
      new DecodeError(`Ffi.${field} is not a string`),
    );
  });

  // A code is matched exactly, so a label, another case or another spelling
  // is outside its set, and refused rather than passed through.
  it.each([
    [
      "type",
      "IPPT FFI",
      "overseas-exercises | cat-a-ocs-scs | ippt | dental | ord-release-from-service | vocational",
    ],
    ["outcome", "FIT", "fit | request-waiver | unfit"],
    ["higherAuthority", "comd_mmi", "camo | carmo | cnmo | comd-mmi | dis"],
    [
      "submittingMc",
      "Nee Soon Camp MC",
      "nee-soon-camp | kranji-camp | hendon-camp | mandai-hill-camp",
    ],
    ["status", "expired", "active | inactive"],
  ])("refuses an unknown %s code, %s", (field, code, set) => {
    expect(() => decodeFfi({ ...IPPT, [field]: code })).toThrow(
      new DecodeError(`Ffi.${field} is not ${set}`),
    );
  });

  it.each([
    ["issueDate", "2024-01"],
    ["issueDate", "10 Jan 2024"],
    ["expiryDate", "2025-02-30"],
    ["expiryDate", "2025-01-09T00:00:00Z"],
  ])("refuses a %s of %s, which is no calendar date", (field, date) => {
    expect(() => decodeFfi({ ...IPPT, [field]: date })).toThrow(
      new DecodeError(`Ffi.${field} is not a date`),
    );
  });

  it("refuses a field it does not know, rather than drop it", () => {
    expect(() => decodeFfi({ ...IPPT, expired: true })).toThrow(
      new DecodeError("Ffi.expired is not expected"),
    );
  });

  it.each([
    ["an array", [IPPT]],
    ["null", null],
    ["a string", "ffi"],
  ])("refuses %s", (_, value) => {
    expect(() => decodeFfi(value)).toThrow(new DecodeError("Ffi is not an object"));
  });
});

describe("FFI_HISTORY", () => {
  it("reads a patient's FFI History a page at a time, by the patient's id", () => {
    expect(FFI_HISTORY.path("eq081-VQEgP8drUUqCWzHfw3", { count: 5, startIndex: 6 })).toBe(
      "/api/patient-ffi/eq081-VQEgP8drUUqCWzHfw3?startIndex=6&count=5",
    );
  });

  it("reads with patient:read, a 404 being no patient rather than a failure", () => {
    expect([FFI_HISTORY.permission, FFI_HISTORY.absentOnNotFound]).toEqual(["patient:read", true]);
  });

  it("asks for five FFIs a page", () => {
    expect(FFI_HISTORY_PAGE_SIZE).toBe(5);
  });

  it("decodes a page of FFIs", async () => {
    await expect(
      FFI_HISTORY.decode(
        new Response(JSON.stringify({ ffis: [IPPT], startIndex: 1, totalResults: 4 })),
      ),
    ).resolves.toStrictEqual({ rows: [IPPT], startIndex: 1, totalResults: 4 });
  });

  it("refuses a page holding an FFI it cannot read", async () => {
    expect(
      await refused({ ffis: [{ ...IPPT, status: "lapsed" }], startIndex: 1, totalResults: 1 }),
    ).toStrictEqual(new DecodeError("Ffi.status is not active | inactive"));
  });

  it("refuses a page holding a field it does not know", async () => {
    expect(
      await refused({ ffis: [], startIndex: 1, totalResults: 0, nextPage: null }),
    ).toStrictEqual(new DecodeError("FfiHistoryPage.nextPage is not expected"));
  });
});
