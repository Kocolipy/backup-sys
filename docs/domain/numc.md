---
type: Domain Rules
title: NUMC
description: A patient's NUMC History — the in-house patient source's own NUMC Records, read through it at GET {base}/Patient/{id}/numc on the FFI History's terms, the four elements and the two closed sets carried as codes, the source's order and paging, the failures and their NUMC log reasons, the logging rules, no caching, no auditing, the NUMC page and its states, and the mock's NUMC fixtures and fault stubs.
tags: [patient, numc]
---

# NUMC

**NUMC Record** — one record of whether a patient was found fit for one
underwater duty, as the in-house patient source holds it for the Navy
Underwater Medicine Centre (NUMC). A patient's **NUMC History** is every NUMC
Record the source holds for them, in the source's own order. The backend's
`patient` module reads it from the source on every read and serves it,
read-only, at `GET /api/patient-numc/{patientId}` to a session holding
`patient:read` ([authorization](authorization.md)), as plain JSON a page at a
time. Nothing stores it.

It is read on the same terms as an FFI History (ADR 0019, D5): what the
[FFI rules](ffi.md) say of the source's paged read, its client and timeouts, its
failures, its logging, caching and auditing holds here too, and this file says
only where a NUMC History differs — the endpoint, the four elements and the two
closed sets — and what is its own. The wire contract is
`backend/docs/openapi.yaml`. The SPA never reaches the source:
SPA → `GET /api/patient-numc/{patientId}` → backend →
`GET {base}/Patient/{id}/numc`.

## The elements

Every NUMC Record has every element; none is optional.

| Element      | On the wire (both sides)   | Shown as                                                                                                                                             |
| ------------ | -------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| NUMC Type    | `type`, a code             | `hyperbaric-chamber` Hyperbaric Chamber Duties · `submariner` Submariner Duties · `diving` Diving Duties                                             |
| NUMC Outcome | `outcome`, a code          | `fit` FIT (green badge) · `unfit` UNFIT (red badge) · `waiver-granted` WAIVER GRANTED (blue badge) · `waiver-rejected` WAIVER REJECTED (muted badge) |
| Issue Date   | `issueDate`, `YYYY-MM-DD`  | `14 Oct 2023`                                                                                                                                        |
| Expiry Date  | `expiryDate`, `YYYY-MM-DD` | `14 Oct 2023`                                                                                                                                        |

Each NUMC Record also carries its own `id` at the source, in FHIR's id grammar,
which the page keys its row by and never shows.

**A NUMC Record has no Status.** Nothing in the SPA or the backend works out
"expired" from its Expiry Date: a record past it is shown as any other.

### The closed sets

The NUMC Type and the NUMC Outcome are closed sets, carried and matched as an
FFI's are ([ffi](ffi.md), "The closed sets"): each value travels as its code,
from the source and to the SPA, matched exactly, and the SPA alone names it with
its label. A code the service does not know is a failure of the source, never a
row with a blank cell. Each set is one Java enum (`NumcType`, `NumcOutcome`,
codes through `SourceCode`), one enum in `backend/docs/openapi.yaml` and one SPA
set (`src/pages/numc/numc-api.ts`), held set-equal by `VocabularyContractTests`
and by `make verify-contract` (`/scripts/spa-contract/README.md`).

**The NUMC Outcome is a set of its own**, apart from an FFI's Outcome: `fit` and
`unfit` are codes of both, but an FFI's `request-waiver` is no NUMC Outcome, and
a NUMC Record carrying it is a failure of the source.

## The source contract

As an FFI History's ([ffi](ffi.md), "The source contract"), FHIR-shaped paging
only — not FHIR-conformant:

- **The call.** `GET {base}/Patient/{id}/numc?_offset=…&_count=…&_total=accurate`,
  on the same client, base URL, timeouts and absent credential, the id sent
  unchanged. Nothing else is sent: no `_sort` and no filter, so the source's
  own order is the order shown.
- **The answer.** A FHIR-R4-shaped `searchset` `Bundle` — its `total` and each
  `entry[].resource` — whose every resource is a custom `"resourceType": "NUMC"`
  object of flat fields: `id`, `type`, `outcome`, `issueDate` and
  `expiryDate`. Nothing else in the Bundle is read, and any other field of a
  record is never bound.
- **The page.** Read by ADR 0017's rules and sent as `_offset`
  (`startIndex - 1`) and `_count`; `totalResults` is the source's `total`, and
  the source is asked even for `count=0` or a page past the end (ADR 0018, D3).
  The SPA asks for 5. `SourceSearchset` reads it, as it reads an FFI History's.
- **Only a `404` is no patient**, our API's `404` too. Every other answer is a
  failure of the source, our `502` ([Failures](#failures)).
- **No caching.** No NUMC Record is kept in Redis or in memory: every read, of
  the same page too, is a call to the source.

## Our API

`GET /api/patient-numc/{patientId}?startIndex=…&count=…`, outside the
`/api/patient` FHIR namespace: plain JSON, `{totalResults, startIndex,
records: [{id, type, outcome, issueDate, expiryDate}]}`, each code as the source
sent it and both dates `YYYY-MM-DD`. It takes no sort and no filter. Errors are
the application API's `ApiError`:

| Case                                                                        | Status |
| --------------------------------------------------------------------------- | ------ |
| `patientId` outside FHIR's id grammar, or a paging value that is no integer | `400`  |
| No session                                                                  | `401`  |
| Session lacks `patient:read`                                                | `403`  |
| The source's `404` for the patient                                          | `404`  |
| The source failing                                                          | `502`  |

The `400` is refused before the source is called. The `403` is refused by the
handler's `@PreAuthorize`, the chain's backstop and the document's `security`,
audited as an `ACCESS_DENIED` and logged as every authorization refusal is.

## Failures

Each of these is a failure of the source, our `502`, and its one fault record's
`event.reason`. A failure of the call or of the searchset is the one every paged
read of the source shares; **every rule a NUMC Record breaks has a reason naming
NUMC**, so a log line says which history broke.

| Failure                                                     | `event.reason`                                                                 | `error.category` |
| ----------------------------------------------------------- | ------------------------------------------------------------------------------ | ---------------- |
| No connection, or one refused, reset or dropped             | `unreachable`                                                                  | `network`        |
| No connection within 2 seconds, or no whole answer within 5 | `timed-out`                                                                    | `network`        |
| A `5xx`                                                     | `server-error`                                                                 | `server`         |
| Any other status but a `200` or a `404`                     | `unexpected-status`                                                            | `data`           |
| A body that arrived whole and is not JSON or not the shape  | `unreadable`                                                                   | `data`           |
| A `200` that is no searchset `Bundle`                       | `not-a-searchset`                                                              | `data`           |
| A searchset with no `total`, or a negative one              | `no-total`                                                                     | `data`           |
| An entry with no resource, or one that is no `NUMC`         | `another-resource`                                                             | `data`           |
| An entry with no `id`, or one outside FHIR's id grammar     | `numc-no-id`, `numc-invalid-id`                                                | `data`           |
| A record missing an element — every element is required     | `no-numc-type`, `no-numc-outcome`, `no-numc-issue-date`, `no-numc-expiry-date` | `data`           |
| A code outside its closed set                               | `unknown-numc-type`, `unknown-numc-outcome`                                    | `data`           |
| A date not to the day                                       | `numc-issue-date-not-to-the-day`, `numc-expiry-date-not-to-the-day`            | `data`           |

The adapter raises each as a `PatientSourceException`, which the app-wide
handler answers `502` with the generic `server-error` `ApiError`, as for an FFI
History.

## Logging

As an FFI History's ([ffi](ffi.md), "Logging"): each failure of the source is
exactly one `ERROR`, the `502`'s `http.request.fault` record naming its reason;
neither the log nor the exception's message names the patient id or anything of
the source's body — not a record's id, code or date, nor an error answer's body;
and a read adds no record of its own, the request record naming the route
template (`/api/patient-numc/{patientId}`), never the id.
`NumcHistoryIntegrationTests` holds each NUMC fault stub's read to exactly one
`ERROR` naming its failure under its category, and the stream, for those reads
and for successful ones, to naming neither the patient id nor any record's id or
dates.

## Not audited

A NUMC History read writes **no audit row**, as an FFI History read and a read
of one Patient by id write none (ADR 0019, D3 and D5). A refused read is audited
as `ACCESS_DENIED`, as every authorization refusal is.

## The NUMC page

`/numc`, titled **NUMC**, linked **NUMC** in the sidebar after FFI, with the
`Waves` icon, for a session holding `patient:read`, and guarded by it as FFI is:
a session without it is offered no link and is routed to its landing page.

- **It reads the patient in context** as the FFI page does, from the signed-in
  layout's one reader of it ([patient](patient.md), "The patient in context"),
  through the same shared wrapper: it reads that patient's NUMC History once the
  patient is read, says "Loading NUMC records…" while the patient or the first
  page is read, and a failed read of the patient leaves it empty. It is
  read-only, five NUMC Records a page, with no row action.
- **The table**: Type, Outcome, Issue Date, Expiry Date — the NUMC Type by its
  label, the NUMC Outcome as its badge ([the elements](#the-elements)), both
  dates `dd MMM yyyy`. No column sorts and nothing filters.
- **The shared paged listing**: the numbered pager and "Showing x to y of N
  NUMC records", named on a single page too, the page shown held while the next
  loads.
- **No patient in context** — the tab holds none, or the source holds no
  patient under its id (its `404`, our `404`) — shows "No patient in context",
  as the FFI page does, and reads no NUMC Records or reports nothing.
- **A patient with no NUMC Records** — a `200` with `total: 0` — shows "No NUMC
  records".
- **The source failing** shows "Couldn't load NUMC records" on the page's error
  line, and Try again, which reads the same page again.

## The patient source's NUMC fixtures

The patient source's mock (`backend/patient-source-mock`, [patient](patient.md),
"The patient source's fixtures") serves the NUMC Histories of the development
fixtures beside their FFI Histories, on the same patients, and the backend's
tests read the same stub files (`PatientSourceStubs`).

- **Tan Wei Ming** (`eq081-VQEgP8drUUqCWzHfw3`) has exactly four NUMC Records,
  on one page (`numc-tan-wei-ming`):

  | NUMC Type                 | NUMC Outcome    | Issue Date  | Expiry Date |
  | ------------------------- | --------------- | ----------- | ----------- |
  | Hyperbaric Chamber Duties | FIT             | 14 Oct 2023 | 13 Oct 2024 |
  | Submariner Duties         | WAIVER REJECTED | 10 Jan 2024 | 09 Jan 2025 |
  | Diving Duties             | WAIVER GRANTED  | 22 Nov 2023 | 21 Nov 2024 |
  | Hyperbaric Chamber Duties | UNFIT           | 05 Aug 2023 | 04 Aug 2024 |

- **Tan Hui Min** (`dev-patient-05`) has seven NUMC Records over two pages of
  five (`numc-tan-hui-min-page-1`, `-page-2`), between them every code of both
  closed sets.
- **Every other fixture** has none — Siti Nurhaliza binte Ahmad
  (`erXuFYUfucBZaryVksYEcMg3`) is the one the tests read — answered by
  `numc-fallback`, which also answers any page of a fixture's history the
  canned stubs do not name, with an empty searchset, `total: 0`.
- **Any other well-formed id** is the source's `404` (`numc-not-found`).

Each canned page is matched on the page the backend sends — `_count=5`,
`_offset` and `_total=accurate` — so a read of any other page falls to the
fallback. Each fixture is selectable on Patient Search (the name search `tan`
finds both Tans).

**Fault stubs.** Beside them, each failure above has a stub of its own under a
well-formed patient id no fixture uses, `fault-numc-<failure>`, marked with
`metadata.fault`: `unreachable` (the connection reset), `slow` (the answer after
7 seconds), `server-error` (`503`), `unexpected-status` (`400`),
`not-a-searchset`, `no-total`, `no-id`, `invalid-id`, `no-outcome` (a record
missing an element), `unknown-type` and `unknown-outcome` (a code outside each
set, the second an FFI's `request-waiver`) and `expiry-date-not-to-the-day`. A
fault stub is no fixture. `PatientSourceFixturesTests` holds the fixtures to
these rules, `PatientStoreContract` holds what each history reads, written out,
and `PatientSourceNumcAdapterTests` each fault stub's failure.
