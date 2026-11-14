// Tests for the SPA–API contract check (./check.mjs). Run by `make verify-contract`
// before the check itself, with Node's own runner: `node --test scripts/spa-contract/`.
//
// Every test hands `findMismatches` an SPA and an API document that agree, less
// the one difference it names, and asserts on the findings the check reports.

import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { declaredNames, discover, findMismatches } from "./check.mjs";

/** An API document holding every enum and operation the check reads, agreeing with `spa()`. */
function document() {
  return {
    security: [{ sessionCookie: [] }],
    paths: {
      "/api/admin/accounts": {
        get: { security: [{ sessionCookie: ["user:read"] }] },
      },
      "/api/admin/groups/{id}/permissions": {
        put: { security: [{ sessionCookie: ["superuser"] }] },
      },
      "/api/admin/groups/{id}/members": {
        get: { security: [{ sessionCookie: ["group:read", "user:read"] }] },
      },
      "/api/admin/audit-events": {
        get: { security: [{ sessionCookie: ["audit:read"] }] },
      },
    },
    components: {
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
        AccountSummary: {
          properties: { lockCause: { enum: ["FAILURES", "DORMANCY", null] } },
        },
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

/** The SPA's modules, as the check reads them: each module's exports, and the declarations its source names. */
function spa() {
  return {
    modules: {
      "src/auth/api.ts": { PERMISSIONS: ["group:read", "user:read"] },
      "src/pages/accounts/accounts-api.ts": {
        TOKEN_PERMISSIONS: ["user:read", "user:write"],
        LOCK_CAUSES: ["FAILURES", "DORMANCY"],
        USERS: { permission: "user:read", path: () => "/api/admin/accounts" },
        SET_GROUP_PERMISSIONS: {
          method: "PUT",
          permission: "superuser",
          path: (groupId) =>
            `/api/admin/groups/${encodeURIComponent(groupId)}/permissions`,
        },
        GROUP_MEMBERS: {
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
          permission: "audit:read",
          path: (query) => `/api/admin/audit-events?${query.search}`,
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
  it("finds nothing when the SPA and the document agree", () => {
    assert.deepEqual(findMismatches(spa(), document()), []);
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
      (s) => s.modules["src/pages/numc/numc-api.ts"].NUMC_OUTCOMES.push("request-waiver"),
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
        s.modules["src/pages/medication/medication-api.ts"].DISPENSE_STATUSES.push(
          "collected",
        ),
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
    it(`names the mismatch when ${name}`, () => {
      const [s, doc] = [spa(), document()];
      edit(s, doc);

      assert.deepEqual(findMismatches(s, doc), [finding].flat());
    });
  }
});

describe("the SPA's request declarations", () => {
  it("names a declaration whose Permission is not the one its operation requires", () => {
    const doc = document();
    doc.paths["/api/admin/accounts"].get.security = [
      { sessionCookie: ["group:read"] },
    ];

    assert.deepEqual(findMismatches(spa(), doc), [
      'USERS (src/pages/accounts/accounts-api.ts) requires "user:read" for GET /api/admin/accounts, but openapi.yaml requires sessionCookie: [group:read]',
    ]);
  });

  it("names a Superuser declaration whose operation requires a Permission instead", () => {
    const doc = document();
    doc.paths["/api/admin/groups/{id}/permissions"].put.security = [
      { sessionCookie: ["group:read"] },
    ];

    assert.deepEqual(findMismatches(spa(), doc), [
      'SET_GROUP_PERMISSIONS (src/pages/accounts/accounts-api.ts) requires "superuser" for PUT /api/admin/groups/{id}/permissions, but openapi.yaml requires sessionCookie: [group:read]',
    ]);
  });

  it("names a declaration requiring two Permissions whose operation requires only one", () => {
    const doc = document();
    doc.paths["/api/admin/groups/{id}/members"].get.security = [
      { sessionCookie: ["group:read"] },
    ];

    assert.deepEqual(findMismatches(spa(), doc), [
      'GROUP_MEMBERS (src/pages/accounts/accounts-api.ts) requires "group:read" and "user:read" for GET /api/admin/groups/{id}/members, but openapi.yaml requires sessionCookie: [group:read]',
    ]);
  });

  it("names a declaration requiring one Permission whose operation requires two", () => {
    const doc = document();
    doc.paths["/api/admin/accounts"].get.security = [
      { sessionCookie: ["user:read", "group:read"] },
    ];

    assert.deepEqual(findMismatches(spa(), doc), [
      'USERS (src/pages/accounts/accounts-api.ts) requires "user:read" for GET /api/admin/accounts, but openapi.yaml requires sessionCookie: [user:read, group:read]',
    ]);
  });

  it("holds the Permissions an operation requires together as a set, in any order", () => {
    const doc = document();
    doc.paths["/api/admin/groups/{id}/members"].get.security = [
      { sessionCookie: ["user:read", "group:read"] },
    ];

    assert.deepEqual(findMismatches(spa(), doc), []);
  });

  it("does not read two Permissions offered as alternatives as both required", () => {
    const doc = document();
    doc.paths["/api/admin/groups/{id}/members"].get.security = [
      { sessionCookie: ["group:read"] },
      { sessionCookie: ["user:read"] },
    ];

    assert.deepEqual(findMismatches(spa(), doc), [
      'GROUP_MEMBERS (src/pages/accounts/accounts-api.ts) requires "group:read" and "user:read" for GET /api/admin/groups/{id}/members, but openapi.yaml requires sessionCookie: [group:read] or sessionCookie: [user:read]',
    ]);
  });

  it("names a declaration whose Permissions name one twice in place of a second", () => {
    const s = spa();
    s.modules["src/pages/accounts/accounts-api.ts"].GROUP_MEMBERS.permission = {
      allOf: ["group:read", "group:read"],
    };

    assert.deepEqual(findMismatches(s, document()), [
      'GROUP_MEMBERS (src/pages/accounts/accounts-api.ts) requires "group:read" and "group:read" for GET /api/admin/groups/{id}/members, but openapi.yaml requires sessionCookie: [group:read, user:read]',
    ]);
  });

  it("does not take a requirement naming no Permission for a declaration", () => {
    const s = spa();
    s.modules["src/pages/accounts/accounts-api.ts"].GROUP_MEMBERS.permission = {
      allOf: [],
    };

    assert.deepEqual(findMismatches(s, document()), [
      "src/pages/accounts/accounts-api.ts declares GROUP_MEMBERS, which did not evaluate to a request declaration",
    ]);
  });

  it("holds a declaration to the document's own security when its operation names none", () => {
    const doc = document();
    delete doc.paths["/api/admin/accounts"].get.security;

    assert.deepEqual(findMismatches(spa(), doc), [
      'USERS (src/pages/accounts/accounts-api.ts) requires "user:read" for GET /api/admin/accounts, but openapi.yaml requires sessionCookie: []',
    ]);
  });

  it("names a declaration whose operation a connector token is required for", () => {
    const doc = document();
    doc.paths["/api/admin/accounts"].get.security = [
      { connectorBearer: ["user:read"] },
    ];

    assert.deepEqual(findMismatches(spa(), doc), [
      'USERS (src/pages/accounts/accounts-api.ts) requires "user:read" for GET /api/admin/accounts, but openapi.yaml requires connectorBearer: [user:read]',
    ]);
  });

  it("names a declaration the document has no operation for", () => {
    const doc = document();
    delete doc.paths["/api/admin/groups/{id}/permissions"].put;

    assert.deepEqual(findMismatches(spa(), doc), [
      "SET_GROUP_PERMISSIONS (src/pages/accounts/accounts-api.ts) sends PUT /api/admin/groups/ARG/permissions, which no operation in openapi.yaml serves",
    ]);
  });

  it("names a declaration whose path does not resolve to one", () => {
    const s = spa();
    s.modules["src/pages/audit/audit-api.ts"].AUDIT_EVENTS.path = (query) =>
      query.path;

    assert.deepEqual(findMismatches(s, document()), [
      "AUDIT_EVENTS (src/pages/audit/audit-api.ts) builds no absolute path from placeholder arguments",
    ]);
  });

  it("names a declaration its source exports that does not evaluate to one", () => {
    const s = spa();
    delete s.modules["src/pages/accounts/accounts-api.ts"].USERS;

    assert.deepEqual(findMismatches(s, document()), [
      "src/pages/accounts/accounts-api.ts declares USERS, which did not evaluate to a request declaration",
    ]);
  });

  it("refuses to pass an SPA in which it finds no declaration at all", () => {
    const s = spa();
    delete s.modules["src/pages/accounts/accounts-api.ts"].USERS;
    delete s.modules["src/pages/accounts/accounts-api.ts"]
      .SET_GROUP_PERMISSIONS;
    delete s.modules["src/pages/accounts/accounts-api.ts"].GROUP_MEMBERS;
    delete s.modules["src/pages/audit/audit-api.ts"].AUDIT_EVENTS;
    s.declared = {};

    assert.deepEqual(findMismatches(s, document()), [
      "found no request declaration in the SPA; the check would hold nothing",
    ]);
  });
});

describe("declaredNames", () => {
  it("lists every exported read and write declaration a source names, however its type wraps", () => {
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

  it("lists a paged declaration, its generic arguments on one line or across several", () => {
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

  it("loads a module whose only request declaration is a paged one, beside the vocabulary modules", () => {
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

  it("holds a paged-only module's declaration, loaded as main.mjs loads it, to its operation's security", () => {
    const sources = [PAGED_ONLY];
    const evaluated = {
      ...spa().modules,
      "src/pages/tokens/tokens-api.ts": {
        TOKENS: {
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
    doc.paths["/api/admin/connectors/{id}/tokens"] = {
      get: { security: [{ sessionCookie: ["superuser"] }] },
    };

    assert.deepEqual(findMismatches({ modules, declared }, doc), [
      'TOKENS (src/pages/tokens/tokens-api.ts) requires "connector:read" for GET /api/admin/connectors/{id}/tokens, but openapi.yaml requires sessionCookie: [superuser]',
    ]);
  });

  it("names a paged declaration its source exports that does not evaluate to one", () => {
    const { declared } = discover([PAGED_ONLY]);
    const s = spa();
    s.modules["src/pages/tokens/tokens-api.ts"] = {
      TOKENS: { permission: "connector:read", path: "/api/admin/connectors" },
    };
    s.declared = { ...s.declared, ...declared };

    assert.deepEqual(findMismatches(s, document()), [
      "src/pages/tokens/tokens-api.ts declares TOKENS, which did not evaluate to a request declaration",
    ]);
  });
});
