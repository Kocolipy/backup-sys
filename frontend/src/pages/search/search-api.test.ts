import { describe, expect, it } from "vitest";

import { DecodeError } from "@/lib/decode";
import { describeDecoder, STRING } from "@/lib/decode.testHelpers";

import {
  decodePatientMatch,
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

describe("PATIENT_SEARCH", () => {
  it("reads a page of five for its query, encoded, under patient:read", () => {
    expect({
      pageSize: PATIENT_SEARCH_PAGE_SIZE,
      path: PATIENT_SEARCH.path("Tan & Co", { count: 5, startIndex: 6 }),
      permission: PATIENT_SEARCH.permission,
    }).toEqual({
      pageSize: 5,
      path: "/api/patient-search?q=Tan%20%26%20Co&startIndex=6&count=5",
      permission: "patient:read",
    });
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
