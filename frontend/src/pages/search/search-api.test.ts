import { describe, expect, it } from "vitest";

import { DecodeError } from "@/lib/decode";
import { without } from "@/lib/decode.testHelpers";

import {
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
  invalid: [],
};

/** {@link TAN_HUI_MIN} as the page reads it. */
const TAN_HUI_MIN_READ = {
  id: "dev-patient-05",
  name: { state: "present", value: "Tan Hui Min" },
  nric: { state: "present", value: "S8512345A" },
  gender: { state: "present", value: "female" },
  birthDate: { state: "present", value: "1985-02-14" },
  lastVisit: { state: "present", value: "2026-11-02" },
};

/** A match's patient-source fields, each read as received. */
const SOURCE_FIELDS = ["name", "nric", "gender", "birthDate", "lastVisit"] as const;

/** An entry that is no Patient, as the backend answers it: no id, every field invalid. */
const NO_PATIENT = {
  id: null,
  name: null,
  nric: null,
  gender: null,
  birthDate: null,
  lastVisit: null,
  invalid: ["name", "nric", "gender", "birthDate", "lastVisit"],
};

// A match is shown as received (ADR 0020): each of its fields but the id is
// read by the lenient field decoder, so its value never refuses the match. The
// row's shape is strict: each key is always there, and the id a string, or
// null for an entry that is no Patient.
describe("decodePatientMatch", () => {
  it("reads a match, each field as received", () => {
    expect(decodePatientMatch(TAN_HUI_MIN)).toStrictEqual(TAN_HUI_MIN_READ);
  });

  it("drops a field the match does not have", () => {
    expect(decodePatientMatch({ ...TAN_HUI_MIN, extra: "ignored" })).toStrictEqual(
      TAN_HUI_MIN_READ,
    );
  });

  it("reads an entry that is no Patient with no id and every field invalid", () => {
    expect(decodePatientMatch(NO_PATIENT)).toStrictEqual({
      id: null,
      name: { state: "invalid" },
      nric: { state: "invalid" },
      gender: { state: "invalid" },
      birthDate: { state: "invalid" },
      lastVisit: { state: "invalid" },
    });
  });

  it.each(SOURCE_FIELDS)("reads a %s the backend sends null as missing", (field) => {
    expect(decodePatientMatch({ ...TAN_HUI_MIN, [field]: null })[field]).toStrictEqual({
      state: "missing",
    });
  });

  it.each(SOURCE_FIELDS)("reads a %s the row names invalid as invalid", (field) => {
    expect(
      decodePatientMatch({ ...TAN_HUI_MIN, [field]: null, invalid: [field] })[field],
    ).toStrictEqual({ state: "invalid" });
  });

  it("reads a gender outside the four codes as present, its raw code", () => {
    expect(decodePatientMatch({ ...TAN_HUI_MIN, gender: "F" }).gender).toStrictEqual({
      state: "present",
      value: "F",
    });
  });

  it.each(["14 Feb 1985", "1985-02-30", 19850214])(
    "reads a date of birth that is no calendar date, %s, as invalid rather than refuse the match",
    (birthDate) => {
      expect(decodePatientMatch({ ...TAN_HUI_MIN, birthDate }).birthDate).toStrictEqual({
        state: "invalid",
      });
    },
  );

  it.each(["name", "nric", "gender"] as const)(
    "reads a %s of the wrong type as invalid rather than refuse the match",
    (field) => {
      expect(decodePatientMatch({ ...TAN_HUI_MIN, [field]: 7 })[field]).toStrictEqual({
        state: "invalid",
      });
    },
  );

  it.each(["name", "nric", "gender", "birthDate"])(
    "refuses a match without %s, which the backend always sends",
    (field) => {
      expect(() => decodePatientMatch(without(TAN_HUI_MIN, field))).toThrow(
        new DecodeError(`PatientMatch.${field} is not a string or null`),
      );
    },
  );

  it("refuses a match without id, which the backend always sends", () => {
    expect(() => decodePatientMatch(without(TAN_HUI_MIN, "id"))).toThrow(
      new DecodeError("PatientMatch.id is not a string"),
    );
  });

  it("refuses a match whose id is of the wrong type", () => {
    expect(() => decodePatientMatch({ ...TAN_HUI_MIN, id: 7 })).toThrow(
      new DecodeError("PatientMatch.id is not a string"),
    );
  });

  it("reads a Patient with no Last visit as missing", () => {
    expect(decodePatientMatch({ ...TAN_HUI_MIN, lastVisit: null }).lastVisit).toStrictEqual({
      state: "missing",
    });
  });

  it("reads a Last visit the row names invalid as invalid", () => {
    expect(
      decodePatientMatch({ ...TAN_HUI_MIN, lastVisit: null, invalid: ["lastVisit"] }).lastVisit,
    ).toStrictEqual({ state: "invalid" });
  });

  it.each(["2026-11", "2026-02-30", 20261102])(
    "reads a Last visit that is no calendar date, %s, as invalid rather than refuse the match",
    (lastVisit) => {
      expect(decodePatientMatch({ ...TAN_HUI_MIN, lastVisit }).lastVisit).toStrictEqual({
        state: "invalid",
      });
    },
  );

  it("refuses a match without lastVisit, which the backend always sends", () => {
    expect(() => decodePatientMatch(without(TAN_HUI_MIN, "lastVisit"))).toThrow(
      new DecodeError("PatientMatch.lastVisit is not a string or null"),
    );
  });

  it("refuses a match without invalid", () => {
    expect(() => decodePatientMatch(without(TAN_HUI_MIN, "invalid"))).toThrow(
      new DecodeError("PatientMatch.invalid is not an array"),
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

describe("countPatientFilters", () => {
  it("counts none for no filter", () => {
    expect(countPatientFilters(NO_PATIENT_FILTERS)).toBe(0);
  });

  it.each(["male", "female"] as const)("counts the gender %s as one", (gender) => {
    expect(countPatientFilters({ gender })).toBe(1);
  });
});

describe("PATIENT_SEARCH", () => {
  it("reads a page of five for its query, encoded, under patient:read", () => {
    expect({
      pageSize: PATIENT_SEARCH_PAGE_SIZE,
      path: PATIENT_SEARCH.path("Tan & Co", NO_PATIENT_FILTERS, { count: 5, startIndex: 6 }),
      permission: PATIENT_SEARCH.permission,
    }).toEqual({
      pageSize: 5,
      path: "/api/patient-search?q=Tan%20%26%20Co&startIndex=6&count=5",
      permission: "patient:read",
    });
  });

  it.each(["male", "female"] as const)("asks for the gender %s before the page", (gender) => {
    expect(PATIENT_SEARCH.path("tan", { gender }, { count: 5, startIndex: 1 })).toBe(
      `/api/patient-search?q=tan&gender=${gender}&startIndex=1&count=5`,
    );
  });

  it("reads its answer as one page of matching Patients, and how many of it were dropped", async () => {
    await expect(
      PATIENT_SEARCH.decode(
        Response.json({
          droppedResults: 2,
          patients: [TAN_HUI_MIN, NO_PATIENT],
          startIndex: 1,
          totalResults: 7,
        }),
      ),
    ).resolves.toStrictEqual({
      droppedResults: 2,
      rows: [TAN_HUI_MIN_READ, decodePatientMatch(NO_PATIENT)],
      startIndex: 1,
      totalResults: 7,
    });
  });

  it("refuses a page without droppedResults, which the backend always sends", async () => {
    await expect(
      PATIENT_SEARCH.decode(
        Response.json({ patients: [TAN_HUI_MIN], startIndex: 1, totalResults: 7 }),
      ),
    ).rejects.toThrow(new DecodeError("PatientSearchPage.droppedResults is not an integer"));
  });

  it("refuses a page holding a field it does not know", async () => {
    await expect(
      PATIENT_SEARCH.decode(
        Response.json({
          droppedResults: 0,
          extra: 1,
          patients: [],
          startIndex: 1,
          totalResults: 0,
        }),
      ),
    ).rejects.toThrow(new DecodeError("PatientSearchPage.extra is not expected"));
  });

  it("refuses an answer that is a bare list of Patients rather than a page", async () => {
    await expect(PATIENT_SEARCH.decode(Response.json([TAN_HUI_MIN]))).rejects.toThrow(
      new DecodeError("PatientSearchPage is not an object"),
    );
  });
});
