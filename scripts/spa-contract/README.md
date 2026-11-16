---
type: Contract
title: SPA–API contract check
description: What make verify-contract holds the SPA to against backend/docs/openapi.yaml — the closed sets it mirrors, every request declaration's Permission or Superuser requirement, the query parameters each sends and the shape of each response it decodes — how it finds the declarations and samples the responses, and when it refuses to pass.
tags: [contract, openapi, spa, verification]
---

# SPA–API contract check

`make verify-contract`, from the repo root, runs `../verify-contract.sh`, which
runs this check. `make verify` ends with it. It belongs to neither app because it
reads both, which neither app may do (root `AGENTS.md`, "Paths").

The SPA mirrors part of the API by hand: its closed sets, what each request
requires, the query parameters each request sends and the shape of each response
it decodes. This check holds that copy to `backend/docs/openapi.yaml`. The
backend's contract tests hold the document to the running service, so the SPA is
held to the backend by transitivity.

## The vocabulary chain

Each closed set the API exposes is defined once, as a Java enum, and passes
through the document to the SPA. Nothing is generated: each link is a copy, held
set-equal to the one before it. The document's enums are named under
`#/components/schemas/`.

| Closed set             | Java (backend)                               | `openapi.yaml` enum                                                                     | SPA                                         |
| ---------------------- | -------------------------------------------- | --------------------------------------------------------------------------------------- | ------------------------------------------- |
| Session Permissions    | every `Permission` that is not `tokenOnly()` | `UserResponse/properties/permissions/items`                                             | `PERMISSIONS` (`src/auth/api.ts`)           |
| Delegatable Permission | `Permission.delegatable()`                   | `DelegatablePermission`                                                                 | covered by `PERMISSIONS`                    |
| Token Permissions      | `Permission.tokenGrantable()`                | `ConnectorTokenPermissions/items`, `ConnectorTokenSummary/properties/permissions/items` | `TOKEN_PERMISSIONS` (`accounts-api.ts`)     |
| Audit operations       | `AuditOperation`, by `name()`                | `AuditOperation`                                                                        | `AUDIT_OPERATIONS` (`audit-api.ts`)         |
| Lock causes            | `LockCause`, by `name()`                     | `AccountSummary/properties/lockCause` (its `null` is no member)                         | `LOCK_CAUSES` (`accounts-api.ts`)           |
| FFI Types              | `FfiType`, by `code()`                       | `FfiType`                                                                               | `FFI_TYPES` (`ffi-api.ts`)                  |
| FFI Outcomes           | `FfiOutcome`, by `code()`                    | `FfiOutcome`                                                                            | `FFI_OUTCOMES` (`ffi-api.ts`)               |
| Higher Authorities     | `FfiHigherAuthority`, by `code()`            | `FfiHigherAuthority`                                                                    | `FFI_HIGHER_AUTHORITIES` (`ffi-api.ts`)     |
| FFI Statuses           | `FfiStatus`, by `code()`                     | `FfiStatus`                                                                             | `FFI_STATUSES` (`ffi-api.ts`)               |
| NUMC Types             | `NumcType`, by `code()`                      | `NumcType`                                                                              | `NUMC_TYPES` (`numc-api.ts`)                |
| NUMC Outcomes          | `NumcOutcome`, by `code()`                   | `NumcOutcome`                                                                           | `NUMC_OUTCOMES` (`numc-api.ts`)             |
| ARMC Board Types       | `ArmcBoardType`, by `code()`                 | `ArmcBoardType`                                                                         | `ARMC_BOARD_TYPES` (`armc-api.ts`)          |
| ARMC Outcomes          | `ArmcOutcome`, by `code()`                   | `ArmcOutcome`                                                                           | `ARMC_OUTCOMES` (`armc-api.ts`)             |
| Dispense Statuses      | `DispenseStatus`, by `code()`                | `DispenseStatus`                                                                        | `DISPENSE_STATUSES` (`medication-api.ts`)   |
| Medical Centres        | `MedicalCentre`, by `code()`                 | `MedicalCentre`                                                                         | `MEDICAL_CENTRES` (`medical-centre-api.ts`) |

- **Java ↔ document**: the backend's `VocabularyContractTests`
  (`backend/docs/api-contract-check.md`) compares each Java set with each enum
  beside it in both directions, so a member either side gains or loses fails the
  backend's baseline gate — even one no response yet carries. Every Permission
  lands in a compared set: a session's, a token's, or both.
- **Document ↔ SPA**: this check. `VOCABULARIES` in `check.mjs` is the one list
  of which SPA set is held to which enum, and whether in both directions
  (`equal`) or only to hold every documented value (`covers`, for a set the SPA
  also decodes another response with: `PERMISSIONS` against the delegatable
  enum). A new closed set joins both lists.
- **A set shown as received** (ADR 0020) stays `equal`. An FFI's or a NUMC
  Record's code may be any string — the document's row field is `anyOf` the set's enum, any
  string and `null` — but the set is what a filter's codes are checked
  against and what the Filter panel offers, so the SPA's copy and the enum hold
  the same known codes, and the check compares the enum, not the row field.

## The request declarations

Every exported request declaration under `frontend/src/` is held to the
operation it calls: the Permission it names, every Permission for a declaration
requiring several, or `superuser`, against that operation's `security`.

They are found from the source text. `main.mjs` reads the production modules
(`productionSources`: every `.ts`/`.tsx` file under `frontend/src/` that is no
`*.test`, `*.testHelpers` or `*.d` file), and `discover` in `check.mjs` reads
those: every module with an
`export const NAME: ReadDeclaration<…>`, `WriteDeclaration<…>` or
`PagedDeclaration<…>` — however its generic arguments wrap — is evaluated
beside the vocabulary modules, whatever else it exports. A name found that way
whose export does not evaluate to a request declaration (a `path` function and a
Permission, `superuser` or `{ allOf }` requirement) is reported, never skipped.

A value either side gains or loses fails the check, naming the mismatch.

A declaration's path is built from **stand-in arguments** (`placeholder` in
`check.mjs`), so the check needs no argument of the right type: a stand-in reads
as `ARG` wherever it becomes text, any property of it is another stand-in, and it
is set wherever a path asks — never `null` or `""`, holding every code
`includes` asks about, iterating as one value. A path therefore takes every
branch that sends a parameter, and the check finds its operation from what is
left before the `?`.

## Query parameters

Every query parameter a declaration's path sends with stand-in arguments — each
history's filters, date ranges, `sortBy` and `sortOrder`, the Patient search's
`q` and `gender`, the projections' `search`, `startIndex` and `count` — must be
an `in: query` parameter its operation documents, on the operation or its path
item, `$ref` followed. One the document does not name, renamed on either side, is
a finding; so is one the operation `required`s that the path never sends.

A parameter's **name** must be text the path writes itself: one built from an
argument (`?${query.search}`, a name read off the arguments) reads as `ARG`, and
is reported as a declaration whose parameters the check cannot hold — never
skipped. A parameter a path sends only when an argument is empty, `null` or one
particular value is not seen.

## Response shapes

For every declaration with a `decode`, the check takes each body its
operation's `2xx` responses document, `$ref` followed — each media type its
own, `application/fhir+json` included — builds two sample bodies from the
body's schema and runs the declaration's own `decode` over each, as a
`Response` served under that media type:

| Sample           | Properties                | Nullable  | Boolean |
| ---------------- | ------------------------- | --------- | ------- |
| `every-property` | every documented property | its value | `true`  |
| `required-only`  | only the `required` ones  | `null`    | `false` |

Both take an enum's first member; one item of an array, or `minItems`; an
integer of `minimum` (else 1), within `maximum`; and for a string the sample of
its `format` — `date`, `date-time` with an offset, `uuid` — padded to
`minLength` and cut to `maxLength`. An `anyOf` is sampled by one branch: its
first in `every-property`, its last in `required-only`. Nested objects and
arrays are sampled the same way.

So a row shown as received (ADR 0020) — each patient-source field nullable, a
closed-set field `anyOf` its enum, any string and `null`, and `invalid` an array
of field names — is sampled with every field set, each code a known one and
`invalid` naming the row's first field, and with every field `null`: the
decoder must read both without refusing the row. A sample the decoder refuses is a finding, naming the sample, the
operation and the decoder's own error, which names the field; the first refusal
per declaration is reported.

So a field the document renames or removes, one it adds against a decoder that
refuses unknown fields (`onlyFields`), and a required field it makes optional or
nullable each fail the check. A field added against a decoder that ignores
unknown fields does not.

The check refuses to sample rather than sample wrongly:

- A **schema keyword** the sampler does not implement (`SAMPLED_KEYWORDS`;
  annotations such as `description` aside), a `format` it has no sample of, a
  `pattern` its string does not match, or a `required` property with no schema
  fails the check, naming it and where, as the backend's `OpenApiContractTests`
  does for its validator.
- A declaration with a decoder whose operation documents **no success body**
  fails, and so does a `GET` declaration with **no decoder**. A write with no
  decoder answers no body the SPA reads, so nothing of its response is held.

**What it is not:** a proof that every body the schema allows decodes. It is a
sample check: two bodies per response, each value one the sampler picks. A
decoder that refuses an enum member other than the first, a second array item, a
particular string, an `anyOf` branch between its first and its last — a code
outside its closed set among them — or a combination of values the schema allows — such as one
field `null` exactly when another is `false` — is not caught here; the SPA's own
unit tests and E2E hold those. The samples set booleans `true` where every
nullable is set and `false` where every nullable is `null`, so a rule pairing
the two, as `UserRow.lockCause` is `null` exactly when `locked` is false, holds
in both.

## Running it

It needs `npm ci` in `frontend/` and the pinned Node. It runs its own tests
(`check.test.mjs`) first, then the check (`main.mjs` reads the sources, loads the
modules `discover` names with the frontend's Vite, parses the document, and
`check.mjs` compares them). Its summary line counts what agreed: the
vocabularies and declarations, the decoders that read all their samples and how
many samples, and the declarations sending documented query parameters and how
many parameters. It refuses to pass when it finds no declaration, or when an
enum or export it expects is missing, so an empty or renamed input cannot pass
as agreement.
