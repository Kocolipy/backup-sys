// The SPA–API contract check: holds what the SPA mirrors by hand — its closed
// sets, each request declaration's requirement, the query parameters it sends
// and the shape of each response it decodes — against the API document,
// backend/docs/openapi.yaml. Pure — it is handed the SPA's evaluated modules and
// the parsed document, and returns every disagreement as one readable line.
// `discover` picks, from the SPA's source text, the modules to evaluate;
// ./main.mjs does the loading; ./check.test.mjs pins this.

/**
 * Each closed set the SPA decodes strictly, the module that exports it, and
 * the enums in the document it mirrors. `equal` holds the two sets to each
 * other in both directions; `covers` only requires the SPA's set to hold every
 * value of the document's, for a set the SPA also decodes another response with.
 */
export const VOCABULARIES = [
  {
    set: "PERMISSIONS",
    module: "src/auth/api.ts",
    mirrors: [
      {
        pointer:
          "#/components/schemas/UserResponse/properties/permissions/items",
        relation: "equal",
      },
      {
        pointer: "#/components/schemas/DelegatablePermission",
        relation: "covers",
      },
    ],
  },
  {
    set: "TOKEN_PERMISSIONS",
    module: "src/pages/accounts/accounts-api.ts",
    mirrors: [
      {
        pointer: "#/components/schemas/ConnectorTokenPermissions/items",
        relation: "equal",
      },
      {
        pointer:
          "#/components/schemas/ConnectorTokenSummary/properties/permissions/items",
        relation: "equal",
      },
    ],
  },
  {
    set: "AUDIT_OPERATIONS",
    module: "src/pages/audit/audit-api.ts",
    mirrors: [
      { pointer: "#/components/schemas/AuditOperation", relation: "equal" },
    ],
  },
  {
    set: "LOCK_CAUSES",
    module: "src/pages/accounts/accounts-api.ts",
    mirrors: [
      {
        pointer: "#/components/schemas/AccountSummary/properties/lockCause",
        relation: "equal",
      },
    ],
  },
  // An FFI's four closed sets of its own, each carried as its code.
  ...[
    ["FFI_TYPES", "FfiType"],
    ["FFI_OUTCOMES", "FfiOutcome"],
    ["FFI_HIGHER_AUTHORITIES", "FfiHigherAuthority"],
    ["FFI_STATUSES", "FfiStatus"],
  ].map(([set, schema]) => ({
    set,
    module: "src/pages/ffi/ffi-api.ts",
    mirrors: [{ pointer: `#/components/schemas/${schema}`, relation: "equal" }],
  })),
  // Medical Centre, carried as its code: the set an FFI's Submitting MC and a
  // Medication Record's Dispensary share.
  {
    set: "MEDICAL_CENTRES",
    module: "src/pages/medical-centre-api.ts",
    mirrors: [
      { pointer: "#/components/schemas/MedicalCentre", relation: "equal" },
    ],
  },
  // A NUMC Record's two closed sets, each carried as its code.
  ...[
    ["NUMC_TYPES", "NumcType"],
    ["NUMC_OUTCOMES", "NumcOutcome"],
  ].map(([set, schema]) => ({
    set,
    module: "src/pages/numc/numc-api.ts",
    mirrors: [{ pointer: `#/components/schemas/${schema}`, relation: "equal" }],
  })),
  // An ARMC Board's two closed sets, each carried as its code.
  ...[
    ["ARMC_BOARD_TYPES", "ArmcBoardType"],
    ["ARMC_OUTCOMES", "ArmcOutcome"],
  ].map(([set, schema]) => ({
    set,
    module: "src/pages/armc/armc-api.ts",
    mirrors: [{ pointer: `#/components/schemas/${schema}`, relation: "equal" }],
  })),
  // A Medication Record's Dispense Status, carried as its code.
  {
    set: "DISPENSE_STATUSES",
    module: "src/pages/medication/medication-api.ts",
    mirrors: [
      { pointer: "#/components/schemas/DispenseStatus", relation: "equal" },
    ],
  },
];

/** The node a `#/a/b` JSON pointer names in `doc`, or `undefined`. */
function resolve(doc, pointer) {
  return pointer
    .slice(2)
    .split("/")
    .map((token) => token.replaceAll("~1", "/").replaceAll("~0", "~"))
    .reduce((node, token) => (node == null ? undefined : node[token]), doc);
}

/** The string values of the enum at `pointer`, following one `$ref`; `null` when there is none. */
function enumAt(doc, pointer) {
  let node = resolve(doc, pointer);
  if (node && typeof node.$ref === "string") node = resolve(doc, node.$ref);
  if (!node || !Array.isArray(node.enum)) return null;
  // A nullable enum lists `null` beside its values; the SPA decodes it apart.
  return node.enum.filter((value) => value !== null);
}

function vocabularyMismatches(spa, doc) {
  const findings = [];
  for (const { set, module, mirrors } of VOCABULARIES) {
    const values = spa.modules[module]?.[set];
    const label = `${set} (${module})`;
    if (
      !Array.isArray(values) ||
      !values.every((value) => typeof value === "string")
    ) {
      findings.push(`${module} exports no ${set} array of strings`);
      continue;
    }
    for (const { pointer, relation } of mirrors) {
      const documented = enumAt(doc, pointer);
      if (documented === null) {
        findings.push(
          `openapi.yaml has no enum at ${pointer}, which ${label} mirrors`,
        );
        continue;
      }
      for (const value of documented.filter(
        (value) => !values.includes(value),
      )) {
        findings.push(`${label} lacks "${value}", which ${pointer} has`);
      }
      if (relation !== "equal") continue;
      for (const value of values.filter(
        (value) => !documented.includes(value),
      )) {
        findings.push(`${label} has "${value}", which ${pointer} lacks`);
      }
    }
  }
  return findings;
}

/** What a stand-in argument reads as wherever it becomes text. */
const STAND_IN = "ARG";

/**
 * A stand-in for any argument a declaration's path is built from: it reads as
 * `ARG` wherever it becomes text (and as 1 wherever it becomes a number), and
 * any property of it is another stand-in, so `(id) => \`/x/${id}\`` and
 * `(query) => \`/x?${query.search}\`` both resolve without the check knowing
 * an argument's type.
 *
 * It is set wherever a path asks: it is no `null` and no `""`, calling it
 * answers another stand-in, which is truthy, and iterating it yields one
 * stand-in — so `sort === null`, `search === ""`, `chosen.includes(code)` and
 * `for (const code of chosen)` all take the branch that sends a parameter, and
 * every optional query parameter a path can send is sent.
 */
function placeholder() {
  return new Proxy(function stand() {}, {
    apply: () => placeholder(),
    get: (_target, key) => {
      if (key === Symbol.toPrimitive) {
        return (hint) => (hint === "number" ? 1 : STAND_IN);
      }
      if (key === "toString" || key === "valueOf") return () => STAND_IN;
      if (key === Symbol.iterator) {
        return function* one() {
          yield placeholder();
        };
      }
      return placeholder();
    },
  });
}

const ARGUMENTS = Array.from({ length: 8 }, placeholder);

/**
 * What a declaration's `permission` requires, as the list of names a session
 * must hold every one of: one Permission (or `superuser`), or the names of an
 * `{ allOf }` — `null` for anything else, an `allOf` naming nothing included.
 */
function requiredBy(permission) {
  if (typeof permission === "string") return [permission];
  const allOf = permission?.allOf;
  return Array.isArray(allOf) &&
    allOf.length > 0 &&
    allOf.every((name) => typeof name === "string")
    ? allOf
    : null;
}

const isDeclaration = (value) =>
  value !== null &&
  typeof value === "object" &&
  typeof value.path === "function" &&
  requiredBy(value.permission) !== null;

/** Every exported request declaration in `spa`, with where it is and how it is sent. */
function declarationsOf(spa) {
  const found = [];
  for (const [module, exports] of Object.entries(spa.modules)) {
    for (const [name, value] of Object.entries(exports)) {
      if (!isDeclaration(value)) continue;
      found.push({
        label: `${name} (${module})`,
        method: value.method ?? "GET",
        required: requiredBy(value.permission),
        value,
      });
    }
  }
  return found;
}

/** The path template in `doc` the concrete `path` addresses with `method`, or `null`. */
function operationFor(doc, method, path) {
  const bare = path.split("?")[0];
  const matches = Object.entries(doc.paths ?? {})
    .filter(([, item]) => item?.[method.toLowerCase()] !== undefined)
    .filter(([template]) => {
      const pattern = template
        .split(/\{[^}]+\}/)
        .map((part) => part.replace(/[.*+?^$()|[\]\\]/g, "\\$&"))
        .join("[^/]+");
      return new RegExp(`^${pattern}$`).test(bare);
    })
    .map(([template]) => template);
  // A literal segment is more specific than a parameter that would also match it.
  matches.sort((a, b) => a.split("{").length - b.split("{").length);
  return matches[0] ?? null;
}

/** An operation's `security`, as one line: `sessionCookie: [user:read]`, or several joined by `or`. */
function describeSecurity(security) {
  if (security.length === 0) return "nothing (security: [])";
  return security
    .map((alternative) =>
      Object.entries(alternative)
        .map(([scheme, scopes]) => `${scheme}: [${scopes.join(", ")}]`)
        .join(" and "),
    )
    .join(" or ");
}

/**
 * The Permissions, or `superuser`, a session must hold every one of for
 * `security` — the scopes of its one `sessionCookie` entry; `null` for
 * anything else: alternatives, another scheme, or an entry naming none.
 */
function sessionRequirement(security) {
  if (security.length !== 1) return null;
  const [alternative] = security;
  const schemes = Object.keys(alternative);
  if (schemes.length !== 1 || schemes[0] !== "sessionCookie") return null;
  const scopes = alternative.sessionCookie;
  return scopes.length > 0 ? scopes : null;
}

/** Whether two lists of names hold the same names, as many times each, in any order. */
function sameNames(a, b) {
  if (a === null || b === null || a.length !== b.length) return false;
  const sorted = (names) => [...names].sort();
  return sorted(a).every((name, index) => name === sorted(b)[index]);
}

/** A path the declaration `value` builds from stand-in arguments, or `null` when it builds none. */
function pathOf(value) {
  try {
    const path = value.path(...ARGUMENTS);
    return typeof path === "string" && path.startsWith("/") ? path : null;
  } catch {
    return null;
  }
}

/** Each name a module's source declares that its exports do not hold as a declaration. */
function unevaluatedDeclarations(spa) {
  return Object.entries(spa.declared).flatMap(([module, names]) =>
    names
      .filter((name) => !isDeclaration(spa.modules[module]?.[name]))
      .map(
        (name) =>
          `${module} declares ${name}, which did not evaluate to a request declaration`,
      ),
  );
}

/** `node`, or the node its `$ref` names when it is a reference. */
const dereferenced = (doc, node) =>
  typeof node?.$ref === "string" ? resolve(doc, node.$ref) : node;

// ---- query parameters ----------------------------------------------------------------

/** The name of each query parameter `path` sends, each once, in the order it first sends it. */
function queryNamesOf(path) {
  const query = path.includes("?") ? path.slice(path.indexOf("?") + 1) : "";
  const names = query
    .split("&")
    .filter((part) => part !== "")
    .map((part) => part.split("=")[0]);
  return [...new Set(names)];
}

/** Every `in: query` parameter `doc` documents for the operation, the path item's own included. */
function documentedQueryParameters(doc, { template, node }) {
  return [...(doc.paths[template].parameters ?? []), ...(node.parameters ?? [])]
    .map((parameter) => dereferenced(doc, parameter))
    .filter((parameter) => parameter?.in === "query");
}

/**
 * Each query parameter the declaration's path sends that its operation does
 * not document, each one the operation requires that the path never sends,
 * and a name the path builds from its arguments, which the check cannot hold.
 */
function queryMismatches(doc, { label }, path, served, held) {
  const { operation } = served;
  const documented = documentedQueryParameters(doc, served);
  const documentedNames = documented.map(({ name }) => name);
  const sent = queryNamesOf(path);
  const findings = [];
  for (const name of sent) {
    if (name.includes(STAND_IN)) {
      findings.push(
        `${label} builds a query parameter's name from its arguments, so the check cannot hold what it sends to ${operation}`,
      );
    } else if (!documentedNames.includes(name)) {
      findings.push(
        `${label} sends query parameter "${name}" to ${operation}, which openapi.yaml does not document`,
      );
    } else {
      held.parameters += 1;
    }
  }
  for (const { name } of documented.filter(
    ({ name, required }) => required === true && !sent.includes(name),
  )) {
    findings.push(
      `${label} never sends query parameter "${name}", which ${operation} requires`,
    );
  }
  if (sent.length > 0 && findings.length === 0) held.querying += 1;
  return findings;
}

// ---- response shapes -----------------------------------------------------------------

/**
 * The keywords the sampler builds a sample from. A schema holding any other —
 * an annotation aside — cannot be sampled faithfully, so it fails the check,
 * naming the keyword, rather than being sampled as if it were not there.
 */
const SAMPLED_KEYWORDS = new Set([
  "$ref",
  "type",
  "enum",
  "format",
  "pattern",
  "minLength",
  "maxLength",
  "minimum",
  "maximum",
  "properties",
  "required",
  "additionalProperties",
  "items",
  "minItems",
  "maxItems",
]);

/** Keywords that say nothing about which values a schema allows. */
const ANNOTATIONS = new Set([
  "description",
  "title",
  "example",
  "examples",
  "readOnly",
  "writeOnly",
  "deprecated",
]);

/** The sample of each string `format` the sampler implements: values the SPA's decoders accept. */
const STRING_FORMATS = {
  date: "2024-02-29",
  "date-time": "2024-02-29T08:30:00+08:00",
  uuid: "3f2c1d4e-5b6a-4c7d-8e9f-0a1b2c3d4e5f",
};

const INTEGER_FORMATS = new Set(["int32", "int64"]);

/** The two samples built of every success body: what each holds. */
const SAMPLES = [
  { name: "every-property", full: true },
  { name: "required-only", full: false },
];

/** A schema the sampler cannot build a sample of, and why. */
class Unsampleable extends Error {}

/**
 * A value `schema` allows. The `full` sample holds every property, a nullable
 * one set and every boolean `true`; the other only the required properties,
 * a nullable one `null` and every boolean `false`. Both take an enum's first
 * member, an array of one item (or `minItems`), and the format's sample, the
 * pattern checked, of a string.
 */
function sampleOf(doc, schema, full, at) {
  if (schema === null || typeof schema !== "object") {
    unsampleable("there is no schema", at);
  }
  for (const keyword of Object.keys(schema)) {
    if (!SAMPLED_KEYWORDS.has(keyword) && !ANNOTATIONS.has(keyword)) {
      unimplemented(`"${keyword}"`, at);
    }
  }
  if (typeof schema.$ref === "string") {
    return sampleOf(doc, resolve(doc, schema.$ref), full, schema.$ref);
  }
  const types = [schema.type ?? []].flat();
  const nullable = types.includes("null") || schema.enum?.includes(null);
  if (nullable && !full) return null;
  if (Array.isArray(schema.enum)) {
    return schema.enum.find((value) => value !== null) ?? null;
  }
  const kinds = types.filter((type) => type !== "null");
  if (kinds.length === 0) {
    return nullable ? null : unimplemented("a schema with no type", at);
  }
  if (kinds.length > 1) return unimplemented("a type of several kinds", at);
  switch (kinds[0]) {
    case "object":
      return objectSample(doc, schema, full, at);
    case "array":
      return arraySample(doc, schema, full, at);
    case "string":
      return stringSample(schema, at);
    case "integer":
      return integerSample(schema, at);
    case "boolean":
      return full;
    default:
      return unimplemented(`type "${kinds[0]}"`, at);
  }
}

/** Refuses to sample the schema at `at`, saying why. */
function unsampleable(reason, at) {
  throw new Unsampleable(`${reason} (at ${at})`);
}

/** Refuses to sample the schema at `at`, naming `what` it uses that the sampler does not implement. */
function unimplemented(what, at) {
  unsampleable(`the sampler does not implement ${what}`, at);
}

function objectSample(doc, schema, full, at) {
  const properties = schema.properties ?? {};
  const required = schema.required ?? [];
  const missing = required.find((name) => !(name in properties));
  if (missing !== undefined) {
    unsampleable(`required "${missing}" has no schema`, at);
  }
  const names = full ? Object.keys(properties) : required;
  return Object.fromEntries(
    names.map((name) => [
      name,
      sampleOf(doc, properties[name], full, `${at}/properties/${name}`),
    ]),
  );
}

function arraySample(doc, schema, full, at) {
  const length = Math.min(
    Math.max(1, schema.minItems ?? 0),
    schema.maxItems ?? Infinity,
  );
  return Array.from({ length }, () =>
    sampleOf(doc, schema.items, full, `${at}/items`),
  );
}

function stringSample(schema, at) {
  if (schema.format !== undefined && !(schema.format in STRING_FORMATS)) {
    unimplemented(`format "${schema.format}"`, at);
  }
  let value = STRING_FORMATS[schema.format] ?? "sample";
  value = value.padEnd(schema.minLength ?? 0, "x");
  if (schema.maxLength !== undefined) value = value.slice(0, schema.maxLength);
  if (schema.pattern !== undefined) {
    // The pattern is read from the repo's own openapi.yaml, never from a caller.
    // nosemgrep: javascript.lang.security.audit.detect-non-literal-regexp.detect-non-literal-regexp
    const pattern = new RegExp(schema.pattern, "u");
    if (!pattern.test(value)) {
      unsampleable(
        `the sampler's "${value}" does not match the pattern "${schema.pattern}"`,
        at,
      );
    }
  }
  return value;
}

function integerSample(schema, at) {
  if (schema.format !== undefined && !INTEGER_FORMATS.has(schema.format)) {
    unimplemented(`format "${schema.format}"`, at);
  }
  return Math.min(schema.minimum ?? 1, schema.maximum ?? Infinity);
}

/** Each body the operation's `2xx` responses document, `$ref` followed: its status, media type and schema. */
function successBodies(doc, operation) {
  return Object.entries(operation.responses ?? {})
    .filter(([status]) => /^2/.test(status))
    .flatMap(([status, response]) =>
      Object.entries(dereferenced(doc, response)?.content ?? {}).map(
        ([mediaType, { schema }]) => ({ status, mediaType, schema }),
      ),
    );
}

/**
 * The declaration's `decode` run over both samples of each body its
 * operation's success responses document, under that body's own media type:
 * the first sample it refuses, or why there is nothing to run it over.
 */
async function responseMismatches(doc, { label, value }, served, held) {
  const { method, operation } = served;
  if (value.decode === undefined) {
    return method === "GET"
      ? [
          `${label} reads ${operation} with no decoder, so the check cannot hold its response`,
        ]
      : [];
  }
  if (typeof value.decode !== "function") {
    return [`${label} has a decode that is not a function`];
  }
  const bodies = successBodies(doc, served.node);
  if (bodies.length === 0) {
    return [
      `${label} decodes a body, but ${operation} documents no success body`,
    ];
  }
  let decoded = 0;
  for (const { status, mediaType, schema } of bodies) {
    const response = `${operation} ${status} ${mediaType}`;
    for (const { name, full } of SAMPLES) {
      let sample;
      try {
        sample = sampleOf(doc, schema, full, response);
      } catch (error) {
        if (!(error instanceof Unsampleable)) throw error;
        return [`${label} cannot be held to ${response}: ${error.message}`];
      }
      try {
        await value.decode(
          new Response(JSON.stringify(sample), {
            headers: { "content-type": mediaType },
          }),
        );
      } catch (error) {
        const reason = error instanceof Error ? error.message : String(error);
        return [
          `${label} refuses the ${name} sample of ${response}: ${reason}`,
        ];
      }
      decoded += 1;
    }
  }
  held.samples += decoded;
  held.decoders += 1;
  return [];
}

// ---- the declarations ----------------------------------------------------------------

async function declarationMismatches(spa, doc, held) {
  const declarations = declarationsOf(spa);
  if (declarations.length === 0) {
    return [
      "found no request declaration in the SPA; the check would hold nothing",
    ];
  }
  const findings = unevaluatedDeclarations(spa);
  for (const declaration of declarations) {
    const { label, method, value, required } = declaration;
    const path = pathOf(value);
    if (path === null) {
      findings.push(
        `${label} builds no absolute path from placeholder arguments`,
      );
      continue;
    }
    const template = operationFor(doc, method, path);
    if (template === null) {
      findings.push(
        `${label} sends ${method} ${path.split("?")[0]}, which no operation in openapi.yaml serves`,
      );
      continue;
    }
    // The operation the declaration is sent to: its path template, method, the
    // `METHOD /template` its findings name it by, and its node in the document.
    const served = {
      template,
      method,
      operation: `${method} ${template}`,
      node: doc.paths[template][method.toLowerCase()],
    };
    const security = served.node.security ?? doc.security ?? [];
    if (!sameNames(required, sessionRequirement(security))) {
      const requires = required.map((name) => `"${name}"`).join(" and ");
      findings.push(
        `${label} requires ${requires} for ${served.operation}, but openapi.yaml requires ${describeSecurity(security)}`,
      );
    }
    findings.push(
      ...queryMismatches(doc, declaration, path, served, held),
      ...(await responseMismatches(doc, declaration, served, held)),
    );
  }
  return findings;
}

/**
 * The names a module's source exports as a request declaration — every
 * `export const NAME: ReadDeclaration<…>`, `WriteDeclaration<…>` or
 * `PagedDeclaration<…>`, its generic arguments on one line or several — read
 * off the text. Deliberately narrow: it only says which exports the evaluated
 * module must hold as declarations, so one that fails to evaluate as one is
 * reported instead of passing unnoticed.
 */
export function declaredNames(source) {
  return [
    ...source.matchAll(
      /^export const (\w+)\s*:\s*(?:Read|Write|Paged)Declaration\b/gm,
    ),
  ].map(([, name]) => name);
}

/**
 * Which of the SPA's production `sources` — `[file, text]` pairs, the file
 * relative to `frontend/` — the check evaluates: `declared` maps each file
 * whose text exports a request declaration to those names (see
 * {@link declaredNames}), and `toLoad` lists every module to evaluate — each
 * vocabulary's module, then every file `declared` names — each once.
 */
export function discover(sources) {
  const declared = Object.fromEntries(
    sources
      .map(([file, source]) => [file, declaredNames(source)])
      .filter(([, names]) => names.length > 0),
  );
  const toLoad = [
    ...new Set([
      ...VOCABULARIES.map(({ module }) => module),
      ...Object.keys(declared),
    ]),
  ];
  return { declared, toLoad };
}

/**
 * The SPA, as `spa` holds it, held to `doc`, the parsed API document:
 * `findings` lists every way they disagree, empty when they agree, and `held`
 * counts what agreed — the `decoders` that read every sample of their
 * response, the `samples` they read, the declarations `querying` with every
 * query parameter they send documented, and those `parameters`.
 *
 * `spa.modules` maps a source path under `frontend/` to that module's exports;
 * `spa.declared` maps a source path to the names of the request declarations
 * its source exports, read off the text, so a declaration that does not
 * evaluate to one is reported rather than skipped.
 */
export async function holdContract(spa, doc) {
  const held = { decoders: 0, samples: 0, querying: 0, parameters: 0 };
  const findings = [
    ...vocabularyMismatches(spa, doc),
    ...(await declarationMismatches(spa, doc, held)),
  ];
  return { findings, held };
}
