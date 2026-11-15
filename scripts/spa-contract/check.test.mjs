// Tests for the SPA–API contract check (./check.mjs). Run by `make verify-contract`
// before the check itself, with Node's own runner: `node --test scripts/spa-contract/`.
//
// Every test hands `findMismatches` an SPA and an API document that agree, less
// the one difference it names, and asserts on the findings the check reports.
// The SPA's decoders are written here, so a finding is the document disagreeing
// with a decoder of known behaviour, never with one under test elsewhere.

import assert from "node:assert/strict";
import { describe, it } from "node:test";

import {
  declaredNames,
  discover,
  findMismatches,
  holdContract,
} from "./check.mjs";

// ---- the SPA's decoders, written here -----------------------------------------------
//
// Each reads the fields it names, as the SPA's decoders do (`frontend/src/lib/decode.ts`):
// a field it reads must be there and of its kind, an optional one may be absent, and a
// strict one refuses any field it does not name. Each throws naming the field.

const is = (label, test) => ({ label, test });
const aString = is("a string", (value) => typeof value === "string");
const anInteger = is("an integer", (value) => Number.isInteger(value));
const aBoolean = is("a boolean", (value) => typeof value === "boolean");
const aUuid = is("a uuid", (value) =>
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(value),
);
const aDate = is("a date", (value) => /^\d{4}-\d{2}-\d{2}$/.test(value));
const aDateTime = is("a date-time", (value) =>
  /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2})?([+-]\d{2}:\d{2}|Z)$/.test(value),
);
const nullable = ({ label, test }) =>
  is(`null or ${label}`, (value) => value === null || test(value));
const optional = (check) => ({ ...check, optional: true });
const oneOf = (...members) =>
  is(members.join(" | "), (value) => members.includes(value));
const arrayOf = (decode) =>
  is(
    "an array",
    (value) =>
      Array.isArray(value) && value.every((item) => (decode(item), true)),
  );

/** A decoder of an object holding `fields`; a `strict` one refuses any other field. */
function objectOf(what, fields, { strict = false } = {}) {
  return (value) => {
    if (typeof value !== "object" || value === null || Array.isArray(value)) {
      throw new Error(`${what} is not an object`);
    }
    for (const [key, { label, test, optional: absent }] of Object.entries(
      fields,
    )) {
      if (absent && !(key in value)) continue;
      if (!(key in value) || !test(value[key])) {
        throw new Error(`${what}.${key} is not ${label}`);
      }
    }
    const extra = Object.keys(value).find((key) => !(key in fields));
    if (strict && extra !== undefined) {
      throw new Error(`${what}.${extra} is not expected`);
    }
    return value;
  };
}

/** A page of `rowsField`, each row read by `row`: strict, as the SPA's page decoder is. */
const pageOf = (what, rowsField, row) =>
  objectOf(
    what,
    {
      totalResults: anInteger,
      startIndex: anInteger,
      [rowsField]: arrayOf(row),
    },
    { strict: true },
  );

/** `decode` lifted to the `Response` decoder a declaration names. */
const json = (decode) => async (response) => decode(await response.json());

const readAccount = objectOf("AccountSummary", {
  id: aUuid,
  userName: aString,
  displayName: nullable(aString),
  locked: aBoolean,
  lockCause: nullable(aString),
  nickname: optional(aString),
});

/** An account whose `lockCause` is `null` exactly when it is not `locked`, as the SPA's `decodeUserRow` reads one. */
const decodeAccount = (value) => {
  const account = readAccount(value);
  if (account.locked !== (account.lockCause !== null)) {
    throw new Error(
      "AccountSummary.lockCause is not null exactly when locked is false",
    );
  }
  return account;
};

const decodeGroup = objectOf("GroupSummary", {
  id: aUuid,
  displayName: aString,
  permissions: arrayOf((permission) => permission),
});

const decodeMember = objectOf(
  "GroupMember",
  { id: aUuid, userName: aString },
  { strict: true },
);

const decodeAuditEvent = objectOf(
  "AuditEvent",
  {
    id: aUuid,
    occurredAt: aDateTime,
    operation: aString,
    outcome: oneOf("SUCCESS", "FAILURE"),
    actorId: nullable(aUuid),
  },
  { strict: true },
);

const decodeFhirPatient = objectOf(
  "Patient",
  { resourceType: oneOf("Patient"), id: aString, birthDate: aDate },
  { strict: true },
);

/** Reads a Patient only as FHIR JSON, as a decoder reading the media type would. */
const decodePatient = async (response) => {
  if (response.headers.get("content-type") !== "application/fhir+json") {
    throw new Error("Patient is not served as application/fhir+json");
  }
  return decodeFhirPatient(await response.json());
};

// ---- the document, and the SPA, agreeing --------------------------------------------

/** A documented success body of `schema`, under `mediaType`. */
const body = (schema, mediaType = "application/json") => ({
  description: "Answered.",
  content: { [mediaType]: { schema } },
});

const ref = (name) => ({ $ref: `#/components/schemas/${name}` });

/** A page schema whose rows arrive in `rowsField`, each of the schema `row` names. */
const pageSchema = (rowsField, row) => ({
  type: "object",
  required: ["totalResults", "startIndex", rowsField],
  additionalProperties: false,
  properties: {
    totalResults: { type: "integer", format: "int64" },
    startIndex: { type: "integer", minimum: 1 },
    [rowsField]: { type: "array", items: ref(row) },
  },
});

/** An API document holding every enum and operation the check reads, agreeing with `spa()`. */
function document() {
  return {
    security: [{ sessionCookie: [] }],
    paths: {
      "/api/admin/accounts": {
        get: {
          security: [{ sessionCookie: ["user:read"] }],
          parameters: [
            { $ref: "#/components/parameters/Search" },
            { $ref: "#/components/parameters/StartIndex" },
          ],
          responses: { 200: body(ref("AccountPage")) },
        },
      },
      "/api/admin/groups/{id}/permissions": {
        put: {
          security: [{ sessionCookie: ["superuser"] }],
          responses: { 200: { $ref: "#/components/responses/Group" } },
        },
      },
      "/api/admin/groups/{id}/members": {
        parameters: [
          { name: "id", in: "path", required: true },
          { $ref: "#/components/parameters/StartIndex" },
        ],
        get: {
          security: [{ sessionCookie: ["group:read", "user:read"] }],
          responses: { 200: body(ref("GroupMemberPage")) },
        },
      },
      "/api/admin/audit-events": {
        get: {
          security: [{ sessionCookie: ["audit:read"] }],
          parameters: [
            { name: "operation", in: "query" },
            { name: "outcome", in: "query" },
          ],
          responses: { 200: body(ref("AuditEventPage")) },
        },
      },
      "/api/patient/{id}": {
        get: {
          security: [{ sessionCookie: ["patient:read"] }],
          responses: {
            200: body(ref("FhirPatient"), "application/fhir+json"),
            404: body(ref("FhirOperationOutcome"), "application/fhir+json"),
          },
        },
      },
    },
    components: {
      parameters: {
        Search: { name: "search", in: "query", required: false },
        StartIndex: { name: "startIndex", in: "query" },
      },
      responses: { Group: body(ref("GroupSummary")) },
      schemas: {
        UserResponse: {
          properties: {
            permissions: {
              items: { type: "string", enum: ["group:read", "user:read"] },
            },
          },
        },
        DelegatablePermission: { type: "string", enum: ["group:read"] },
        ConnectorTokenPermissions: {
          type: "array",
          items: { type: "string", enum: ["user:read", "user:write"] },
        },
        ConnectorTokenSummary: {
          properties: {
            permissions: {
              items: { type: "string", enum: ["user:read", "user:write"] },
            },
          },
        },
        AuditOperation: { type: "string", enum: ["LOGIN_SUCCESS", "LOGOUT"] },
        AccountPage: pageSchema("users", "AccountSummary"),
        AccountSummary: {
          type: "object",
          required: ["id", "userName", "displayName", "locked", "lockCause"],
          properties: {
            id: { type: "string", format: "uuid" },
            userName: { type: "string", minLength: 1 },
            displayName: { type: ["string", "null"] },
            locked: { type: "boolean" },
            lockCause: { enum: ["FAILURES", "DORMANCY", null] },
            nickname: { type: "string", description: "Absent when unset." },
          },
        },
        GroupSummary: {
          type: "object",
          required: ["id", "displayName", "permissions"],
          properties: {
            id: { type: "string", format: "uuid" },
            displayName: { type: "string" },
            permissions: { type: "array", items: ref("DelegatablePermission") },
          },
        },
        GroupMemberPage: pageSchema("members", "GroupMember"),
        GroupMember: {
          type: "object",
          required: ["id", "userName"],
          additionalProperties: false,
          properties: {
            id: { type: "string", format: "uuid" },
            userName: { type: "string" },
          },
        },
        AuditEventPage: pageSchema("events", "AuditEvent"),
        AuditEvent: {
          type: "object",
          required: ["id", "occurredAt", "operation", "outcome", "actorId"],
          properties: {
            id: { type: "string", format: "uuid" },
            occurredAt: { type: "string", format: "date-time" },
            operation: { type: "string" },
            outcome: ref("AuditOutcome"),
            actorId: { type: ["string", "null"], format: "uuid" },
          },
        },
        AuditOutcome: { type: "string", enum: ["SUCCESS", "FAILURE"] },
        FhirPatient: {
          type: "object",
          required: ["resourceType", "id", "birthDate"],
          additionalProperties: false,
          properties: {
            resourceType: { type: "string", enum: ["Patient"] },
            id: { type: "string", pattern: "^[A-Za-z0-9\\-.]{1,64}$" },
            birthDate: {
              type: "string",
              format: "date",
              pattern: "^[0-9]{4}-[0-9]{2}-[0-9]{2}$",
            },
          },
        },
        FhirOperationOutcome: { type: "object" },
        FfiType: { type: "string", enum: ["ippt", "dental"] },
        FfiOutcome: { type: "string", enum: ["fit", "unfit"] },
        FfiHigherAuthority: { type: "string", enum: ["camo", "dis"] },
        MedicalCentre: { type: "string", enum: ["kranji-camp"] },
        FfiStatus: { type: "string", enum: ["active", "inactive"] },
        NumcType: { type: "string", enum: ["submariner", "diving"] },
        NumcOutcome: { type: "string", enum: ["fit", "waiver-granted"] },
        ArmcBoardType: { type: "string", enum: ["aces"] },
        ArmcOutcome: { type: "string", enum: ["fit"] },
        DispenseStatus: { type: "string", enum: ["pending"] },
      },
    },
  };
}

/** The query string of a filter set the audit trail is read for, each filter only when set. */
function auditQuery({ operations, outcome }) {
  const params = new URLSearchParams();
  for (const operation of operations) params.append("operation", operation);
  if (outcome !== null) params.set("outcome", outcome);
  return params.toString();
}

/** The SPA's modules, as the check reads them: each module's exports, and the declarations its source names. */
function spa() {
  return {
    modules: {
      "src/auth/api.ts": { PERMISSIONS: ["group:read", "user:read"] },
      "src/pages/accounts/accounts-api.ts": {
        TOKEN_PERMISSIONS: ["user:read", "user:write"],
        LOCK_CAUSES: ["FAILURES", "DORMANCY"],
        USERS: {
          decode: json(pageOf("AccountPage", "users", decodeAccount)),
          permission: "user:read",
          path: ({ search }, { startIndex }) =>
            `/api/admin/accounts?${[
              ...(search === ""
                ? []
                : [`search=${encodeURIComponent(search)}`]),
              `startIndex=${startIndex}`,
            ].join("&")}`,
        },
        SET_GROUP_PERMISSIONS: {
          decode: json(decodeGroup),
          method: "PUT",
          permission: "superuser",
          path: (groupId) =>
            `/api/admin/groups/${encodeURIComponent(groupId)}/permissions`,
        },
        GROUP_MEMBERS: {
          decode: json(pageOf("GroupMemberPage", "members", decodeMember)),
          permission: { allOf: ["group:read", "user:read"] },
          path: (groupId, startIndex) =>
            `/api/admin/groups/${encodeURIComponent(groupId)}/members?startIndex=${startIndex}`,
        },
      },
      "src/pages/ffi/ffi-api.ts": {
        FFI_TYPES: ["ippt", "dental"],
        FFI_OUTCOMES: ["fit", "unfit"],
        FFI_HIGHER_AUTHORITIES: ["camo", "dis"],
        FFI_STATUSES: ["active", "inactive"],
      },
      "src/pages/numc/numc-api.ts": {
        NUMC_TYPES: ["submariner", "diving"],
        NUMC_OUTCOMES: ["fit", "waiver-granted"],
      },
      "src/pages/armc/armc-api.ts": {
        ARMC_BOARD_TYPES: ["aces"],
        ARMC_OUTCOMES: ["fit"],
      },
      "src/pages/medical-centre-api.ts": { MEDICAL_CENTRES: ["kranji-camp"] },
      "src/pages/medication/medication-api.ts": {
        DISPENSE_STATUSES: ["pending"],
      },
      "src/pages/audit/audit-api.ts": {
        AUDIT_OPERATIONS: ["LOGIN_SUCCESS", "LOGOUT"],
        AUDIT_EVENTS: {
          decode: json(pageOf("AuditEventPage", "events", decodeAuditEvent)),
          permission: "audit:read",
          path: (query) => `/api/admin/audit-events?${auditQuery(query)}`,
        },
      },
      "src/pages/patient-api.ts": {
        PATIENT: {
          decode: decodePatient,
          permission: "patient:read",
          path: (id) => `/api/patient/${encodeURIComponent(id)}`,
        },
      },
    },
    declared: {
      "src/pages/accounts/accounts-api.ts": [
        "USERS",
        "SET_GROUP_PERMISSIONS",
        "GROUP_MEMBERS",
      ],
      "src/pages/audit/audit-api.ts": ["AUDIT_EVENTS"],
      "src/pages/patient-api.ts": ["PATIENT"],
    },
  };
}

const USER_RESPONSE =
  "#/components/schemas/UserResponse/properties/permissions/items";
const DELEGATABLE = "#/components/schemas/DelegatablePermission";
const TOKEN_REQUEST = "#/components/schemas/ConnectorTokenPermissions/items";
const TOKEN_SUMMARY =
  "#/components/schemas/ConnectorTokenSummary/properties/permissions/items";
const AUDIT_OPERATION = "#/components/schemas/AuditOperation";
const LOCK_CAUSE = "#/components/schemas/AccountSummary/properties/lockCause";

const schemas = (doc) => doc.components.schemas;

describe("the SPA's vocabularies", () => {
  it("finds nothing when the SPA and the document agree", async () => {
    assert.deepEqual(await findMismatches(spa(), document()), []);
  });

  // Each row edits one side and names the findings that edit must produce.
  const cases = [
    [
      "the document gains a session Permission",
      (_spa, doc) =>
        schemas(doc).UserResponse.properties.permissions.items.enum.push(
          "ops:read",
        ),
      `PERMISSIONS (src/auth/api.ts) lacks "ops:read", which ${USER_RESPONSE} has`,
    ],
    [
      "the SPA gains a session Permission",
      (s) => s.modules["src/auth/api.ts"].PERMISSIONS.push("ops:read"),
      `PERMISSIONS (src/auth/api.ts) has "ops:read", which ${USER_RESPONSE} lacks`,
    ],
    [
      "the document gains a delegatable Permission the SPA cannot decode",
      (_spa, doc) => schemas(doc).DelegatablePermission.enum.push("ops:read"),
      `PERMISSIONS (src/auth/api.ts) lacks "ops:read", which ${DELEGATABLE} has`,
    ],
    [
      "the document gains a token Permission on the request",
      (_spa, doc) =>
        schemas(doc).ConnectorTokenPermissions.items.enum.push("group:read"),
      `TOKEN_PERMISSIONS (src/pages/accounts/accounts-api.ts) lacks "group:read", which ${TOKEN_REQUEST} has`,
    ],
    [
      "the document loses a token Permission from the listing",
      (_spa, doc) =>
        schemas(
          doc,
        ).ConnectorTokenSummary.properties.permissions.items.enum.pop(),
      `TOKEN_PERMISSIONS (src/pages/accounts/accounts-api.ts) has "user:write", which ${TOKEN_SUMMARY} lacks`,
    ],
    [
      "the SPA loses a token Permission",
      (s) =>
        s.modules[
          "src/pages/accounts/accounts-api.ts"
        ].TOKEN_PERMISSIONS.shift(),
      [
        `TOKEN_PERMISSIONS (src/pages/accounts/accounts-api.ts) lacks "user:read", which ${TOKEN_REQUEST} has`,
        `TOKEN_PERMISSIONS (src/pages/accounts/accounts-api.ts) lacks "user:read", which ${TOKEN_SUMMARY} has`,
      ],
    ],
    [
      "the document gains an audit operation",
      (_spa, doc) => schemas(doc).AuditOperation.enum.push("GROUP_DELETE"),
      `AUDIT_OPERATIONS (src/pages/audit/audit-api.ts) lacks "GROUP_DELETE", which ${AUDIT_OPERATION} has`,
    ],
    [
      "the SPA gains an audit operation",
      (s) =>
        s.modules["src/pages/audit/audit-api.ts"].AUDIT_OPERATIONS.push(
          "ROLE_GRANT",
        ),
      `AUDIT_OPERATIONS (src/pages/audit/audit-api.ts) has "ROLE_GRANT", which ${AUDIT_OPERATION} lacks`,
    ],
    [
      "the document gains a lock cause",
      (_spa, doc) =>
        schemas(doc).AccountSummary.properties.lockCause.enum.push("EXPIRY"),
      `LOCK_CAUSES (src/pages/accounts/accounts-api.ts) lacks "EXPIRY", which ${LOCK_CAUSE} has`,
    ],
    [
      "the SPA loses a lock cause",
      (s) => s.modules["src/pages/accounts/accounts-api.ts"].LOCK_CAUSES.pop(),
      `LOCK_CAUSES (src/pages/accounts/accounts-api.ts) lacks "DORMANCY", which ${LOCK_CAUSE} has`,
    ],
    // One case per FFI set: each is its own row of VOCABULARIES, so each is
    // the only proof its set is compared at all.
    ...[
      ["FFI_TYPES", "FfiType", "vocational"],
      ["FFI_OUTCOMES", "FfiOutcome", "request-waiver"],
      ["FFI_HIGHER_AUTHORITIES", "FfiHigherAuthority", "cnmo"],
      ["FFI_STATUSES", "FfiStatus", "lapsed"],
    ].map(([set, schema, code]) => [
      `the document gains a code of ${schema}`,
      (_spa, doc) => schemas(doc)[schema].enum.push(code),
      `${set} (src/pages/ffi/ffi-api.ts) lacks "${code}", which #/components/schemas/${schema} has`,
    ]),
    // One case per NUMC set, for the same reason.
    ...[
      ["NUMC_TYPES", "NumcType", "hyperbaric-chamber"],
      ["NUMC_OUTCOMES", "NumcOutcome", "waiver-rejected"],
    ].map(([set, schema, code]) => [
      `the document gains a code of ${schema}`,
      (_spa, doc) => schemas(doc)[schema].enum.push(code),
      `${set} (src/pages/numc/numc-api.ts) lacks "${code}", which #/components/schemas/${schema} has`,
    ]),
    [
      "the SPA gains a NUMC Outcome",
      (s) =>
        s.modules["src/pages/numc/numc-api.ts"].NUMC_OUTCOMES.push(
          "request-waiver",
        ),
      `NUMC_OUTCOMES (src/pages/numc/numc-api.ts) has "request-waiver", which #/components/schemas/NumcOutcome lacks`,
    ],
    // One case per ARMC set, for the same reason.
    ...[
      ["ARMC_BOARD_TYPES", "ArmcBoardType", "aircrew"],
      ["ARMC_OUTCOMES", "ArmcOutcome", "unfit"],
    ].map(([set, schema, code]) => [
      `the document gains a code of ${schema}`,
      (_spa, doc) => schemas(doc)[schema].enum.push(code),
      `${set} (src/pages/armc/armc-api.ts) lacks "${code}", which #/components/schemas/${schema} has`,
    ]),
    [
      "the SPA gains an ARMC Outcome",
      (s) =>
        s.modules["src/pages/armc/armc-api.ts"].ARMC_OUTCOMES.push(
          "waiver-granted",
        ),
      `ARMC_OUTCOMES (src/pages/armc/armc-api.ts) has "waiver-granted", which #/components/schemas/ArmcOutcome lacks`,
    ],
    // Medical Centre, the set an FFI's Submitting MC and a Medication Record's
    // Dispensary share, is its own row, in a module of its own.
    [
      "the document gains a Medical Centre",
      (_spa, doc) => schemas(doc).MedicalCentre.enum.push("khatib-camp"),
      `MEDICAL_CENTRES (src/pages/medical-centre-api.ts) lacks "khatib-camp", which #/components/schemas/MedicalCentre has`,
    ],
    [
      "the document gains a Dispense Status",
      (_spa, doc) => schemas(doc).DispenseStatus.enum.push("uncollected"),
      `DISPENSE_STATUSES (src/pages/medication/medication-api.ts) lacks "uncollected", which #/components/schemas/DispenseStatus has`,
    ],
    [
      "the SPA gains a Dispense Status",
      (s) =>
        s.modules[
          "src/pages/medication/medication-api.ts"
        ].DISPENSE_STATUSES.push("collected"),
      `DISPENSE_STATUSES (src/pages/medication/medication-api.ts) has "collected", which #/components/schemas/DispenseStatus lacks`,
    ],
    [
      "the SPA gains an FFI Status",
      (s) => s.modules["src/pages/ffi/ffi-api.ts"].FFI_STATUSES.push("expired"),
      `FFI_STATUSES (src/pages/ffi/ffi-api.ts) has "expired", which #/components/schemas/FfiStatus lacks`,
    ],
    [
      "the document no longer holds an enum the SPA mirrors",
      (_spa, doc) => delete schemas(doc).AuditOperation,
      `openapi.yaml has no enum at ${AUDIT_OPERATION}, which AUDIT_OPERATIONS (src/pages/audit/audit-api.ts) mirrors`,
    ],
    [
      "the SPA no longer exports a set the check reads",
      (s) => delete s.modules["src/pages/accounts/accounts-api.ts"].LOCK_CAUSES,
      "src/pages/accounts/accounts-api.ts exports no LOCK_CAUSES array of strings",
    ],
  ];

  for (const [name, edit, finding] of cases) {
    it(`names the mismatch when ${name}`, async () => {
      const [s, doc] = [spa(), document()];
      edit(s, doc);

      assert.deepEqual(await findMismatches(s, doc), [finding].flat());
    });
  }
});

describe("the SPA's request declarations", () => {
  it("names a declaration whose Permission is not the one its operation requires", async () => {
    const doc = document();
    doc.paths["/api/admin/accounts"].get.security = [
      { sessionCookie: ["group:read"] },
    ];

    assert.deepEqual(await findMismatches(spa(), doc), [
      'USERS (src/pages/accounts/accounts-api.ts) requires "user:read" for GET /api/admin/accounts, but openapi.yaml requires sessionCookie: [group:read]',
    ]);
  });

  it("names a Superuser declaration whose operation requires a Permission instead", async () => {
    const doc = document();
    doc.paths["/api/admin/groups/{id}/permissions"].put.security = [
      { sessionCookie: ["group:read"] },
    ];

    assert.deepEqual(await findMismatches(spa(), doc), [
      'SET_GROUP_PERMISSIONS (src/pages/accounts/accounts-api.ts) requires "superuser" for PUT /api/admin/groups/{id}/permissions, but openapi.yaml requires sessionCookie: [group:read]',
    ]);
  });

  it("names a declaration requiring two Permissions whose operation requires only one", async () => {
    const doc = document();
    doc.paths["/api/admin/groups/{id}/members"].get.security = [
      { sessionCookie: ["group:read"] },
    ];

    assert.deepEqual(await findMismatches(spa(), doc), [
      'GROUP_MEMBERS (src/pages/accounts/accounts-api.ts) requires "group:read" and "user:read" for GET /api/admin/groups/{id}/members, but openapi.yaml requires sessionCookie: [group:read]',
    ]);
  });

  it("names a declaration requiring one Permission whose operation requires two", async () => {
    const doc = document();
    doc.paths["/api/admin/accounts"].get.security = [
      { sessionCookie: ["user:read", "group:read"] },
    ];

    assert.deepEqual(await findMismatches(spa(), doc), [
      'USERS (src/pages/accounts/accounts-api.ts) requires "user:read" for GET /api/admin/accounts, but openapi.yaml requires sessionCookie: [user:read, group:read]',
    ]);
  });

  it("holds the Permissions an operation requires together as a set, in any order", async () => {
    const doc = document();
    doc.paths["/api/admin/groups/{id}/members"].get.security = [
      { sessionCookie: ["user:read", "group:read"] },
    ];

    assert.deepEqual(await findMismatches(spa(), doc), []);
  });

  it("does not read two Permissions offered as alternatives as both required", async () => {
    const doc = document();
    doc.paths["/api/admin/groups/{id}/members"].get.security = [
      { sessionCookie: ["group:read"] },
      { sessionCookie: ["user:read"] },
    ];

    assert.deepEqual(await findMismatches(spa(), doc), [
      'GROUP_MEMBERS (src/pages/accounts/accounts-api.ts) requires "group:read" and "user:read" for GET /api/admin/groups/{id}/members, but openapi.yaml requires sessionCookie: [group:read] or sessionCookie: [user:read]',
    ]);
  });

  it("names a declaration whose Permissions name one twice in place of a second", async () => {
    const s = spa();
    s.modules["src/pages/accounts/accounts-api.ts"].GROUP_MEMBERS.permission = {
      allOf: ["group:read", "group:read"],
    };

    assert.deepEqual(await findMismatches(s, document()), [
      'GROUP_MEMBERS (src/pages/accounts/accounts-api.ts) requires "group:read" and "group:read" for GET /api/admin/groups/{id}/members, but openapi.yaml requires sessionCookie: [group:read, user:read]',
    ]);
  });

  it("does not take a requirement naming no Permission for a declaration", async () => {
    const s = spa();
    s.modules["src/pages/accounts/accounts-api.ts"].GROUP_MEMBERS.permission = {
      allOf: [],
    };

    assert.deepEqual(await findMismatches(s, document()), [
      "src/pages/accounts/accounts-api.ts declares GROUP_MEMBERS, which did not evaluate to a request declaration",
    ]);
  });

  it("holds a declaration to the document's own security when its operation names none", async () => {
    const doc = document();
    delete doc.paths["/api/admin/accounts"].get.security;

    assert.deepEqual(await findMismatches(spa(), doc), [
      'USERS (src/pages/accounts/accounts-api.ts) requires "user:read" for GET /api/admin/accounts, but openapi.yaml requires sessionCookie: []',
    ]);
  });

  it("names a declaration whose operation a connector token is required for", async () => {
    const doc = document();
    doc.paths["/api/admin/accounts"].get.security = [
      { connectorBearer: ["user:read"] },
    ];

    assert.deepEqual(await findMismatches(spa(), doc), [
      'USERS (src/pages/accounts/accounts-api.ts) requires "user:read" for GET /api/admin/accounts, but openapi.yaml requires connectorBearer: [user:read]',
    ]);
  });

  it("names a declaration the document has no operation for", async () => {
    const doc = document();
    delete doc.paths["/api/admin/groups/{id}/permissions"].put;

    assert.deepEqual(await findMismatches(spa(), doc), [
      "SET_GROUP_PERMISSIONS (src/pages/accounts/accounts-api.ts) sends PUT /api/admin/groups/ARG/permissions, which no operation in openapi.yaml serves",
    ]);
  });

  it("names a declaration whose path does not resolve to one", async () => {
    const s = spa();
    s.modules["src/pages/audit/audit-api.ts"].AUDIT_EVENTS.path = (query) =>
      query.path;

    assert.deepEqual(await findMismatches(s, document()), [
      "AUDIT_EVENTS (src/pages/audit/audit-api.ts) builds no absolute path from placeholder arguments",
    ]);
  });

  it("names a declaration its source exports that does not evaluate to one", async () => {
    const s = spa();
    delete s.modules["src/pages/accounts/accounts-api.ts"].USERS;

    assert.deepEqual(await findMismatches(s, document()), [
      "src/pages/accounts/accounts-api.ts declares USERS, which did not evaluate to a request declaration",
    ]);
  });

  it("refuses to pass an SPA in which it finds no declaration at all", async () => {
    const s = spa();
    delete s.modules["src/pages/accounts/accounts-api.ts"].USERS;
    delete s.modules["src/pages/accounts/accounts-api.ts"]
      .SET_GROUP_PERMISSIONS;
    delete s.modules["src/pages/accounts/accounts-api.ts"].GROUP_MEMBERS;
    delete s.modules["src/pages/audit/audit-api.ts"].AUDIT_EVENTS;
    delete s.modules["src/pages/patient-api.ts"].PATIENT;
    s.declared = {};

    assert.deepEqual(await findMismatches(s, document()), [
      "found no request declaration in the SPA; the check would hold nothing",
    ]);
  });
});

describe("the SPA's response shapes", () => {
  const ACCOUNTS = "GET /api/admin/accounts 200 application/json";
  const USERS = "USERS (src/pages/accounts/accounts-api.ts)";
  const AUDIT = "AUDIT_EVENTS (src/pages/audit/audit-api.ts)";

  it("counts each decoder that reads every sample of its response, and the samples it read", async () => {
    const { held } = await holdContract(spa(), document());

    assert.deepEqual([held.decoders, held.samples], [5, 10]);
  });

  // Each row edits one side and names the one finding that edit must produce.
  const cases = [
    [
      "the document renames a field the decoder reads",
      (_spa, doc) => {
        const account = schemas(doc).AccountSummary;
        account.properties.login = account.properties.userName;
        delete account.properties.userName;
        account.required[account.required.indexOf("userName")] = "login";
      },
      `${USERS} refuses the every-property sample of ${ACCOUNTS}: AccountSummary.userName is not a string`,
    ],
    [
      "the document removes a field the decoder reads",
      (_spa, doc) => {
        const event = schemas(doc).AuditEvent;
        delete event.properties.actorId;
        event.required.pop();
      },
      `${AUDIT} refuses the every-property sample of GET /api/admin/audit-events 200 application/json: AuditEvent.actorId is not null or a uuid`,
    ],
    [
      "the document adds a field a decoder refusing unknown fields does not know",
      (_spa, doc) => {
        schemas(doc).GroupMember.properties.email = { type: "string" };
      },
      "GROUP_MEMBERS (src/pages/accounts/accounts-api.ts) refuses the every-property sample of GET /api/admin/groups/{id}/members 200 application/json: GroupMember.email is not expected",
    ],
    [
      "the document makes a field the decoder requires optional",
      (_spa, doc) => {
        const { required } = schemas(doc).AccountSummary;
        required.splice(required.indexOf("locked"), 1);
      },
      `${USERS} refuses the required-only sample of ${ACCOUNTS}: AccountSummary.locked is not a boolean`,
    ],
    [
      "the document makes a field the decoder reads as a string nullable",
      (_spa, doc) => {
        schemas(doc).GroupSummary.properties.displayName.type = [
          "string",
          "null",
        ];
      },
      "SET_GROUP_PERMISSIONS (src/pages/accounts/accounts-api.ts) refuses the required-only sample of PUT /api/admin/groups/{id}/permissions 200 application/json: GroupSummary.displayName is not a string",
    ],
    [
      "the document narrows a date-time the decoder reads to a date",
      (_spa, doc) => {
        schemas(doc).AuditEvent.properties.occurredAt.format = "date";
      },
      `${AUDIT} refuses the every-property sample of GET /api/admin/audit-events 200 application/json: AuditEvent.occurredAt is not a date-time`,
    ],
    [
      "the document serves a body under another media type than the decoder reads",
      (_spa, doc) => {
        const { content } = doc.paths["/api/patient/{id}"].get.responses[200];
        content["application/json"] = content["application/fhir+json"];
        delete content["application/fhir+json"];
      },
      "PATIENT (src/pages/patient-api.ts) refuses the every-property sample of GET /api/patient/{id} 200 application/json: Patient is not served as application/fhir+json",
    ],
    [
      "the document uses a schema keyword the sampler does not implement",
      (_spa, doc) => {
        schemas(doc).AccountSummary.properties.displayName = {
          oneOf: [{ type: "string" }, { type: "null" }],
        };
      },
      `${USERS} cannot be held to ${ACCOUNTS}: the sampler does not implement "oneOf" (at #/components/schemas/AccountSummary/properties/displayName)`,
    ],
    [
      "the document uses a string format the sampler does not implement",
      (_spa, doc) => {
        schemas(doc).AccountSummary.properties.userName.format = "email";
      },
      `${USERS} cannot be held to ${ACCOUNTS}: the sampler does not implement format "email" (at #/components/schemas/AccountSummary/properties/userName)`,
    ],
    [
      "the document holds a string to a pattern the sampler's string does not match",
      (_spa, doc) => {
        schemas(doc).AccountSummary.properties.userName.pattern = "^[0-9]+$";
      },
      `${USERS} cannot be held to ${ACCOUNTS}: the sampler's "sample" does not match the pattern "^[0-9]+$" (at #/components/schemas/AccountSummary/properties/userName)`,
    ],
    [
      "the document requires a property it gives no schema",
      (_spa, doc) => {
        schemas(doc).GroupMember.required.push("active");
      },
      'GROUP_MEMBERS (src/pages/accounts/accounts-api.ts) cannot be held to GET /api/admin/groups/{id}/members 200 application/json: required "active" has no schema (at #/components/schemas/GroupMember)',
    ],
    [
      "the document documents no success body for a declaration with a decoder",
      (_spa, doc) => {
        doc.paths["/api/admin/groups/{id}/members"].get.responses = {
          204: { description: "Nothing." },
          400: body(ref("GroupMember")),
        };
      },
      "GROUP_MEMBERS (src/pages/accounts/accounts-api.ts) decodes a body, but GET /api/admin/groups/{id}/members documents no success body",
    ],
    [
      "the document documents a second success body the decoder refuses",
      (_spa, doc) => {
        doc.paths["/api/admin/groups/{id}/members"].get.responses[203] = body(
          ref("GroupMember"),
        );
      },
      "GROUP_MEMBERS (src/pages/accounts/accounts-api.ts) refuses the every-property sample of GET /api/admin/groups/{id}/members 203 application/json: GroupMemberPage.totalResults is not an integer",
    ],
    [
      "the document gives a property a type of several kinds",
      (_spa, doc) => {
        schemas(doc).GroupMember.properties.userName.type = [
          "string",
          "integer",
        ];
      },
      "GROUP_MEMBERS (src/pages/accounts/accounts-api.ts) cannot be held to GET /api/admin/groups/{id}/members 200 application/json: the sampler does not implement a type of several kinds (at #/components/schemas/GroupMember/properties/userName)",
    ],
    [
      "the document gives a property no type",
      (_spa, doc) => {
        schemas(doc).GroupMember.properties.userName = {
          description: "Anything.",
        };
      },
      "GROUP_MEMBERS (src/pages/accounts/accounts-api.ts) cannot be held to GET /api/admin/groups/{id}/members 200 application/json: the sampler does not implement a schema with no type (at #/components/schemas/GroupMember/properties/userName)",
    ],
    [
      "the document uses an integer format the sampler does not implement",
      (_spa, doc) => {
        schemas(doc).GroupMemberPage.properties.totalResults.format = "uint8";
      },
      'GROUP_MEMBERS (src/pages/accounts/accounts-api.ts) cannot be held to GET /api/admin/groups/{id}/members 200 application/json: the sampler does not implement format "uint8" (at #/components/schemas/GroupMemberPage/properties/totalResults)',
    ],
    [
      "the document gives a property a type the sampler does not implement",
      (_spa, doc) => {
        schemas(doc).GroupMember.properties.userName.type = "number";
      },
      'GROUP_MEMBERS (src/pages/accounts/accounts-api.ts) cannot be held to GET /api/admin/groups/{id}/members 200 application/json: the sampler does not implement type "number" (at #/components/schemas/GroupMember/properties/userName)',
    ],
    [
      "the document refers to a schema it does not hold",
      (_spa, doc) => {
        delete schemas(doc).GroupMember;
      },
      "GROUP_MEMBERS (src/pages/accounts/accounts-api.ts) cannot be held to GET /api/admin/groups/{id}/members 200 application/json: there is no schema at #/components/schemas/GroupMember",
    ],
    [
      "a declaration's decode is not a function",
      (s) => {
        s.modules["src/pages/accounts/accounts-api.ts"].USERS.decode =
          "decodeUsers";
      },
      `${USERS} has a decode that is not a function`,
    ],
    [
      "a read declares no decoder",
      (s) =>
        delete s.modules["src/pages/accounts/accounts-api.ts"].USERS.decode,
      `${USERS} reads GET /api/admin/accounts with no decoder, so the check cannot hold its response`,
    ],
  ];

  for (const [name, edit, finding] of cases) {
    it(`names the mismatch when ${name}`, async () => {
      const [s, doc] = [spa(), document()];
      edit(s, doc);

      assert.deepEqual(await findMismatches(s, doc), [finding]);
    });
  }

  it("finds nothing when the document adds a field a decoder that drops unknown fields does not read", async () => {
    const doc = document();
    schemas(doc).AccountSummary.properties.email = { type: "string" };

    assert.deepEqual(await findMismatches(spa(), doc), []);
  });

  it("holds nothing of the response of a write that declares no decoder", async () => {
    const s = spa();
    delete s.modules["src/pages/accounts/accounts-api.ts"].SET_GROUP_PERMISSIONS
      .decode;

    assert.deepEqual(await findMismatches(s, document()), []);
  });

  // Each row gives a Group's property a constraint, and reads the Group with a
  // decoder that accepts only a value within it: both samples must honour it.
  const honoured = [
    [
      "an array of at least minItems items",
      { type: "array", minItems: 2, items: { type: "string" } },
      (value) => Array.isArray(value) && value.length === 2,
    ],
    [
      "an array of no more than maxItems items",
      { type: "array", maxItems: 0, items: { type: "string" } },
      (value) => Array.isArray(value) && value.length === 0,
    ],
    [
      "a string of at least minLength characters",
      { type: "string", minLength: 9 },
      (value) => typeof value === "string" && value.length === 9,
    ],
    [
      "a string of no more than maxLength characters",
      { type: "string", maxLength: 2 },
      (value) => typeof value === "string" && value.length === 2,
    ],
    [
      "an integer no less than its minimum",
      { type: "integer", minimum: 5 },
      (value) => value === 5,
    ],
    [
      "an integer no more than its maximum",
      { type: "integer", maximum: -3 },
      (value) => value === -3,
    ],
    [
      "an enum whose one member is null",
      { enum: [null] },
      (value) => value === null,
    ],
    ["a value of type null", { type: "null" }, (value) => value === null],
  ];

  for (const [name, schema, accepts] of honoured) {
    it(`builds samples a decoder reading ${name} accepts`, async () => {
      const [s, doc] = [spa(), document()];
      schemas(doc).GroupSummary.properties.limited = schema;
      schemas(doc).GroupSummary.required.push("limited");
      s.modules[
        "src/pages/accounts/accounts-api.ts"
      ].SET_GROUP_PERMISSIONS.decode = json(
        objectOf("GroupSummary", { limited: is(name, accepts) }),
      );

      assert.deepEqual(await findMismatches(s, doc), []);
    });
  }
});

describe("the SPA's query parameters", () => {
  const USERS = "USERS (src/pages/accounts/accounts-api.ts)";
  const AUDIT = "AUDIT_EVENTS (src/pages/audit/audit-api.ts)";

  it("counts each declaration whose query parameters are all documented, and those parameters", async () => {
    const { held } = await holdContract(spa(), document());

    assert.deepEqual([held.querying, held.parameters], [3, 5]);
  });

  const cases = [
    [
      "the document renames a parameter a path sends only when its filter is set",
      (_spa, doc) => {
        doc.components.parameters.Search.name = "q";
      },
      `${USERS} sends query parameter "search" to GET /api/admin/accounts, which openapi.yaml does not document`,
    ],
    [
      "the SPA renames a parameter a path sends once per value chosen",
      (s) => {
        s.modules["src/pages/audit/audit-api.ts"].AUDIT_EVENTS.path = (query) =>
          `/api/admin/audit-events?${auditQuery(query).replaceAll("operation=", "operations=")}`;
      },
      `${AUDIT} sends query parameter "operations" to GET /api/admin/audit-events, which openapi.yaml does not document`,
    ],
    [
      "a path sends a parameter for each code its filter holds",
      (s) => {
        s.modules["src/pages/audit/audit-api.ts"].AUDIT_EVENTS.path = ({
          results,
        }) =>
          `/api/admin/audit-events?${["SUCCESS", "FAILURE"]
            .filter((code) => results.includes(code))
            .map((code) => `result=${code}`)
            .join("&")}`;
      },
      `${AUDIT} sends query parameter "result" to GET /api/admin/audit-events, which openapi.yaml does not document`,
    ],
    [
      "a path sends a parameter only when its filter holds any value",
      (s) => {
        s.modules["src/pages/audit/audit-api.ts"].AUDIT_EVENTS.path = ({
          actors,
        }) =>
          `/api/admin/audit-events?${actors.length > 0 ? `actor=${actors}` : ""}`;
      },
      `${AUDIT} sends query parameter "actor" to GET /api/admin/audit-events, which openapi.yaml does not document`,
    ],
    [
      "a path builds a parameter's name from its arguments",
      (s) => {
        s.modules["src/pages/audit/audit-api.ts"].AUDIT_EVENTS.path = (query) =>
          `/api/admin/audit-events?${query.search}`;
      },
      `${AUDIT} builds a query parameter's name from its arguments, so the check cannot hold what it sends to GET /api/admin/audit-events`,
    ],
    [
      "the document requires a parameter the path never sends",
      (_spa, doc) => {
        doc.paths["/api/admin/accounts"].get.parameters.push({
          name: "q",
          in: "query",
          required: true,
        });
      },
      `${USERS} never sends query parameter "q", which GET /api/admin/accounts requires`,
    ],
  ];

  for (const [name, edit, finding] of cases) {
    it(`names the mismatch when ${name}`, async () => {
      const [s, doc] = [spa(), document()];
      edit(s, doc);

      assert.deepEqual(await findMismatches(s, doc), [finding]);
    });
  }
});

describe("declaredNames", () => {
  it("lists every exported read and write declaration a source names, however its type wraps", async () => {
    const source = `
import type { ReadDeclaration, WriteDeclaration } from "@/auth/declarations";

export const USERS: ReadDeclaration<UserRow[]> = { permission: "user:read" };
const LOCAL: ReadDeclaration<Row> = { permission: "user:read" };
export const SET_GROUP_PERMISSIONS: WriteDeclaration<
  [groupId: string, permissions: readonly Permission[]],
  GroupRow
> = { permission: SUPERUSER };
export const PERMISSIONS = ["user:read"] as const;
export const REVOKE_TOKEN: WriteDeclaration<[connectorId: string, tokenId: string]> = {};
`;

    assert.deepEqual(declaredNames(source), [
      "USERS",
      "SET_GROUP_PERMISSIONS",
      "REVOKE_TOKEN",
    ]);
  });

  it("lists a paged declaration, its generic arguments on one line or across several", async () => {
    const source = `
import type { PagedDeclaration } from "@/pages/paged-read";

export const USERS: PagedDeclaration<UserRow, [query: ListingQuery]> = { permission: "user:read" };
export const GROUP_MEMBERS: PagedDeclaration<
  MemberRow,
  [groupId: string],
  { allOf: ["group:read", "user:read"] }
> = { permission: { allOf: ["group:read", "user:read"] } };
`;

    assert.deepEqual(declaredNames(source), ["USERS", "GROUP_MEMBERS"]);
  });
});

describe("discover", () => {
  /** A module whose one request declaration is a paged read, typed across several lines. */
  const PAGED_ONLY = [
    "src/pages/tokens/tokens-api.ts",
    `
import type { PagedDeclaration } from "@/pages/paged-read";

export const TOKENS: PagedDeclaration<
  TokenRow,
  [connectorId: string]
> = { permission: "connector:read", path: (id) => \`/api/admin/connectors/\${id}/tokens\` };
`,
  ];

  it("loads a module whose only request declaration is a paged one, beside the vocabulary modules", async () => {
    const { toLoad } = discover([
      PAGED_ONLY,
      ["src/lib/format.ts", "export const format = (x) => String(x);\n"],
    ]);

    assert.deepEqual(toLoad, [
      "src/auth/api.ts",
      "src/pages/accounts/accounts-api.ts",
      "src/pages/audit/audit-api.ts",
      "src/pages/ffi/ffi-api.ts",
      "src/pages/medical-centre-api.ts",
      "src/pages/numc/numc-api.ts",
      "src/pages/armc/armc-api.ts",
      "src/pages/medication/medication-api.ts",
      "src/pages/tokens/tokens-api.ts",
    ]);
  });

  it("holds a paged-only module's declaration, loaded as main.mjs loads it, to its operation's security", async () => {
    const sources = [PAGED_ONLY];
    const evaluated = {
      ...spa().modules,
      "src/pages/tokens/tokens-api.ts": {
        TOKENS: {
          decode: json(pageOf("TokenPage", "tokens", (token) => token)),
          permission: "connector:read",
          path: (id) => `/api/admin/connectors/${id}/tokens?startIndex=1`,
        },
      },
    };
    const { declared, toLoad } = discover(sources);
    const modules = Object.fromEntries(
      toLoad.map((file) => [file, evaluated[file] ?? {}]),
    );
    const doc = document();
    doc.components.schemas.Token = { type: "object" };
    doc.paths["/api/admin/connectors/{id}/tokens"] = {
      get: {
        security: [{ sessionCookie: ["superuser"] }],
        parameters: [{ $ref: "#/components/parameters/StartIndex" }],
        responses: { 200: body(pageSchema("tokens", "Token")) },
      },
    };

    assert.deepEqual(await findMismatches({ modules, declared }, doc), [
      'TOKENS (src/pages/tokens/tokens-api.ts) requires "connector:read" for GET /api/admin/connectors/{id}/tokens, but openapi.yaml requires sessionCookie: [superuser]',
    ]);
  });

  it("names a paged declaration its source exports that does not evaluate to one", async () => {
    const { declared } = discover([PAGED_ONLY]);
    const s = spa();
    s.modules["src/pages/tokens/tokens-api.ts"] = {
      TOKENS: { permission: "connector:read", path: "/api/admin/connectors" },
    };
    s.declared = { ...s.declared, ...declared };

    assert.deepEqual(await findMismatches(s, document()), [
      "src/pages/tokens/tokens-api.ts declares TOKENS, which did not evaluate to a request declaration",
    ]);
  });
});
