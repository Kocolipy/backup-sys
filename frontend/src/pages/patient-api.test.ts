import type { Patient } from "fhir/r5";
import { describe, expect, expectTypeOf, it } from "vitest";

import type { PatientGender } from "@/lib/patient-gender";

import { PATIENT } from "./patient-api";
import { TAN_WEI_MING } from "./patient.testHelpers";

describe("PATIENT", () => {
  it("reads a patient at /api/patient/{id}, under patient:read, a 404 being absent", () => {
    expect({
      absentOnNotFound: PATIENT.absentOnNotFound,
      path: PATIENT.path("00000000-0000-4000-8000-0000000000aa"),
      permission: PATIENT.permission,
    }).toEqual({
      absentOnNotFound: true,
      path: "/api/patient/00000000-0000-4000-8000-0000000000aa",
      permission: "patient:read",
    });
  });

  it("reads exactly fhir5's administrative gender codes", () => {
    expectTypeOf<PatientGender>().toEqualTypeOf<NonNullable<Patient["gender"]>>();
  });
});

describe("PATIENT's answer", () => {
  it("is read down to the id, name text, NRIC, gender and date of birth", async () => {
    await expect(PATIENT.decode(Response.json(TAN_WEI_MING))).resolves.toEqual({
      birthDate: "1985-03-12",
      gender: "male",
      id: "eq081-VQEgP8drUUqCWzHfw3",
      name: "Tan Wei Ming",
      nric: "S1234567A",
    });
  });

  it("reads a name with no family or given part by its text alone", async () => {
    const mononym = { ...TAN_WEI_MING, name: [{ text: "Suriani", use: "official" }] };

    await expect(PATIENT.decode(Response.json(mononym))).resolves.toEqual(
      expect.objectContaining({ name: "Suriani" }),
    );
  });

  it("reads the NRIC from the identifier under the NRIC system, past any other", async () => {
    const twoIdentifiers = {
      ...TAN_WEI_MING,
      identifier: [{ system: "urn:example:other", value: "X-1" }, ...TAN_WEI_MING.identifier],
    };

    await expect(PATIENT.decode(Response.json(twoIdentifiers))).resolves.toEqual(
      expect.objectContaining({ nric: "S1234567A" }),
    );
  });

  it.each([
    [
      "another resource type",
      { ...TAN_WEI_MING, resourceType: "Practitioner" },
      "Patient.resourceType is not Patient",
    ],
    ["no id", { ...TAN_WEI_MING, id: undefined }, "Patient.id is not a string"],
    ["no name", { ...TAN_WEI_MING, name: [] }, "Patient.name has no entry"],
    [
      "a name with no text",
      { ...TAN_WEI_MING, name: [{ use: "official" }] },
      "HumanName.text is not a string",
    ],
    [
      "no NRIC identifier",
      { ...TAN_WEI_MING, identifier: [{ system: "urn:example:other", value: "X-1" }] },
      "Patient.identifier has no NRIC",
    ],
    [
      "an unknown gender",
      { ...TAN_WEI_MING, gender: "M" },
      "Patient.gender is not male | female | other | unknown",
    ],
    [
      "a malformed birth date",
      { ...TAN_WEI_MING, birthDate: "12/03/1985" },
      "Patient.birthDate is not a date",
    ],
    [
      "an identifier that is not an object",
      { ...TAN_WEI_MING, identifier: ["S1234567A"] },
      "Identifier is not an object",
    ],
    ["a non-object body", [TAN_WEI_MING], "Patient is not an object"],
  ])("is refused with %s", async (_, body, message) => {
    await expect(PATIENT.decode(Response.json(body))).rejects.toThrow(
      expect.objectContaining({ message, name: "DecodeError" }),
    );
  });
});
