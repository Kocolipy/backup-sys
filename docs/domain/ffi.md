---
type: Domain Rules
title: FFIs
description: A patient's FFI History — the in-house patient source's own FFIs, read through it at GET {base}/Patient/{id}/ffi as a FHIR-shaped but non-conformant searchset, the elements read and the five closed sets carried as codes, the source's order and paging, the failures and their log reasons, the logging rules, no caching, no auditing, the FFI page with its empty states, and the mock's FFI fixtures and fault stubs.
tags: [patient, ffi]
---

# FFIs

**FFI** (Fit for Instruction) — one record of whether a patient was found fit
for an instruction, as the in-house patient source holds it. A patient's
**FFI History** is every FFI the source holds for them, in the source's own
order. The backend's `patient` module reads it from the source on every read
and serves it, read-only, at `GET /api/patient-ffi/{patientId}` to a session
holding `patient:read` ([authorization](authorization.md)), as plain JSON a
page at a time. Nothing stores it. Why it is the source's, read through, and
why the read is not audited, is ADR 0019; the wire contract is
`backend/docs/openapi.yaml`. The SPA never reaches the source:
SPA → `GET /api/patient-ffi/{patientId}` → backend →
`GET {base}/Patient/{id}/ffi`.

## The elements

Every FFI has every element; none is optional.

| Element          | On the wire (both sides)   | Shown as                                                                                                                                                                                                |
| ---------------- | -------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| FFI Type         | `type`, a code             | `overseas-exercises` Overseas Exercises · `cat-a-ocs-scs` CAT A - OCS/SCS · `ippt` IPPT FFI · `dental` Dental FFI · `ord-release-from-service` ORD / Release from Service · `vocational` Vocational FFI |
| Outcome          | `outcome`, a code          | `fit` FIT (green badge) · `request-waiver` REQUEST WAIVER (amber badge) · `unfit` UNFIT (red badge)                                                                                                     |
| Issue Date       | `issueDate`, `YYYY-MM-DD`  | `05 Feb 2001`                                                                                                                                                                                           |
| Expiry Date      | `expiryDate`, `YYYY-MM-DD` | `05 Feb 2001`                                                                                                                                                                                           |
| Higher Authority | `higherAuthority`, a code  | `camo` CAMO · `carmo` CARMO · `cnmo` CNMO · `comd-mmi` Comd MMI · `dis` DIS                                                                                                                             |
| Submitting MC    | `submittingMc`, a code     | `nee-soon-camp` Nee Soon Camp MC · `kranji-camp` Kranji Camp MC · `hendon-camp` Hendon Camp MC · `mandai-hill-camp` Mandai Hill Camp MC                                                                 |
| Status           | `status`, a code           | `active` Active (green) · `inactive` Inactive (muted)                                                                                                                                                   |

Each FFI also carries its own `id` at the source, in FHIR's id grammar, which
the page keys its row by and never shows.

### The closed sets

The FFI Type, Outcome, Higher Authority, Submitting MC and Status are closed
sets. Each value travels as its **code** — from the source, and to the SPA —
and the SPA alone names it with its **label**. A code is matched exactly: a
label, another case or another spelling is outside the set. Each set is one
Java enum (`FfiType`, `FfiOutcome`, `FfiHigherAuthority`, `FfiSubmittingMc`,
`FfiStatus`, codes through `FfiCode`), one enum in `backend/docs/openapi.yaml`
and one SPA set (`src/pages/ffi/ffi-api.ts`), held set-equal by
`VocabularyContractTests` and by `make verify-contract`
(`/scripts/spa-contract/README.md`).

### Status is the source's

**Status is the source's own field.** It is never derived from the Expiry
Date: an FFI past its Expiry Date may still be Active. Nothing in the SPA or
the backend works out "expired".

## The source contract

The contract is ours to define, and it is **FHIR-shaped paging only — not
FHIR-conformant** (ADR 0019):

- **The call.** `GET {base}/Patient/{id}/ffi?_offset=…&_count=…&_total=accurate`,
  with no credential, asking for `application/fhir+json`, on the same client,
  base URL and timeouts as a read by id ([patient](patient.md), "The in-house
  patient source"): 2 seconds to connect and 5 to answer, the whole body
  included, no redirect followed and no retry. The id is the patient's EHR's
  FHIR id, sent unchanged. No `_sort` is sent, so the source's default order is
  the order shown.
- **The answer.** A FHIR-R4-shaped `searchset` `Bundle` — `resourceType:
Bundle`, `type: searchset`, its `total` and each `entry[].resource` — whose
  every resource is a custom `"resourceType": "FFI"` object of flat fields:
  `id`, `type`, `outcome`, `issueDate`, `expiryDate`, `higherAuthority`,
  `submittingMc` and `status`. No FHIR resource type is named FFI; nothing else
  in the Bundle is read.
- **The page.** `startIndex` and `count` are read by ADR 0017's rules
  (`PageParameter`, `PageRequest`: coerced, never refused; a non-integer is
  `400`) and sent as `_offset` (`startIndex - 1`) and `_count`.
  `totalResults` is the source's `total`. The source is asked even for
  `count=0` or a page past the end, as a Patient search's is (ADR 0018, D3).
  The SPA asks for 5. The searchset's paging — the page's parameters, the
  `total`, the `entry`, and an entry's `id` rules — is read by the one piece
  a Patient search reads its page with (`SourceSearchset`).
- **Only a `404` is no patient**, and is our API's `404` too. Every other answer
  is a failure of the source, our `502` (["Failures"](#failures)).
- **No caching.** No FFI is kept in Redis or in memory: every read, of the same
  page too, is a call to the source.

## Our API

`GET /api/patient-ffi/{patientId}?startIndex=…&count=…`, outside the
`/api/patient` FHIR namespace, as Patient search is: plain JSON,
`{totalResults, startIndex, ffis: [{id, type, outcome, issueDate, expiryDate,
higherAuthority, submittingMc, status}]}`, each code as the source sent it and
both dates `YYYY-MM-DD`. Errors are the application API's `ApiError`:

| Case                                                                        | Status |
| --------------------------------------------------------------------------- | ------ |
| `patientId` outside FHIR's id grammar, or a paging value that is no integer | `400`  |
| No session                                                                  | `401`  |
| Session lacks `patient:read`                                                | `403`  |
| The source's `404` for the patient                                          | `404`  |
| The source failing                                                          | `502`  |

The `400` is refused before the source is called. The `403` is refused by the
handler's `@PreAuthorize` and the chain's backstop, audited as an
`ACCESS_DENIED` and logged as every authorization refusal is.

## Failures

Each of these is a failure of the source, our `502`, and its one fault record's
`event.reason`:

| Failure                                                     | `event.reason`                                                                                                     | `error.category` |
| ----------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------ | ---------------- |
| No connection, or one refused, reset or dropped             | `unreachable`                                                                                                      | `network`        |
| No connection within 2 seconds, or no whole answer within 5 | `timed-out`                                                                                                        | `network`        |
| A `5xx`                                                     | `server-error`                                                                                                     | `server`         |
| Any other status but a `200` or a `404`                     | `unexpected-status`                                                                                                | `data`           |
| A body that arrived whole and is not JSON or not the shape  | `unreadable`                                                                                                       | `data`           |
| A `200` that is no searchset `Bundle`                       | `not-a-searchset`                                                                                                  | `data`           |
| A searchset with no `total`, or a negative one              | `no-total`                                                                                                         | `data`           |
| An entry with no resource, or one that is no `FFI`          | `another-resource`                                                                                                 | `data`           |
| An entry with no `id`, or one outside FHIR's id grammar     | `no-id`, `invalid-id`                                                                                              | `data`           |
| An entry missing a field — every field is required          | `no-type`, `no-outcome`, `no-issue-date`, `no-expiry-date`, `no-higher-authority`, `no-submitting-mc`, `no-status` | `data`           |
| A code outside its closed set                               | `unknown-type`, `unknown-outcome`, `unknown-higher-authority`, `unknown-submitting-mc`, `unknown-status`           | `data`           |
| A date not to the day                                       | `issue-date-not-to-the-day`, `expiry-date-not-to-the-day`                                                          | `data`           |

The adapter raises each as a `PatientSourceException`, which the app-wide
handler answers `502` with the generic `server-error` `ApiError`
([patient](patient.md), "Errors", has the classifier every outbound call uses).

## Logging

- **Each failure of the source is exactly one `ERROR`**, the `502`'s
  `http.request.fault` record, whose `event.reason` names the failure or the
  rule broken (the table above) and whose error message is the fault's fixed
  sentence.
- **Neither the log nor the exception's message names the patient id or
  anything of the source's body**: not an FFI's id, code or date, nor an error
  answer's body, which is never read. The exception's cause is redacted to its
  type, as a read by id's is ([patient](patient.md), "Logging").
- **A read adds no record of its own.** A success, a `400` and a `404` each
  have the request record alone, which names the route template
  (`/api/patient-ffi/{patientId}`), never the id; a `403` adds the
  authorization refusal's `WARN` record.

`FfiHistoryIntegrationTests` holds each FFI fault stub's read to exactly one
`ERROR` naming its failure under its category, and the stream, for those reads
and for successful ones, to naming neither the patient id nor any FFI's id or
dates.

## Not audited

An FFI read writes **no audit row**, as a read of one Patient by id writes none
([patient](patient.md), "Patient reads are not audited"; ADR 0019): it reads one
patient's records by the id the EHR context gave, the ordinary unit of care. A
refused read is audited as `ACCESS_DENIED`, as every authorization refusal is.

## The FFI page

`/ffi`, linked **FFI** in the sidebar after Profile for a session holding
`patient:read`, and guarded by it as Patient Search is: a session without it is
offered no link and is routed to its landing page. Headed "FFI History".

- **It reads the patient in context**, the tab's EHR context's patient, from
  the signed-in layout's one reader of it ([patient](patient.md), "The patient
  in context"): it reads that patient's FFI History once the patient is read,
  and a failed read of the patient leaves the page empty, its copy
  "Couldn't load patient" on the page's error line. It is read-only, five FFIs
  a page, with no row action.
- **The table**: FFI Type, Outcome, Issue Date, Expiry Date, Higher Authority,
  Submitting MC, Status — each code by its label, the Outcome and the Status as
  badges ([the elements](#the-elements)), both dates `dd MMM yyyy`.
- **The shared paged listing**: the numbered pager and "Showing x to y of N
  FFIs", named on a single page too, the page shown held while the next loads.
- **No patient in context** — the tab holds none, or the source holds no
  patient under its id — shows "No patient in context" and reads no FFIs.
- **The source's `404`** for the patient is our `404`, and shows the same "No
  patient in context", reporting nothing.
- **A patient with no FFIs** — a `200` with `total: 0` — shows "No FFIs".
- **The source failing** shows "Couldn't load FFIs" on the page's error line,
  and Try again, which reads the same page again.

## The patient source's FFI fixtures

The patient source's mock (`backend/patient-source-mock`, [patient](patient.md),
"The patient source's fixtures") serves the FFI Histories of the development
fixtures, and the backend's tests read the same stub files
(`PatientSourceStubs`), so the two cannot drift.

- **Tan Wei Ming** (`eq081-VQEgP8drUUqCWzHfw3`) has exactly four FFIs, on one
  page (`ffi-tan-wei-ming`):

  | FFI Type           | Outcome        | Issue Date  | Expiry Date | Higher Authority | Submitting MC       | Status   |
  | ------------------ | -------------- | ----------- | ----------- | ---------------- | ------------------- | -------- |
  | Overseas Exercises | FIT            | 14 Oct 2023 | 13 Oct 2024 | CAMO             | Nee Soon Camp MC    | Inactive |
  | IPPT FFI           | REQUEST WAIVER | 10 Jan 2024 | 09 Jan 2025 | CNMO             | Kranji Camp MC      | Active   |
  | Dental FFI         | FIT            | 22 Nov 2023 | 21 Nov 2024 | CARMO            | Hendon Camp MC      | Active   |
  | Vocational FFI     | UNFIT          | 05 Aug 2023 | 04 Aug 2024 | CAMO             | Mandai Hill Camp MC | Inactive |

- **Tan Hui Min** (`dev-patient-05`) has seven FFIs over two pages of five
  (`ffi-tan-hui-min-page-1`, `-page-2`), between them every code of every
  closed set — every Outcome, Higher Authority and Status among them — and
  Active FFIs past their Expiry Date.
- **Every other fixture** has none — Siti Nurhaliza binte Ahmad
  (`erXuFYUfucBZaryVksYEcMg3`), found by the NRIC search `S9212345A`, is the
  one the tests read — answered by `ffi-fallback`, which also answers any page
  of a fixture's history the canned stubs do not name, with an empty searchset,
  `total: 0`.
- **Any other well-formed id** is the source's `404` (`ffi-not-found`).

Each canned page is matched on the paging the SPA sends — `_count=5`,
`_offset`, `_total=accurate` and no `_sort`. Each fixture is selectable on
Patient Search (the name search `tan` finds both Tans).

**Fault stubs.** Beside them, each failure above has a stub of its own under a
well-formed patient id no fixture uses, `fault-ffi-<failure>`, marked with
`metadata.fault`: `unreachable` (the connection reset), `slow` (the answer after
7 seconds), `server-error` (`503`), `unexpected-status` (`400`),
`not-a-searchset`, `no-total`, `no-id`, `invalid-id`, `no-outcome` (an FFI
missing a field), `unknown-higher-authority` (a code outside its set) and
`expiry-date-not-to-the-day`. A fault stub is no fixture. `PatientSourceFixturesTests`
holds the fixtures to these rules, `PatientStoreContract` holds what each
history reads, written out, and `PatientSourceFfiAdapterTests` each fault
stub's failure.
