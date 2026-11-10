import { describe, expect, it } from "vitest";

import { DecodeError } from "@/lib/decode";
import { describeDecoder, STRING } from "@/lib/decode.testHelpers";

import {
  checkPatientFilters,
  countPatientFilters,
  decodePatientMatch,
  NO_PATIENT_FILTERS,
  parsePatientQuery,
  PATIENT_SEARCH,
  PATIENT_SEARCH_PAGE_SIZE,
} from "./search-api";

/** One matching Patient exactly as the backend serialises it, `null`s included. */
const TAN_HUI_MIN = {
  id: "dev-patient-05",
  name: "Tan Hui Min",
  nric: "S8512345A",
  gender: "female",
  birthDate: "1985-02-14",
  lastVisit: "2026-11-02",
};

// Every field, missing and wrong-typed. `lastVisit` is nullable: `null` is a
// value the backend sends for a Patient with no Last visit, never an omission.
describeDecoder("PatientMatch", decodePatientMatch, TAN_HUI_MIN, {
  id: STRING,
  name: STRING,
  nric: STRING,
  gender: ["f", "male | female | other | unknown", "a string"],
  birthDate: ["14 Feb 1985", "a date", "a string"],
  lastVisit: ["2026-11", "a date", "a string"],
});

describe("PatientMatch's dates", () => {
  it("reads a Patient with no Last visit as null", () => {
    expect(decodePatientMatch({ ...TAN_HUI_MIN, lastVisit: null }).lastVisit).toBeNull();
  });

  it("refuses a date the calendar does not have", () => {
    expect(() => decodePatientMatch({ ...TAN_HUI_MIN, birthDate: "1985-02-30" })).toThrow(
      new DecodeError("PatientMatch.birthDate is not a date"),
    );
  });
});

describe("parsePatientQuery", () => {
  it.each([
    ["a full NRIC", "S8512345A", "S8512345A"],
    ["a full NRIC in lower case", "s8512345a", "s8512345a"],
    ["a full NRIC of each prefix", "M1234567X", "M1234567X"],
    ["a name of three characters", "tan", "tan"],
    ["a name, trimmed", "  Tan Hui  ", "Tan Hui"],
    ["a partial NRIC, which is a name search", "S851", "S851"],
  ])("sends %s", (_, typed, sent) => {
    expect(parsePatientQuery(typed)).toEqual({ ok: true, query: sent });
  });

  it.each([
    ["nothing", ""],
    ["blanks", "   "],
    ["two characters", "ta"],
    ["two characters between blanks", "  ta  "],
    ["two characters, one beyond the basic plane", "t\u{1F600}"],
  ])("refuses %s, as a name too short to search by", (_, typed) => {
    expect(parsePatientQuery(typed)).toEqual({
      message: "Enter at least 3 characters to search by name.",
      ok: false,
    });
  });
});

describe("checkPatientFilters", () => {
  it("passes no filter at all", () => {
    expect(checkPatientFilters(NO_PATIENT_FILTERS)).toEqual({ ok: true });
  });

  it.each([
    ["both ends", "1980-01-01", "1989-12-31"],
    ["one day", "1980-01-01", "1980-01-01"],
    ["a from-date alone", "1980-01-01", ""],
    ["a to-date alone", "", "1989-12-31"],
  ])("passes a range with %s", (_, from, to) => {
    expect({
      birthDate: checkPatientFilters({
        ...NO_PATIENT_FILTERS,
        birthDateFrom: from,
        birthDateTo: to,
      }),
      lastVisit: checkPatientFilters({
        ...NO_PATIENT_FILTERS,
        lastVisitFrom: from,
        lastVisitTo: to,
      }),
    }).toEqual({ birthDate: { ok: true }, lastVisit: { ok: true } });
  });

  it.each([
    ["birthDate", "Date of Birth"],
    ["lastVisit", "Last Visit"],
  ] as const)("refuses a %s from-date after its to-date, naming the range", (range, label) => {
    expect(
      checkPatientFilters({
        ...NO_PATIENT_FILTERS,
        [`${range}From`]: "2026-10-02",
        [`${range}To`]: "2026-10-01",
      }),
    ).toEqual({
      message: `The ${label} from-date must not be after its to-date.`,
      ok: false,
      range,
    });
  });

  it.each([
    ["birthDateFrom", "birthDate", "Date of Birth"],
    ["birthDateTo", "birthDate", "Date of Birth"],
    ["lastVisitFrom", "lastVisit", "Last Visit"],
    ["lastVisitTo", "lastVisit", "Last Visit"],
  ] as const)(
    "refuses a %s the calendar does not have, naming the range",
    (field, range, label) => {
      expect(checkPatientFilters({ ...NO_PATIENT_FILTERS, [field]: "275760-02-30" })).toEqual({
        message: `Enter the ${label} dates as real dates.`,
        ok: false,
        range,
      });
    },
  );
});

describe("countPatientFilters", () => {
  it("counts none for no filter", () => {
    expect(countPatientFilters(NO_PATIENT_FILTERS)).toBe(0);
  });

  it.each([
    ["the gender", { gender: "male" }],
    ["a Date of Birth from-date", { birthDateFrom: "1980-01-01" }],
    ["a Date of Birth to-date", { birthDateTo: "1989-12-31" }],
    ["a Last Visit from-date", { lastVisitFrom: "2026-09-01" }],
    ["a Last Visit to-date", { lastVisitTo: "2026-10-31" }],
  ] as const)("counts %s as one", (_, set) => {
    expect(countPatientFilters({ ...NO_PATIENT_FILTERS, ...set })).toBe(1);
  });

  it("counts every filter set", () => {
    expect(
      countPatientFilters({
        birthDateFrom: "1980-01-01",
        birthDateTo: "1989-12-31",
        gender: "female",
        lastVisitFrom: "2026-09-01",
        lastVisitTo: "2026-10-31",
      }),
    ).toBe(5);
  });
});

describe("PATIENT_SEARCH", () => {
  it("reads a page of five for its query, encoded, under patient:read", () => {
    expect({
      pageSize: PATIENT_SEARCH_PAGE_SIZE,
      path: PATIENT_SEARCH.path("Tan & Co", NO_PATIENT_FILTERS, null, { count: 5, startIndex: 6 }),
      permission: PATIENT_SEARCH.permission,
    }).toEqual({
      pageSize: 5,
      path: "/api/patient-search?q=Tan%20%26%20Co&startIndex=6&count=5",
      permission: "patient:read",
    });
  });

  it("asks for every filter set, and the sort, before the page", () => {
    expect(
      PATIENT_SEARCH.path(
        "tan",
        {
          birthDateFrom: "1980-01-01",
          birthDateTo: "1999-12-31",
          gender: "female",
          lastVisitFrom: "2026-08-01",
          lastVisitTo: "2026-10-20",
        },
        { by: "birthDate", order: "descending" },
        { count: 5, startIndex: 1 },
      ),
    ).toBe(
      "/api/patient-search?q=tan&gender=female&birthDateFrom=1980-01-01&birthDateTo=1999-12-31" +
        "&lastVisitFrom=2026-08-01&lastVisitTo=2026-10-20&sortBy=birthDate&sortOrder=descending" +
        "&startIndex=1&count=5",
    );
  });

  it.each([
    ["the gender", { gender: "male" }, "gender=male"],
    ["a Date of Birth from-date", { birthDateFrom: "1980-01-01" }, "birthDateFrom=1980-01-01"],
    ["a Date of Birth to-date", { birthDateTo: "1989-12-31" }, "birthDateTo=1989-12-31"],
    ["a Last Visit from-date", { lastVisitFrom: "2026-09-01" }, "lastVisitFrom=2026-09-01"],
    ["a Last Visit to-date", { lastVisitTo: "2026-10-31" }, "lastVisitTo=2026-10-31"],
  ] as const)("asks for %s alone when it is the one filter set", (_, set, asked) => {
    expect(
      PATIENT_SEARCH.path("tan", { ...NO_PATIENT_FILTERS, ...set }, null, {
        count: 5,
        startIndex: 1,
      }),
    ).toBe(`/api/patient-search?q=tan&${asked}&startIndex=1&count=5`);
  });

  it.each([
    ["lastVisit", "ascending"],
    ["lastVisit", "descending"],
    ["birthDate", "ascending"],
  ] as const)("asks for the sort by %s, %s", (by, order) => {
    expect(
      PATIENT_SEARCH.path("tan", NO_PATIENT_FILTERS, { by, order }, { count: 5, startIndex: 1 }),
    ).toBe(`/api/patient-search?q=tan&sortBy=${by}&sortOrder=${order}&startIndex=1&count=5`);
  });

  it("reads its answer as one page of matching Patients", async () => {
    await expect(
      PATIENT_SEARCH.decode(
        Response.json({ patients: [TAN_HUI_MIN], startIndex: 1, totalResults: 7 }),
      ),
    ).resolves.toStrictEqual({ rows: [TAN_HUI_MIN], startIndex: 1, totalResults: 7 });
  });

  it("refuses an answer that is a bare list of Patients rather than a page", async () => {
    await expect(PATIENT_SEARCH.decode(Response.json([TAN_HUI_MIN]))).rejects.toThrow(
      new DecodeError("PatientSearchPage is not an object"),
    );
  });
});
