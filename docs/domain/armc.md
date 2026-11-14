---
type: Domain Rules
title: ARMC
description: A patient's ARMC History — the in-house patient source's own ARMC Boards, read through it at GET {base}/Patient/{id}/armc on the FFI History's terms, the six elements with the End Date the one optional and on or after the Start Date, the two closed sets carried as codes, the source's order and paging, the sort by either date and the filters the source applies — the open-board rule among them — the failures and their ARMC log reasons, the logging rules, no caching, no auditing, the ARMC page with its sort headings, Filter panel and states, and the mock's ARMC fixtures, sort and filter stubs and fault stubs.
tags: [patient, armc]
---

# ARMC

**ARMC Board** — one aeromedical fitness board a patient sat, as the in-house
patient source holds it for the Aeromedical Centre (ARMC). A patient's **ARMC
History** is every ARMC Board the source holds for them, in the source's own
order unless sorted by its Start Date or End Date, narrowed by its Outcome and
either date. The backend's `patient` module reads it from the source on every read
and serves it, read-only, at `GET /api/patient-armc/{patientId}` to a session
holding `patient:read` ([authorization](authorization.md)), as plain JSON a
page at a time. Nothing stores it.

It is read on the same terms as an FFI History (ADR 0019, D6): what the
[FFI rules](ffi.md) say of the source's paged read, its client and timeouts, its
failures, its logging, caching and auditing holds here too, and this file says
only where an ARMC History differs — the endpoint, the six elements, the one
optional element, the two closed sets and how it is sorted and filtered — and
what is its own. The wire
contract is `backend/docs/openapi.yaml`. The SPA never reaches the source:
SPA → `GET /api/patient-armc/{patientId}` → backend →
`GET {base}/Patient/{id}/armc`.

## The elements

Every ARMC Board has every element but the End Date.

| Element            | On the wire (both sides)           | Shown as                                            |
| ------------------ | ---------------------------------- | --------------------------------------------------- |
| ARMC Board Type    | `boardType`, a code                | `aces` ACES, in bold                                |
| ARMC Outcome       | `outcome`, a code                  | `fit` FIT (green badge) · `unfit` UNFIT (red badge) |
| Start Date         | `startDate`, `YYYY-MM-DD`          | `01 Jan 2024`                                       |
| End Date           | `endDate`, `YYYY-MM-DD`, or absent | `10 Feb 2024`, or `—` when absent                   |
| Medical Officer #1 | `medicalOfficer1`, a name          | the name, as the source gives it                    |
| Medical Officer #2 | `medicalOfficer2`, a name          | the name, as the source gives it                    |

Each ARMC Board also carries its own `id` at the source, in FHIR's id grammar,
which the page keys its row by and never shows.

- **The End Date is the one optional element** of any history read from the
  source. An absent End Date is a board still open. The source leaves the field
  out, and so does our API: it is never sent as `null`. Every other date of
  every history — an FFI's, a NUMC Record's, an ARMC Board's Start Date — stays
  required.
- **When the End Date is there, it is on or after the Start Date**; a board may
  start and end on the same day. The rule is the domain's own (`ArmcBoard`), so
  a board ending before it starts is a failure of the source, never a row.
- **The two Medical Officers are names**, shown exactly as the source gives
  them. They are not linked to our Users or accounts, and nothing looks them
  up. A missing or blank name is a failure of the source.

### The closed sets

The ARMC Board Type and the ARMC Outcome are closed sets, carried and matched
as an FFI's are ([ffi](ffi.md), "The closed sets"): each value travels as its
code, from the source and to the SPA, matched exactly, and the SPA alone names
it with its label. A code the service does not know is a failure of the source,
never a row with a blank cell. Each set is one Java enum (`ArmcBoardType`,
`ArmcOutcome`, codes through `SourceCode`), one enum in
`backend/docs/openapi.yaml` and one SPA set (`src/pages/armc/armc-api.ts`), held
set-equal by `VocabularyContractTests` and by `make verify-contract`
(`/scripts/spa-contract/README.md`).

**The ARMC Outcome is a set of its own**, apart from an FFI's Outcome and a
NUMC Record's NUMC Outcome: `fit` and `unfit` are codes of all three, but a
NUMC Outcome's `waiver-granted` or an FFI's `request-waiver` is no ARMC Outcome,
and a board carrying either is a failure of the source.

## The source contract

As an FFI History's ([ffi](ffi.md), "The source contract"), FHIR-shaped paging
only — not FHIR-conformant:

- **The call.** `GET {base}/Patient/{id}/armc?_offset=…&_count=…&_total=accurate`,
  on the same client, base URL, timeouts and absent credential, the id sent
  unchanged, with each filter and the sort the read asks for
  ([Sort and filters](#sort-and-filters)). With no sort asked for, no `_sort`
  is sent, so the source's own order is the order shown.
- **The answer.** A FHIR-R4-shaped `searchset` `Bundle` — its `total` and each
  `entry[].resource` — whose every resource is a custom `"resourceType": "ARMC"`
  object of flat fields: `id`, `boardType`, `outcome`, `startDate`, `endDate`
  (absent for a board still open), `medicalOfficer1` and `medicalOfficer2`.
  Nothing else in the Bundle is read, and any other field of a board is never
  bound.
- **The page.** Read by ADR 0017's rules and sent as `_offset`
  (`startIndex - 1`) and `_count`; `totalResults` is the source's `total`, and
  the source is asked even for `count=0` or a page past the end (ADR 0018, D3).
  The SPA asks for 5. `SourceSearchset` reads it, as it reads an FFI History's,
  and `SourceFields` each field, its optional calendar date for the End Date.
- **Only a `404` is no patient**, our API's `404` too. Every other answer is a
  failure of the source, our `502` ([Failures](#failures)).
- **No caching.** No ARMC Board is kept in Redis or in memory: every read, of
  the same page too, is a call to the source.

## Sort and filters

The patient source sorts, filters, pages and counts an ARMC History; the
service checks the sort and the filters, passes them through and does none of
it itself, as for an FFI or a NUMC History ([ffi](ffi.md), "Sort and filters";
[numc](numc.md), "Sort and filters"; ADR 0019, D4 and D6).

- **The sort.** One column at a time, by `sortBy` — `startDate` or `endDate`
  — and `sortOrder` — `ascending` (the default with a `sortBy`) or
  `descending`. It is sent as `_sort` on the source's own search parameters,
  `start-date` or `end-date`, `-` first for descending, the column alone:
  `_sort=-end-date`. **The source breaks ties**, and decides where a board
  with no End Date falls in an End Date sort: no `_id` tie-break is sent.
  **With no `sortBy`** — whatever `sortOrder` says — **no `_sort` is sent**,
  and the source's default order is the order shown. The domain's
  `HistorySort` holds it, as for an FFI or a NUMC History, with the ARMC
  History's own columns (`HistorySort.StartOrEnd`).
- **The filters** narrow the history, combined with AND, each optional
  (`ArmcFilter`):

  | Column       | Our API                        | Sent to the source                         |
  | ------------ | ------------------------------ | ------------------------------------------ |
  | ARMC Outcome | `outcome`, one code            | `outcome=<code>`                           |
  | Start Date   | `startDateFrom`, `startDateTo` | `start-date=ge<from>`, `start-date=le<to>` |
  | End Date     | `endDateFrom`, `endDateTo`     | `end-date=ge<from>`, `end-date=le<to>`     |

  The Outcome is one code of its [closed set](#the-closed-sets), matched
  exactly: a filter on it is that one Outcome, and none is every Outcome. Each
  end of a date range is a calendar date, `YYYY-MM-DD`, optional and
  inclusive; a from-date may not be after its to-date (`DateRange`). There is
  no filter on the ARMC Board Type while ACES is its only code, and none on
  the Medical Officers. A parameter of another history's — `type`,
  `issueDateFrom`, `status` — is no parameter the read takes, and is not
  passed on.

- **A board with no End Date matches no End Date range.** The source applies
  the rule: under any End Date bound — a from-date, a to-date or both — a
  board still open is left out, whatever its Start Date. With no End Date
  bound, an open board is narrowed by its other columns alone.
- **Checked as the request is read in.** A column or direction spelled any
  other way, an Outcome outside its closed set — a label, another case, a Java
  constant's name, or another set's code, a NUMC Outcome's `waiver-granted`
  among them — an Outcome given twice, a date that is no calendar date, and a
  from-date after its to-date are each `400`, before the source is called.

## Our API

`GET /api/patient-armc/{patientId}?startIndex=…&count=…`, with any filters and
sort, outside the
`/api/patient` FHIR namespace: plain JSON, `{totalResults, startIndex,
records: [{id, boardType, outcome, startDate, endDate, medicalOfficer1,
medicalOfficer2}]}`, each code as the source sent it, both dates `YYYY-MM-DD`,
`endDate` absent for a board still open, and both names as the source gave
them. `totalResults` counts the ARMC Boards the filters select. Errors are the
application API's `ApiError`:

| Case                                                                                                                                                       | Status |
| ---------------------------------------------------------------------------------------------------------------------------------------------------------- | ------ |
| `patientId` outside FHIR's id grammar, a sort or filter the read cannot take ([Sort and filters](#sort-and-filters)), or a paging value that is no integer | `400`  |
| No session                                                                                                                                                 | `401`  |
| Session lacks `patient:read`                                                                                                                               | `403`  |
| The source's `404` for the patient                                                                                                                         | `404`  |
| The source failing                                                                                                                                         | `502`  |

The `400` is refused before the source is called. The `403` is refused by the
handler's `@PreAuthorize`, the chain's backstop and the document's `security`,
audited as an `ACCESS_DENIED` and logged as every authorization refusal is.

## Failures

Each of these is a failure of the source, our `502`, and its one fault record's
`event.reason`. A failure of the call or of the searchset is the one every paged
read of the source shares; **every rule an ARMC Board breaks has a reason
naming ARMC**, so a log line says which history broke.

| Failure                                                               | `event.reason`                                                                                                          | `error.category` |
| --------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------- | ---------------- |
| No connection, or one refused, reset or dropped                       | `unreachable`                                                                                                           | `network`        |
| No connection within 2 seconds, or no whole answer within 5           | `timed-out`                                                                                                             | `network`        |
| A `5xx`                                                               | `server-error`                                                                                                          | `server`         |
| Any other status but a `200` or a `404`                               | `unexpected-status`                                                                                                     | `data`           |
| A body that arrived whole and is not JSON or not the shape            | `unreadable`                                                                                                            | `data`           |
| A `200` that is no searchset `Bundle`                                 | `not-a-searchset`                                                                                                       | `data`           |
| A searchset with no `total`, or a negative one                        | `no-total`                                                                                                              | `data`           |
| An entry with no resource, or one that is no `ARMC`                   | `another-resource`                                                                                                      | `data`           |
| An entry with no `id`, or one outside FHIR's id grammar               | `armc-no-id`, `armc-invalid-id`                                                                                         | `data`           |
| A board missing a required element, or naming a blank Medical Officer | `no-armc-board-type`, `no-armc-outcome`, `no-armc-start-date`, `no-armc-medical-officer-1`, `no-armc-medical-officer-2` | `data`           |
| A code outside its closed set                                         | `unknown-armc-board-type`, `unknown-armc-outcome`                                                                       | `data`           |
| A date not to the day                                                 | `armc-start-date-not-to-the-day`, `armc-end-date-not-to-the-day`                                                        | `data`           |
| An End Date before its Start Date                                     | `armc-end-before-start`                                                                                                 | `data`           |

An absent End Date is none of these: it is a board still open. The adapter
raises each as a `PatientSourceException`, which the app-wide handler answers
`502` with the generic `server-error` `ApiError`, as for an FFI History.

## Logging

As an FFI History's ([ffi](ffi.md), "Logging"): each failure of the source is
exactly one `ERROR`, the `502`'s `http.request.fault` record naming its reason;
neither the log nor the exception's message names the patient id or anything of
the source's body — not a board's id, code, date or Medical Officer, nor an
error answer's body; and a read adds no record of its own, the request record
naming the route template (`/api/patient-armc/{patientId}`), never the id.
`ArmcHistoryIntegrationTests` holds each ARMC fault stub's read to exactly one
`ERROR` naming its failure under its category, and the stream, for those reads
and for successful ones, to naming neither the patient id nor any board's id,
dates or Medical Officers.

## Not audited

An ARMC History read writes **no audit row**, sorted and filtered too, as an
FFI History read and a read of one Patient by id write none (ADR 0019, D3 and
D6), and the log names none of a filter's values. A refused read is audited
as `ACCESS_DENIED`, as every authorization refusal is.

## The ARMC page

`/armc`, titled **ARMC**, linked **ARMC** in the sidebar after NUMC, with the
`Plane` icon, for a session holding `patient:read`, and guarded by it as NUMC
is: a session without it is offered no link and is routed to its landing page.

- **It reads the patient in context** as the FFI page does, from the signed-in
  layout's one reader of it ([patient](patient.md), "The patient in context"),
  through the same shared wrapper: it reads that patient's ARMC History once the
  patient is read, says "Loading ARMC records…" while the patient or the first
  page is read, and a failed read of the patient leaves it empty. It is
  read-only, five ARMC Boards a page, with no row action.
- **The table**: Board Type, Outcome, Start Date, End Date, Medical Officer #1,
  Medical Officer #2 — the ARMC Board Type by its label, in bold, the ARMC
  Outcome as its badge ([the elements](#the-elements)), both dates
  `dd MMM yyyy` and `—` for an absent End Date, and both names as the source
  gave them.
- **The shared paged listing**: the numbered pager and "Showing x to y of N
  ARMC records", named on a single page too, the page shown held while the next
  loads.
- **Sort.** As on the FFI and NUMC pages ([ffi](ffi.md), "The FFI page"): only
  the Start Date and End Date headings sort, one at a time, each the shared
  sortable column heading stating its direction with `aria-sort`; Board Type,
  Outcome and both Medical Officers are plain. The history opens in the
  source's own order, which neither heading shows as sorted. The first press on
  a heading sorts it ascending, and each later press toggles it descending and
  ascending, never back to the source's order; another heading replaces the
  sort, starting ascending. The source breaks ties. Both headings are disabled
  while a read is pending.
- **Filter.** A Filter button at the top right of the ARMC History opens a
  panel of the three filters, the button counting those applied ("Filter, 2
  set", a badge of 2 — the Outcome and each end of a range one each): an
  All / FIT / UNFIT toggle for the Outcome, one pressed at a time, built as the
  Search page's Gender filter is (`ChoiceToggle`), All being no filter; and a
  from–to range of `<input type="date">` for Start Date and for End Date, each
  end optional and inclusive. Nothing in the panel is sent until Apply, or
  Enter in a date; Reset clears every filter and applies none. A from-date
  after its to-date is refused beneath its range — "The End Date from-date
  must not be after its to-date." — the panel stays open and nothing is read.
  The panel is the FFI and NUMC pages', shared (`HistoryFilterPopover`), with
  the ARMC History's own fields.
- **Filters and sort read from the first page.** Applying filters, or any
  change of sort, reads the history from its first page, the filters combined
  with AND and with the current sort. Both live on the page alone: neither
  reaches the URL or the browser history, and a new patient in context starts
  with neither.
- **No patient in context** — the tab holds none, or the source holds no
  patient under its id (its `404`, our `404`) — shows "No patient in context",
  as the FFI and NUMC pages do, and reads no ARMC Boards or reports nothing.
- **A patient with no ARMC Boards** — a `200` with `total: 0` — shows "No ARMC
  records". A filter matching none of a patient's ARMC Boards shows "No ARMC
  records match the filters".
- **The source failing** shows "Couldn't load ARMC records" on the page's error
  line, and Try again, which reads the same page again.

## The patient source's ARMC fixtures

The patient source's mock (`backend/patient-source-mock`, [patient](patient.md),
"The patient source's fixtures") serves the ARMC Histories of the development
fixtures beside their FFI and NUMC Histories, on the same patients, and the
backend's tests read the same stub files (`PatientSourceStubs`).

- **Tan Wei Ming** (`eq081-VQEgP8drUUqCWzHfw3`) has exactly two ARMC Boards, on
  one page (`armc-tan-wei-ming`):

  | ARMC Board Type | ARMC Outcome | Start Date  | End Date    | Medical Officer #1 | Medical Officer #2 |
  | --------------- | ------------ | ----------- | ----------- | ------------------ | ------------------ |
  | ACES            | FIT          | 01 Jan 2024 | 10 Feb 2024 | Medical Officer 1  | Medical Officer 2  |
  | ACES            | UNFIT        | 21 Nov 2020 | 02 Apr 2021 | Medical Officer 3  | Medical Officer 4  |

- **Tan Hui Min** (`dev-patient-05`) has seven ARMC Boards over two pages of
  five (`armc-tan-hui-min-page-1`, `-page-2`), between them every code of both
  closed sets. The first is still open, with no End Date — the one open board
  among the fixtures — and the third starts and ends on the same day.
- **Every other fixture** has none — Siti Nurhaliza binte Ahmad
  (`erXuFYUfucBZaryVksYEcMg3`) is the one the tests read — answered by
  `armc-fallback`, which also answers any page, filter or sort of a fixture's
  history the canned stubs do not name, with an empty searchset, `total: 0`.
- **Any other well-formed id** is the source's `404` (`armc-not-found`).

Each canned page is matched on the whole query the backend sends — `_count=5`,
`_offset`, `_total=accurate`, the `_sort`, and each filter pinned to its exact
value or, where the read uses none, to absent — so a filtered or sorted read
never falls to an unfiltered page. Each fixture is selectable on Patient Search
(the name search `tan` finds both Tans).

**Sort and filter stubs.** Beside the pages in the source's order:

- **The sorts**, of Tan Wei Ming's two ARMC Boards, whose own order is the
  latest first: `armc-tan-wei-ming-by-start-date` (`_sort=start-date`,
  earliest first: the UNFIT board of 2020, then the FIT board of 2024),
  `-by-start-date-descending` (`-start-date`, the reverse), and
  `-by-end-date` and `-by-end-date-descending` (`end-date`, the same two
  orders).
- **The filters**, of Tan Hui Min's seven, in the source's order:
  `armc-tan-hui-min-fit` (`outcome=fit`, `total: 4`, the open board among
  them); `-unfit` (`outcome=unfit`, `total: 3`); `-started-2023`
  (`start-date=ge2023-01-01` and `le2023-12-31`, `total: 2`);
  `-ended-from-2023` (`end-date=ge2023-01-01`, `total: 3`, which leaves out
  the open board though it starts in 2025); `-fit-started-from-2023`
  (`outcome=fit` and `start-date=ge2023-01-01`, two filters combined,
  `total: 3`); and `-started-to-2019` (`start-date=le2019-12-31`, which no
  ARMC Board matches, `total: 0`).

`PatientSourceFixturesTests` holds each sorted or filtered stub to the ARMC
Boards its filters select from the history in the source's order — an open
board in no End Date range — in the order its sort names, and the stubs to
covering each direction of each date, every filter, an End Date range leaving
out an open board, filters combined and a filter matching none.

**Fault stubs.** Beside them, each failure above has a stub of its own under a
well-formed patient id no fixture uses, `fault-armc-<failure>`, marked with
`metadata.fault`: `unreachable` (the connection reset), `slow` (the answer after
7 seconds), `server-error` (`503`), `unexpected-status` (`400`),
`not-a-searchset`, `no-total`, `no-id`, `invalid-id`, `no-outcome` and
`no-medical-officer-2` (a board missing a required element),
`unknown-board-type` and `unknown-outcome` (a code outside each set, the second
a NUMC Outcome's `waiver-granted`), `end-before-start` and
`end-date-not-to-the-day`. A fault stub is no fixture.
`PatientSourceFixturesTests` holds the fixtures to these rules,
`PatientStoreContract` holds what each history reads, written out, and
`PatientSourceArmcAdapterTests` each fault stub's failure.
