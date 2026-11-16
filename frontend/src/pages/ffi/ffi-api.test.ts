import { describe, expect, it } from "vitest";

import { checkDateRanges } from "@/lib/date-range";
import { DecodeError } from "@/lib/decode";

import {
  countFfiFilters,
  decodeFfi,
  FFI_DATE_COLUMNS,
  FFI_HISTORY,
  NO_FFI_FILTERS,
  type FfiFilters,
} from "./ffi-api";

/** One FFI exactly as the backend serialises it, every field present. */
const IPPT = {
  id: "ffi-tan-wei-ming-2",
  type: "ippt",
  outcome: "request-waiver",
  issueDate: "2024-01-10",
  expiryDate: "2025-01-09",
  higherAuthority: "cnmo",
  submittingMc: "kranji-camp",
  status: "active",
  invalid: [],
};

/** {@link IPPT} as the page reads it. */
const IPPT_READ = {
  id: { state: "present", value: "ffi-tan-wei-ming-2" },
  type: { state: "present", value: "ippt" },
  outcome: { state: "present", value: "request-waiver" },
  issueDate: { state: "present", value: "2024-01-10" },
  expiryDate: { state: "present", value: "2025-01-09" },
  higherAuthority: { state: "present", value: "cnmo" },
  submittingMc: { state: "present", value: "kranji-camp" },
  status: { state: "present", value: "active" },
};

/** Every field of an FFI's row the patient source sent. */
const SOURCE_FIELDS = [
  "id",
  "type",
  "outcome",
  "issueDate",
  "expiryDate",
  "higherAuthority",
  "submittingMc",
  "status",
] as const;

/** `decode(body)`'s refusal, read through the declaration as a `Response`. */
async function refused(body: unknown) {
  return Promise.resolve(FFI_HISTORY.decode(new Response(JSON.stringify(body)))).then(
    () => null,
    (error: unknown) => error,
  );
}

// An FFI is shown as received (ADR 0020): each field is read by the lenient
// field decoder (`readObject(…).sourceFields()`, lib/decode.test.ts), so its
// value never refuses the FFI — only its shape does.
describe("decodeFfi", () => {
  it("reads every field present as it came", () => {
    expect(decodeFfi(IPPT)).toStrictEqual(IPPT_READ);
  });

  it("reads a null field as missing, and one the row names invalid as invalid", () => {
    expect(
      decodeFfi({ ...IPPT, id: null, outcome: null, status: null, invalid: ["status"] }),
    ).toStrictEqual({
      ...IPPT_READ,
      id: { state: "missing" },
      outcome: { state: "missing" },
      status: { state: "invalid" },
    });
  });

  it.each(SOURCE_FIELDS)("reads %s invalid when the row names it", (field) => {
    expect(decodeFfi({ ...IPPT, [field]: null, invalid: [field] })).toStrictEqual({
      ...IPPT_READ,
      [field]: { state: "invalid" },
    });
  });

  // Which strings are calendar dates is `isCalendarDate`'s (lib/time.test.ts).
  it.each(["issueDate", "expiryDate"] as const)(
    "reads %s invalid when it is no calendar date",
    (field) => {
      expect(decodeFfi({ ...IPPT, [field]: "2025-02-30" })[field]).toStrictEqual({
        state: "invalid",
      });
    },
  );

  // A code is matched by the page, not the decoder: a label, another case or
  // a code of no set is kept as the source sent it, in each of the five sets.
  it.each([
    ["type", "IPPT FFI"],
    ["type", "swimming"],
    ["outcome", "FIT"],
    ["outcome", "waiver-granted"],
    ["higherAuthority", "comd_mmi"],
    ["submittingMc", "Nee Soon Camp MC"],
    ["submittingMc", "tengah-air-base"],
    ["status", "expired"],
  ])("reads %s outside its closed set as present, as it came: %s", (field, code) => {
    expect(decodeFfi({ ...IPPT, [field]: code })).toStrictEqual({
      ...IPPT_READ,
      [field]: { state: "present", value: code },
    });
  });

  it("reads an id outside FHIR's id grammar as present: ids are not checked", () => {
    expect(decodeFfi({ ...IPPT, id: "ffi tan/1" }).id).toStrictEqual({
      state: "present",
      value: "ffi tan/1",
    });
  });

  it.each(SOURCE_FIELDS)("refuses an FFI without %s, which the backend always sends", (field) => {
    expect(() =>
      decodeFfi(Object.fromEntries(Object.entries(IPPT).filter(([key]) => key !== field))),
    ).toThrow(new DecodeError(`Ffi.${field} is not a string or null`));
  });

  it("refuses an FFI without invalid", () => {
    expect(() =>
      decodeFfi(Object.fromEntries(Object.entries(IPPT).filter(([key]) => key !== "invalid"))),
    ).toThrow(new DecodeError("Ffi.invalid is not an array"));
  });

  it("refuses a field the FFI does not have", () => {
    expect(() => decodeFfi({ ...IPPT, dispensary: "kranji-camp" })).toThrow(
      new DecodeError("Ffi.dispensary is not expected"),
    );
  });

  it.each([
    ["an array", [IPPT]],
    ["null", null],
    ["a string", "ffi"],
  ])("refuses %s as an FFI", (_, value) => {
    expect(() => decodeFfi(value)).toThrow(new DecodeError("Ffi is not an object"));
  });
});

// How a date range is checked is `checkDateRanges`' (lib/date-range.test.ts):
// here, that the FFI History's two ranges are the ones checked, by name.
describe("the FFI History's date ranges", () => {
  it("declares the Issue Date and the Expiry Date, both calendar dates, in that order", () => {
    expect(FFI_DATE_COLUMNS).toEqual([
      { column: "issueDate", label: "Issue Date", precision: "date" },
      { column: "expiryDate", label: "Expiry Date", precision: "date" },
    ]);
  });

  it("checks its filters' ranges by the columns it declares", () => {
    expect(
      checkDateRanges(FFI_DATE_COLUMNS, {
        ...NO_FFI_FILTERS,
        expiryDateFrom: "2025-01-02",
        expiryDateTo: "2025-01-01",
      }),
    ).toEqual({
      column: "expiryDate",
      message: "The Expiry Date from-date must not be after its to-date.",
      ok: false,
    });
  });
});

describe("countFfiFilters", () => {
  it("counts none for no filter", () => {
    expect(countFfiFilters(NO_FFI_FILTERS)).toBe(0);
  });

  it.each([
    ["the FFI Types, however many", { types: ["ippt", "dental"] }],
    ["the Outcomes", { outcomes: ["fit"] }],
    ["an Issue Date from-date", { issueDateFrom: "2024-01-01" }],
    ["an Issue Date to-date", { issueDateTo: "2024-12-31" }],
    ["an Expiry Date from-date", { expiryDateFrom: "2025-01-01" }],
    ["an Expiry Date to-date", { expiryDateTo: "2025-12-31" }],
    ["the Higher Authorities", { higherAuthorities: ["camo", "cnmo", "dis"] }],
    ["the Submitting MCs", { submittingMcs: ["kranji-camp"] }],
    ["the Status", { status: "inactive" }],
  ] as const)("counts %s as one", (_, set) => {
    expect(countFfiFilters({ ...NO_FFI_FILTERS, ...set })).toBe(1);
  });

  it("counts every filter set", () => {
    expect(countFfiFilters(EVERY_FILTER)).toBe(9);
  });
});

/** Every filter set, the codes of each set given out of their set's order. */
const EVERY_FILTER: FfiFilters = {
  expiryDateFrom: "2025-01-01",
  expiryDateTo: "2025-12-31",
  higherAuthorities: ["dis", "camo"],
  issueDateFrom: "2024-01-01",
  issueDateTo: "2024-12-31",
  outcomes: ["unfit", "fit"],
  status: "active",
  submittingMcs: ["mandai-hill-camp", "nee-soon-camp"],
  types: ["vocational", "ippt"],
};

const FIRST_PAGE = { count: 5, startIndex: 1 };

describe("FFI_HISTORY", () => {
  it("reads a patient's FFI History a page at a time, by the patient's id", () => {
    expect(
      FFI_HISTORY.path("eq081-VQEgP8drUUqCWzHfw3", NO_FFI_FILTERS, null, {
        count: 5,
        startIndex: 6,
      }),
    ).toBe("/api/patient-ffi/eq081-VQEgP8drUUqCWzHfw3?startIndex=6&count=5");
  });

  // Each code is a parameter of its own, repeated, in its closed set's order.
  it("asks for every filter set, and the sort, before the page", () => {
    expect(
      FFI_HISTORY.path(
        "dev-patient-05",
        EVERY_FILTER,
        { by: "expiryDate", order: "descending" },
        FIRST_PAGE,
      ),
    ).toBe(
      "/api/patient-ffi/dev-patient-05?type=ippt&type=vocational&outcome=fit&outcome=unfit" +
        "&issueDateFrom=2024-01-01&issueDateTo=2024-12-31" +
        "&expiryDateFrom=2025-01-01&expiryDateTo=2025-12-31" +
        "&higherAuthority=camo&higherAuthority=dis" +
        "&submittingMc=nee-soon-camp&submittingMc=mandai-hill-camp&status=active" +
        "&sortBy=expiryDate&sortOrder=descending&startIndex=1&count=5",
    );
  });

  it.each([
    ["an FFI Type", { types: ["cat-a-ocs-scs"] }, "type=cat-a-ocs-scs"],
    ["an Outcome", { outcomes: ["request-waiver"] }, "outcome=request-waiver"],
    ["an Issue Date from-date", { issueDateFrom: "2024-01-01" }, "issueDateFrom=2024-01-01"],
    ["an Issue Date to-date", { issueDateTo: "2024-12-31" }, "issueDateTo=2024-12-31"],
    ["an Expiry Date from-date", { expiryDateFrom: "2025-01-01" }, "expiryDateFrom=2025-01-01"],
    ["an Expiry Date to-date", { expiryDateTo: "2025-12-31" }, "expiryDateTo=2025-12-31"],
    ["a Higher Authority", { higherAuthorities: ["comd-mmi"] }, "higherAuthority=comd-mmi"],
    ["a Submitting MC", { submittingMcs: ["hendon-camp"] }, "submittingMc=hendon-camp"],
    ["the Status Active", { status: "active" }, "status=active"],
    ["the Status Inactive", { status: "inactive" }, "status=inactive"],
  ] as const)("asks for %s alone when it is the one filter set", (_, set, asked) => {
    expect(FFI_HISTORY.path("p1", { ...NO_FFI_FILTERS, ...set }, null, FIRST_PAGE)).toBe(
      `/api/patient-ffi/p1?${asked}&startIndex=1&count=5`,
    );
  });

  it.each([
    ["issueDate", "ascending"],
    ["issueDate", "descending"],
    ["expiryDate", "ascending"],
  ] as const)("asks for the sort by %s, %s", (by, order) => {
    expect(FFI_HISTORY.path("p1", NO_FFI_FILTERS, { by, order }, FIRST_PAGE)).toBe(
      `/api/patient-ffi/p1?sortBy=${by}&sortOrder=${order}&startIndex=1&count=5`,
    );
  });

  it("reads with patient:read, a 404 being no patient rather than a failure", () => {
    expect([FFI_HISTORY.permission, FFI_HISTORY.absentOnNotFound]).toEqual(["patient:read", true]);
  });

  it("decodes a page of FFIs", async () => {
    await expect(
      FFI_HISTORY.decode(
        new Response(JSON.stringify({ ffis: [IPPT], startIndex: 1, totalResults: 4 })),
      ),
    ).resolves.toStrictEqual({ rows: [IPPT_READ], startIndex: 1, totalResults: 4 });
  });

  it("refuses a page holding an FFI whose shape it cannot read", async () => {
    expect(
      await refused({ ffis: [{ ...IPPT, invalid: "status" }], startIndex: 1, totalResults: 1 }),
    ).toStrictEqual(new DecodeError("Ffi.invalid is not an array"));
  });

  it("refuses a page holding a field it does not know", async () => {
    expect(
      await refused({ ffis: [], startIndex: 1, totalResults: 0, nextPage: null }),
    ).toStrictEqual(new DecodeError("FfiHistoryPage.nextPage is not expected"));
  });
});
