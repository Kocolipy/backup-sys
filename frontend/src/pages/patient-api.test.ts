import type { Patient } from "fhir/r5";
import { describe, expect, expectTypeOf, it } from "vitest";

import type { PatientGender } from "@/lib/patient-gender";

import { MILITARY_HISTORY_EXTENSION, PATIENT } from "./patient-api";
import { TAN_WEI_MING, TAN_WEI_MING_BARE } from "./patient.testHelpers";

/** A relationship coded as the patient's Next of kin. */
const nextOfKinRole = {
  coding: [{ code: "N", system: "http://terminology.hl7.org/CodeSystem/v2-0131" }],
};

/** The Military history extension holding `extension`. */
const military = (extension: object[]) => [{ extension, url: MILITARY_HISTORY_EXTENSION }];

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
  it("is read down to the name text, NRIC, gender and date of birth when it has nothing else", async () => {
    await expect(PATIENT.decode(Response.json(TAN_WEI_MING_BARE))).resolves.toStrictEqual({
      birthDate: "1985-03-12",
      gender: "male",
      id: "eq081-VQEgP8drUUqCWzHfw3",
      name: "Tan Wei Ming",
      nric: "S1234567A",
    });
  });

  it("reads the Demographic details: phone, email, home address, Next of kin and Military history", async () => {
    await expect(PATIENT.decode(Response.json(TAN_WEI_MING))).resolves.toStrictEqual({
      birthDate: "1985-03-12",
      email: "tan.hw@email.com",
      gender: "male",
      id: "eq081-VQEgP8drUUqCWzHfw3",
      militaryHistory: {
        enlistmentDate: "2018-01-15",
        ordDate: "2020-01-14",
        rodDate: "2037-09-08",
        serviceRecords: [
          {
            rank: "CPT",
            service: "Army",
            serviceType: "NSman",
            unit: "3 SIR",
            vocation: "Infantry Officer",
          },
          {
            rank: "ME2",
            service: "Navy",
            serviceType: "DXO",
            unit: "RSS Supreme",
            vocation: "Naval Safety Officer",
          },
        ],
      },
      name: "Tan Wei Ming",
      nextOfKin: { name: "Mdm Lee Siew Kuen", phone: "+65 8234 5678", relationship: "Mother" },
      nric: "S1234567A",
      phone: "+65 9123 4567",
      residentialAddress: "Blk 123 Bishan Street 12 #10-456, Singapore 570123",
    });
  });

  it("reads the first phone and the first email, past any other contact point", async () => {
    const telecom = [
      { system: "fax", value: "+65 6000 0000" },
      { system: "email", value: "first@example.org" },
      { system: "phone", value: "+65 6000 0001" },
      { system: "phone", value: "+65 6000 0002" },
      { system: "email", value: "second@example.org" },
    ];

    await expect(PATIENT.decode(Response.json({ ...TAN_WEI_MING, telecom }))).resolves.toEqual(
      expect.objectContaining({ email: "first@example.org", phone: "+65 6000 0001" }),
    );
  });

  it("reads the residential address from the home address alone", async () => {
    const address = [
      { text: "1 Office Road", use: "work" },
      { text: "2 Home Road", use: "home" },
    ];

    await expect(PATIENT.decode(Response.json({ ...TAN_WEI_MING, address }))).resolves.toEqual(
      expect.objectContaining({ residentialAddress: "2 Home Road" }),
    );
  });

  it("has no residential address when the patient has only another kind", async () => {
    const address = [{ text: "1 Office Road", use: "work" }];

    const patient = await PATIENT.decode(Response.json({ ...TAN_WEI_MING, address }));

    expect(patient).not.toHaveProperty("residentialAddress");
  });

  it("reads the Next of kin from the first contact coded as one, past any other contact", async () => {
    const [nextOfKin] = TAN_WEI_MING.contact;
    const employer = { name: { text: "Acme Pte Ltd" }, relationship: [{ text: "Employer" }] };

    await expect(
      PATIENT.decode(Response.json({ ...TAN_WEI_MING, contact: [employer, nextOfKin] })),
    ).resolves.toEqual(
      expect.objectContaining({
        nextOfKin: { name: "Mdm Lee Siew Kuen", phone: "+65 8234 5678", relationship: "Mother" },
      }),
    );
  });

  it("reads a Next of kin with no relationship text and no phone by their name alone", async () => {
    const contact = [{ name: { text: "Mdm Lee Siew Kuen" }, relationship: [nextOfKinRole] }];

    const patient = await PATIENT.decode(Response.json({ ...TAN_WEI_MING, contact }));

    expect(patient.nextOfKin).toStrictEqual({ name: "Mdm Lee Siew Kuen" });
  });

  it.each([
    ["by text alone", [{ text: "Employer" }]],
    [
      "as v2-0131's emergency contact, C",
      [{ coding: [{ code: "C", system: "http://terminology.hl7.org/CodeSystem/v2-0131" }] }],
    ],
    ["as N under another system", [{ coding: [{ code: "N", system: "urn:example:roles" }] }]],
  ])("has no Next of kin when the one contact is coded %s", async (_, relationship) => {
    const contact = [{ name: { text: "Acme Pte Ltd" }, relationship }];

    const patient = await PATIENT.decode(Response.json({ ...TAN_WEI_MING, contact }));

    expect(patient).not.toHaveProperty("nextOfKin");
  });

  it("reads a Military history with an empty record and an unknown field, leaving what it lacks out", async () => {
    const extension = military([
      { url: "serviceRecord" },
      {
        extension: [
          { url: "rank", valueString: "CPT" },
          { url: "a-later-field", valueString: "unread" },
        ],
        url: "serviceRecord",
      },
      { url: "a-later-field", valueString: "unread" },
      { url: "mrDate", valueDate: "2030-04-01" },
    ]);

    const patient = await PATIENT.decode(Response.json({ ...TAN_WEI_MING, extension }));

    expect(patient.militaryHistory).toStrictEqual({
      mrDate: "2030-04-01",
      serviceRecords: [{}, { rank: "CPT" }],
    });
  });

  it("has no Military history when the patient has only another extension", async () => {
    const extension = [{ url: "urn:example:other", valueString: "x" }];

    const patient = await PATIENT.decode(Response.json({ ...TAN_WEI_MING, extension }));

    expect(patient).not.toHaveProperty("militaryHistory");
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
    [
      "a telecom that is not an array",
      { ...TAN_WEI_MING, telecom: { system: "phone", value: "+65 9123 4567" } },
      "Patient.telecom is not an array",
    ],
    [
      "a contact point with no value",
      { ...TAN_WEI_MING, telecom: [{ system: "phone" }] },
      "ContactPoint.value is not a string",
    ],
    [
      "a home address with no text",
      { ...TAN_WEI_MING, address: [{ line: ["Blk 123"], use: "home" }] },
      "Address.text is not a string",
    ],
    [
      "a Next of kin with no name text",
      { ...TAN_WEI_MING, contact: [{ name: {}, relationship: [nextOfKinRole] }] },
      "Patient.contact.name.text is not a string",
    ],
    [
      "a service record field that is not a string",
      {
        ...TAN_WEI_MING,
        extension: military([
          { extension: [{ url: "rank", valueInteger: 3 }], url: "serviceRecord" },
        ]),
      },
      "serviceRecord.rank.valueString is not a string",
    ],
    [
      "a contact that is not an object",
      { ...TAN_WEI_MING, contact: ["N"] },
      "Patient.contact is not an object",
    ],
    [
      "contacts that are not an array",
      { ...TAN_WEI_MING, contact: {} },
      "Patient.contact is not an array",
    ],
    [
      "a contact's relationships that are not an array",
      { ...TAN_WEI_MING, contact: [{ name: { text: "Lee" }, relationship: "N" }] },
      "Patient.contact.relationship is not an array",
    ],
    [
      "a relationship's codings that are not an array",
      { ...TAN_WEI_MING, contact: [{ name: { text: "Lee" }, relationship: [{ coding: "N" }] }] },
      "CodeableConcept.coding is not an array",
    ],
    [
      "a coding that is not an object",
      { ...TAN_WEI_MING, contact: [{ name: { text: "Lee" }, relationship: [{ coding: ["N"] }] }] },
      "Coding is not an object",
    ],
    [
      "a relationship text that is not a string",
      {
        ...TAN_WEI_MING,
        contact: [{ name: { text: "Lee" }, relationship: [nextOfKinRole, { text: 3 }] }],
      },
      "CodeableConcept.text is not a string",
    ],
    [
      "a Next of kin's contact points that are not an array",
      {
        ...TAN_WEI_MING,
        contact: [{ name: { text: "Lee" }, relationship: [nextOfKinRole], telecom: {} }],
      },
      "Patient.contact.telecom is not an array",
    ],
    [
      "addresses that are not an array",
      { ...TAN_WEI_MING, address: {} },
      "Patient.address is not an array",
    ],
    [
      "extensions that are not an array",
      { ...TAN_WEI_MING, extension: {} },
      "Patient.extension is not an array",
    ],
    [
      "an extension with no url",
      { ...TAN_WEI_MING, extension: [{ valueString: "x" }] },
      "Extension.url is not a string",
    ],
    [
      "a Military history whose parts are not an array",
      { ...TAN_WEI_MING, extension: [{ extension: {}, url: MILITARY_HISTORY_EXTENSION }] },
      "military-service.extension is not an array",
    ],
    [
      "a service record whose fields are not an array",
      { ...TAN_WEI_MING, extension: military([{ extension: {}, url: "serviceRecord" }]) },
      "serviceRecord.extension is not an array",
    ],
    [
      "a Military history part with no url",
      { ...TAN_WEI_MING, extension: military([{ valueDate: "2020-01-14" }]) },
      "Extension.url is not a string",
    ],
    [
      "a service date with no value",
      { ...TAN_WEI_MING, extension: military([{ url: "ordDate" }]) },
      "military-service.ordDate.valueDate is not a string",
    ],
    [
      "a service date not to the day",
      { ...TAN_WEI_MING, extension: military([{ url: "ordDate", valueDate: "2020-01" }]) },
      "military-service.ordDate is not a date",
    ],
  ])("is refused with %s", async (_, body, message) => {
    await expect(PATIENT.decode(Response.json(body))).rejects.toThrow(
      expect.objectContaining({ message, name: "DecodeError" }),
    );
  });
});
