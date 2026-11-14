import { describe, expect, it } from "vitest";

import { DecodeError } from "@/lib/decode";
import { describeDecoder, STRING } from "@/lib/decode.testHelpers";

import {
  countFfiFilters,
  decodeFfi,
  FFI_HISTORY,
  NO_FFI_FILTERS,
  type FfiFilters,
} from "./ffi-api";

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

// Every field is required, and each is read as a string first. A code is
// matched exactly, so a label, another case or another spelling is outside its
// set; which strings are calendar dates is `isCalendarDate`'s (lib/time.test.ts).
describeDecoder(
  "Ffi",
  decodeFfi,
  IPPT,
  {
    id: STRING,
    type: [
      "IPPT FFI",
      "overseas-exercises | cat-a-ocs-scs | ippt | dental | ord-release-from-service | vocational",
      "a string",
    ],
    outcome: ["FIT", "fit | request-waiver | unfit", "a string"],
    issueDate: ["10 Jan 2024", "a date", "a string"],
    expiryDate: ["2025-02-30", "a date", "a string"],
    higherAuthority: ["comd_mmi", "camo | carmo | cnmo | comd-mmi | dis", "a string"],
    submittingMc: [
      "Nee Soon Camp MC",
      "nee-soon-camp | kranji-camp | hendon-camp | mandai-hill-camp | khatib-camp",
      "a string",
    ],
    status: ["expired", "active | inactive", "a string"],
  },
  { strict: true },
);

// Which dates may be sent is `checkDateRanges`' (history-api.test.ts).
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
