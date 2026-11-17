import type { Patient } from "fhir/r5";
import { describe, expect, expectTypeOf, it } from "vitest";

import type { PatientGender } from "@/lib/patient-gender";

import { INVALID_FIELD_EXTENSION, MILITARY_HISTORY_EXTENSION, PATIENT } from "./patient-api";
import { TAN_WEI_MING, TAN_WEI_MING_BARE } from "./patient.testHelpers";

/** A relationship coded as the patient's Next of kin. */
const nextOfKinRole = {
  coding: [{ code: "N", system: "http://terminology.hl7.org/CodeSystem/v2-0131" }],
};

/** The Military history extension holding `extension`. */
const military = (extension: object[]) => [{ extension, url: MILITARY_HISTORY_EXTENSION }];

/** The extension naming `element` as received in the wrong format. */
const invalidField = (element: string) => ({
  url: "http://example.org/fhir/StructureDefinition/invalid-field",
  valueString: element,
});

const present = (value: string) => ({ state: "present", value });
const MISSING = { state: "missing" };
const INVALID = { state: "invalid" };

/** The four elements shown as received, as Tan Wei Ming's are read. */
const TAN_WEI_MINGS_ELEMENTS = {
  birthDate: present("1985-03-12"),
  gender: present("male"),
  name: present("Tan Wei Ming"),
  nric: present("S1234567A"),
};

/** Every Demographic detail missing: what a patient with none of them is read with. */
const NO_DETAILS = { email: MISSING, phone: MISSING, residentialAddress: MISSING };

/** A service record with every field missing, but those given. */
const record = (fields: object) => ({
  rank: MISSING,
  service: MISSING,
  serviceType: MISSING,
  unit: MISSING,
  vocation: MISSING,
  ...fields,
});

/** A Military history with no record and every date missing, but those given. */
const history = (fields: object) => ({
  enlistmentDate: MISSING,
  mrDate: MISSING,
  ordDate: MISSING,
  rodDate: MISSING,
  serviceRecords: [],
  ...fields,
});

/** Tan Wei Ming's four elements as read from `patient`, the rest of him dropped. */
const elementsOf = async (patient: object) => {
  const { birthDate, gender, name, nric } = await PATIENT.decode(Response.json(patient));
  return { birthDate, gender, name, nric };
};

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
      ...TAN_WEI_MINGS_ELEMENTS,
      ...NO_DETAILS,
    });
  });

  it("reads the Demographic details: phone, email, home address, Next of kin and Military history", async () => {
    await expect(PATIENT.decode(Response.json(TAN_WEI_MING))).resolves.toStrictEqual({
      ...TAN_WEI_MINGS_ELEMENTS,
      email: present("tan.hw@email.com"),
      militaryHistory: {
        enlistmentDate: present("2018-01-15"),
        mrDate: MISSING,
        ordDate: present("2020-01-14"),
        rodDate: present("2037-09-08"),
        serviceRecords: [
          {
            rank: present("CPT"),
            service: present("Army"),
            serviceType: present("NSman"),
            unit: present("3 SIR"),
            vocation: present("Infantry Officer"),
          },
          {
            rank: present("ME2"),
            service: present("Navy"),
            serviceType: present("DXO"),
            unit: present("RSS Supreme"),
            vocation: present("Naval Safety Officer"),
          },
        ],
      },
      nextOfKin: {
        name: present("Mdm Lee Siew Kuen"),
        phone: present("+65 8234 5678"),
        relationship: present("Mother"),
      },
      phone: present("+65 9123 4567"),
      residentialAddress: present("Blk 123 Bishan Street 12 #10-456, Singapore 570123"),
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
      expect.objectContaining({
        email: present("first@example.org"),
        phone: present("+65 6000 0001"),
      }),
    );
  });

  it("reads the residential address from the home address alone", async () => {
    const address = [
      { text: "1 Office Road", use: "work" },
      { text: "2 Home Road", use: "home" },
    ];

    await expect(PATIENT.decode(Response.json({ ...TAN_WEI_MING, address }))).resolves.toEqual(
      expect.objectContaining({ residentialAddress: present("2 Home Road") }),
    );
  });

  it("reads the Next of kin from the first contact coded as one, past any other contact", async () => {
    const [nextOfKin] = TAN_WEI_MING.contact;
    const employer = { name: { text: "Acme Pte Ltd" }, relationship: [{ text: "Employer" }] };

    await expect(
      PATIENT.decode(Response.json({ ...TAN_WEI_MING, contact: [employer, nextOfKin] })),
    ).resolves.toEqual(
      expect.objectContaining({
        nextOfKin: {
          name: present("Mdm Lee Siew Kuen"),
          phone: present("+65 8234 5678"),
          relationship: present("Mother"),
        },
      }),
    );
  });

  it("reads a Next of kin with no relationship text and no phone by their name alone", async () => {
    const contact = [{ name: { text: "Mdm Lee Siew Kuen" }, relationship: [nextOfKinRole] }];

    const patient = await PATIENT.decode(Response.json({ ...TAN_WEI_MING, contact }));

    expect(patient.nextOfKin).toStrictEqual({
      name: present("Mdm Lee Siew Kuen"),
      phone: MISSING,
      relationship: MISSING,
    });
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

  it("reads a Military history with an empty record and an unknown field, each field the first under its url", async () => {
    const extension = military([
      { url: "serviceRecord" },
      {
        extension: [
          { url: "rank", valueString: "CPT" },
          { url: "rank", valueString: "MAJ" },
          { url: "a-later-field", valueString: "unread" },
        ],
        url: "serviceRecord",
      },
      { url: "a-later-field", valueString: "unread" },
      { url: "mrDate", valueDate: "2030-04-01" },
    ]);

    const patient = await PATIENT.decode(Response.json({ ...TAN_WEI_MING, extension }));

    expect(patient.militaryHistory).toStrictEqual(
      history({
        mrDate: present("2030-04-01"),
        serviceRecords: [record({}), record({ rank: present("CPT") })],
      }),
    );
  });

  it("has no Military history when the patient has only another extension", async () => {
    const extension = [{ url: "urn:example:other", valueString: "x" }];

    const patient = await PATIENT.decode(Response.json({ ...TAN_WEI_MING, extension }));

    expect(patient).not.toHaveProperty("militaryHistory");
  });

  // Shown as received (ADR 0020): each Demographic detail is its value, missing
  // when the backend left it out, and invalid when an invalid-field extension
  // names it by its path or it is not a value of its kind; none of them refuses
  // the Patient, and each is read apart from the others.
  it.each([
    ["no telecom", { telecom: undefined }, { email: MISSING, phone: MISSING }],
    ["a phone with no value", { telecom: [{ system: "phone" }] }, { phone: MISSING }],
    [
      "no home address",
      { address: [{ text: "1 Office Road", use: "work" }] },
      {
        residentialAddress: MISSING,
      },
    ],
    [
      "a home address with no text",
      { address: [{ line: ["Blk 123"], use: "home" }] },
      { residentialAddress: MISSING },
    ],
  ])("reads %s as that detail missing", async (_, change, read) => {
    await expect(PATIENT.decode(Response.json({ ...TAN_WEI_MING, ...change }))).resolves.toEqual(
      expect.objectContaining(read),
    );
  });

  it.each([
    [
      "the phone",
      { extension: [invalidField("telecom[0].value")], telecom: [{ system: "phone" }] },
      { phone: INVALID },
    ],
    [
      "the email after a phone",
      {
        extension: [invalidField("telecom[1].value")],
        telecom: [{ system: "phone", value: "+65 9123 4567" }, { system: "email" }],
      },
      { email: INVALID, phone: present("+65 9123 4567") },
    ],
    [
      "a phone that is no string",
      { telecom: [{ system: "phone", value: 6591234567 }] },
      {
        phone: INVALID,
      },
    ],
    [
      "the home address",
      { address: [{ use: "home" }], extension: [invalidField("address[0].text")] },
      { residentialAddress: INVALID },
    ],
    [
      "a home address text that is no string",
      { address: [{ text: ["Blk 123"], use: "home" }] },
      { residentialAddress: INVALID },
    ],
  ])("reads %s as invalid, the rest as it came", async (_, change, read) => {
    await expect(PATIENT.decode(Response.json({ ...TAN_WEI_MING, ...change }))).resolves.toEqual(
      expect.objectContaining(read),
    );
  });

  it("reads a Next of kin with no name text as their name missing", async () => {
    const contact = [{ name: { family: "Lee" }, relationship: [nextOfKinRole] }];

    const patient = await PATIENT.decode(Response.json({ ...TAN_WEI_MING, contact }));

    expect(patient.nextOfKin?.name).toStrictEqual(MISSING);
  });

  it("reads each of a Next of kin's details invalid when an invalid-field extension names it", async () => {
    const contact = [{ relationship: [nextOfKinRole], telecom: [{ system: "phone" }] }];
    const extension = [
      invalidField("contact[0].name.text"),
      invalidField("contact[0].relationship[1].text"),
      invalidField("contact[0].telecom[0].value"),
    ];

    const patient = await PATIENT.decode(Response.json({ ...TAN_WEI_MING, contact, extension }));

    expect(patient.nextOfKin).toStrictEqual({
      name: INVALID,
      phone: INVALID,
      relationship: INVALID,
    });
  });

  it("reads a Next of kin's details that are no strings as invalid", async () => {
    const contact = [
      {
        name: { text: 3 },
        relationship: [nextOfKinRole, { text: 1 }],
        telecom: [{ system: "phone", value: 82345678 }],
      },
    ];

    const patient = await PATIENT.decode(Response.json({ ...TAN_WEI_MING, contact }));

    expect(patient.nextOfKin).toStrictEqual({
      name: INVALID,
      phone: INVALID,
      relationship: INVALID,
    });
  });

  it("reads a Military history's fields and dates invalid when an invalid-field extension names each, the rest as they came", async () => {
    const extension = [
      ...military([
        { extension: [{ url: "rank", valueString: "CPT" }], url: "serviceRecord" },
        { extension: [{ url: "serviceType", valueString: "NSF" }], url: "serviceRecord" },
        { url: "ordDate", valueDate: "2020-01-14" },
      ]),
      invalidField("extension[0].extension[1].extension('unit').valueString"),
      invalidField("extension[0].extension('enlistmentDate').valueDate"),
    ];

    const patient = await PATIENT.decode(Response.json({ ...TAN_WEI_MING, extension }));

    expect(patient.militaryHistory).toStrictEqual(
      history({
        enlistmentDate: INVALID,
        ordDate: present("2020-01-14"),
        serviceRecords: [
          record({ rank: present("CPT") }),
          record({ serviceType: present("NSF"), unit: INVALID }),
        ],
      }),
    );
  });

  it.each([
    [
      "a service record field that is no string",
      [{ extension: [{ url: "rank", valueString: 3 }], url: "serviceRecord" }],
      { serviceRecords: [record({ rank: INVALID })] },
    ],
    [
      "a service date not to the day",
      [{ url: "ordDate", valueDate: "2020-01" }],
      {
        ordDate: INVALID,
      },
    ],
    [
      "a service date no calendar has",
      [{ url: "ordDate", valueDate: "2020-02-30" }],
      {
        ordDate: INVALID,
      },
    ],
    ["a service date with no value", [{ url: "ordDate" }], {}],
  ])("reads %s as the Military history holds it", async (_, parts, read) => {
    const patient = await PATIENT.decode(
      Response.json({ ...TAN_WEI_MING, extension: military(parts) }),
    );

    expect(patient.militaryHistory).toStrictEqual(history(read));
  });

  it("reads a Patient whose every Demographic detail is malformed with its card's elements as they came", async () => {
    const malformed = {
      ...TAN_WEI_MING,
      address: [{ text: 1, use: "home" }],
      contact: [{ name: { text: 2 }, relationship: [nextOfKinRole] }],
      extension: military([{ extension: [{ url: "rank", valueString: 4 }], url: "serviceRecord" }]),
      telecom: [
        { system: "phone", value: 5 },
        { system: "email", value: 6 },
      ],
    };

    await expect(elementsOf(malformed)).resolves.toStrictEqual(TAN_WEI_MINGS_ELEMENTS);
  });

  it("reads a name with no family or given part by its text alone", async () => {
    const mononym = { ...TAN_WEI_MING, name: [{ text: "Suriani", use: "official" }] };

    await expect(PATIENT.decode(Response.json(mononym))).resolves.toEqual(
      expect.objectContaining({ name: present("Suriani") }),
    );
  });

  it("reads the NRIC from the identifier under the NRIC system, past any other", async () => {
    const twoIdentifiers = {
      ...TAN_WEI_MING,
      identifier: [{ system: "urn:example:other", value: "X-1" }, ...TAN_WEI_MING.identifier],
    };

    await expect(PATIENT.decode(Response.json(twoIdentifiers))).resolves.toEqual(
      expect.objectContaining({ nric: present("S1234567A") }),
    );
  });

  // Shown as received (ADR 0020): each of the four is its value, missing when
  // the backend left it out, and invalid when an invalid-field extension names
  // it or it is not a value of its kind; none of them refuses the Patient.
  it.each([
    ["no name", { name: undefined }, { name: MISSING }],
    ["no name entry", { name: [] }, { name: MISSING }],
    ["a name with no text", { name: [{ family: "Tan", use: "official" }] }, { name: MISSING }],
    ["no identifier", { identifier: undefined }, { nric: MISSING }],
    [
      "no identifier under the NRIC system",
      { identifier: [{ system: "urn:example:other", value: "X-1" }] },
      { nric: MISSING },
    ],
    [
      "an NRIC identifier with no value",
      { identifier: [{ system: "https://fhir.synapxe.sg/NamingSystem/nric" }] },
      { nric: MISSING },
    ],
    ["no gender", { gender: undefined }, { gender: MISSING }],
    ["no birth date", { birthDate: undefined }, { birthDate: MISSING }],
  ])("reads %s as that element missing, and the rest as they came", async (_, change, read) => {
    await expect(elementsOf({ ...TAN_WEI_MING_BARE, ...change })).resolves.toStrictEqual({
      ...TAN_WEI_MINGS_ELEMENTS,
      ...read,
    });
  });

  it.each([
    [
      "the NRIC",
      {
        extension: [invalidField("identifier[0].value")],
        identifier: [{ system: "https://fhir.synapxe.sg/NamingSystem/nric" }],
      },
      { nric: INVALID },
    ],
    [
      "the NRIC past another identifier",
      {
        extension: [invalidField("identifier[1].value")],
        identifier: [
          { system: "urn:example:other", value: "X-1" },
          { system: "https://fhir.synapxe.sg/NamingSystem/nric" },
        ],
      },
      { nric: INVALID },
    ],
    [
      "the name's text",
      { extension: [invalidField("name[0].text")], name: [{ family: "Tan", use: "official" }] },
      { name: INVALID },
    ],
    ["the gender", { extension: [invalidField("gender")], gender: undefined }, { gender: INVALID }],
    [
      "the birth date",
      { extension: [invalidField("birthDate")], birthDate: undefined },
      { birthDate: INVALID },
    ],
  ])("reads %s as invalid when an invalid-field extension names it", async (_, change, read) => {
    await expect(elementsOf({ ...TAN_WEI_MING_BARE, ...change })).resolves.toStrictEqual({
      ...TAN_WEI_MINGS_ELEMENTS,
      ...read,
    });
  });

  it("reads each of the four invalid at once, past the Military history", async () => {
    const extension = [
      ...military([{ url: "mrDate", valueDate: "2030-04-01" }]),
      invalidField("identifier[0].value"),
      invalidField("name[0].text"),
      invalidField("gender"),
      invalidField("birthDate"),
    ];
    const patient = {
      extension,
      id: TAN_WEI_MING_BARE.id,
      identifier: [{ system: "https://fhir.synapxe.sg/NamingSystem/nric" }],
      name: [{ use: "official" }],
      resourceType: "Patient",
    };

    await expect(PATIENT.decode(Response.json(patient))).resolves.toStrictEqual({
      ...NO_DETAILS,
      birthDate: INVALID,
      gender: INVALID,
      militaryHistory: history({ mrDate: present("2030-04-01") }),
      name: INVALID,
      nric: INVALID,
    });
  });

  it.each([
    ["a name text that is no string", { name: [{ text: 3, use: "official" }] }, { name: INVALID }],
    [
      "an NRIC that is no string",
      { identifier: [{ system: "https://fhir.synapxe.sg/NamingSystem/nric", value: 7 }] },
      { nric: INVALID },
    ],
    ["a gender that is no string", { gender: true }, { gender: INVALID }],
    ["a birth date not to the day", { birthDate: "1985-03" }, { birthDate: INVALID }],
    ["a birth date no calendar has", { birthDate: "1985-02-30" }, { birthDate: INVALID }],
  ])("reads %s, a shape the backend does not send, as invalid", async (_, change, read) => {
    await expect(elementsOf({ ...TAN_WEI_MING_BARE, ...change })).resolves.toStrictEqual({
      ...TAN_WEI_MINGS_ELEMENTS,
      ...read,
    });
  });

  it("reads a gender outside the four codes as the code it came as", async () => {
    await expect(elementsOf({ ...TAN_WEI_MING_BARE, gender: "M" })).resolves.toStrictEqual({
      ...TAN_WEI_MINGS_ELEMENTS,
      gender: present("M"),
    });
  });

  it.each([
    [
      "another resource type",
      { ...TAN_WEI_MING, resourceType: "Practitioner" },
      "Patient.resourceType is not Patient",
    ],
    [
      "an invalid-field extension naming no element",
      { ...TAN_WEI_MING, extension: [{ url: INVALID_FIELD_EXTENSION, valueInteger: 3 }] },
      "invalid-field.valueString is not a string",
    ],
    [
      "a name that is not an object",
      { ...TAN_WEI_MING, name: ["Tan Wei Ming"] },
      "HumanName is not an object",
    ],
    [
      "names that are not an array",
      { ...TAN_WEI_MING, name: { text: "Tan Wei Ming" } },
      "Patient.name is not an array",
    ],
    [
      "identifiers that are not an array",
      { ...TAN_WEI_MING, identifier: {} },
      "Patient.identifier is not an array",
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
      "a Next of kin's name that is not an object",
      { ...TAN_WEI_MING, contact: [{ name: "Lee", relationship: [nextOfKinRole] }] },
      "Patient.contact.name is not an object",
    ],
    [
      "a contact point that is not an object",
      { ...TAN_WEI_MING, telecom: ["+65 9123 4567"] },
      "ContactPoint is not an object",
    ],
    [
      "an address that is not an object",
      { ...TAN_WEI_MING, address: ["Blk 123"] },
      "Address is not an object",
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
  ])("is refused with %s", async (_, body, message) => {
    await expect(PATIENT.decode(Response.json(body))).rejects.toThrow(
      expect.objectContaining({ message, name: "DecodeError" }),
    );
  });
});
