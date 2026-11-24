---
type: Domain Rules
title: HSP Records
description: A patient's HSP History — the in-house patient source's own HSP Records, read through it at GET {base}/Patient/{id}/hsp as a FHIR-shaped but non-conformant searchset and shown as received, the elements read and the two closed sets carried as codes beside the shared Medical Centre, a field missing, in the wrong format or outside its set, the Mandatory elements and the warning on a row missing one, the sort by Start Date or End Date and the filters on every column with the source parameter each is sent as and the refusals, the failures, the logging rules, no caching, no auditing, the HSP page with the Patient card above its table, and the mock's HSP fixtures, sort and filter stubs and fault stubs.
tags: [patient, hsp]
---

# HSP Records

**HSP Record** — one record of a patient's HSP assessment, as the in-house
patient source holds it: the risk it found and the period it covers. A
patient's **HSP History** is every HSP Record the source holds for them, in the
source's own order unless sorted by its Start Date or End Date, and may be
narrowed by a filter on any column. The backend's `patient` module reads it
from the source on every read and serves it, read-only, at
`GET /api/patient-hsp/{patientId}` to a session holding `patient:read`
([authorization](authorization.md)), as plain JSON a page at a time. It is the
FFI History's shape over fewer elements ([ffi](ffi.md)): the source filters,
sorts, pages and counts it; nothing stores it; it is the source's, read
through and not audited (ADR 0019), and **shown as received** (ADR 0020). The
wire contract is `backend/docs/openapi.yaml`. The SPA never reaches the source:
SPA → `GET /api/patient-hsp/{patientId}` → backend →
`GET {base}/Patient/{id}/hsp`.

## The elements

An HSP Record has five elements, each read as the patient source sent it
([Shown as received](#shown-as-received)).

| Element       | On the wire (both sides)  | Shown as                                                                              |
| ------------- | ------------------------- | ------------------------------------------------------------------------------------- |
| Outcome       | `outcome`, a code         | `low` LOW (green badge) · `moderate` MODERATE (amber badge) · `high` HIGH (red badge) |
| Start Date    | `startDate`, `YYYY-MM-DD` | `05 Feb 2001`                                                                         |
| End Date      | `endDate`, `YYYY-MM-DD`   | `05 Feb 2001`                                                                         |
| Submitting MC | `submittingMc`, a code    | a Medical Centre ([ffi](ffi.md), "The elements"): `nee-soon-camp` Nee Soon Camp MC, … |
| Status        | `status`, a code          | `active` Active (green text) · `inactive` Inactive (muted text)                       |

Each record also carries its own `id` at the source, never checked, which the
page keys its row by and never shows; a row with none is keyed by its place.

### The closed sets

The HSP Outcome and HSP Status — the Outcome and Status columns — are closed
sets of the HSP Record's own, and the
Submitting MC draws on **Medical Centre**, the set an FFI's Submitting MC and a
Medication Record's Dispensary share. Each value travels as its **code** — from
the source, and to the SPA — and the SPA alone names a known code with its
**label**. **A code outside its set is shown as the raw code**, the Outcome's
on a white pill rather than a coloured one (ADR 0020, D2): never a failure of the source, and never a
blank cell. The sets check what the service is asked: a filter's code is
matched exactly, and the Filter panel offers only the known codes. Each set is
one Java enum (`HspOutcome`, `HspStatus`, `MedicalCentre`, codes through
`SourceCode`), one enum in `backend/docs/openapi.yaml` and one SPA set
(`src/pages/hsp/hsp-api.ts`, `src/pages/medical-centre-api.ts`), held set-equal
by `VocabularyContractTests` and by `make verify-contract`
(`/scripts/spa-contract/README.md`).

### Shown as received

As for an FFI ([ffi](ffi.md), "Shown as received"): nothing refuses an HSP
Record, or the response, for what a field holds. Each field — the five
elements and the `id` — is present (its value, a code outside its set
included), missing (no key, `null`, `""` or only whitespace: `null` on our API,
"—" on the page) or invalid (a number, a boolean, an object or an array, or a
date that is no calendar date: `null`, named in the row's `invalid`, "Error"
on the page). **No semantic or range check is made**: an End Date before its
Start Date is shown as received. The domain's `HspRecord` holds each field as
a `SourceField` and refuses none.

### Mandatory elements

An HSP Record's **Mandatory elements** are its **HSP Outcome** and **HSP
Status** (ADR 0020, D7), headed Outcome and Status. A row whose Mandatory
element is missing or invalid carries the shared warning
([medication](medication.md), "Mandatory elements") in a leading column before
the Outcome, shown only on a page where a row is flagged — `Critical
information missing: Outcome, Status` — and is still shown in full. A code
outside its closed set is present and flags nothing; no other field flags a
row, its dates and Submitting MC included.

### Status is the source's

**HSP Status is the source's own field.** It is never derived from the End Date: a
record past its End Date may still be Active.

## The source contract

The FFI History's contract ([ffi](ffi.md), "The source contract"), at its own
path:

- **The call.** `GET {base}/Patient/{id}/hsp?_offset=…&_count=…&_total=accurate`,
  on the same client, base URL and timeouts as every read of the source, each
  filter asked for and the sort beside the page; with no sort chosen, no
  `_sort` is sent.
- **The answer.** A FHIR-R4-shaped `searchset` `Bundle` whose every resource is
  a custom `"resourceType": "HSP"` object of flat fields: `id`, `outcome`,
  `startDate`, `endDate`, `submittingMc` and `status`.
- **The page.** As every history's (ADR 0017; `SourceSearchset`). The SPA asks
  for 5.
- **Only a `404` is no patient.** Every other status, and a `200` that is no
  searchset of HSP Records, is a failure of the source, our `502`.
- **No caching.**

## Sort and filters

The patient source sorts, filters, pages and counts an HSP History; the
service passes them through ([ffi](ffi.md), "Sort and filters", for the rules
every history shares). The HSP History's two date columns, the Start Date and
the End Date, are calendar dates (`HspHistory.DATE_COLUMNS`,
`HSP_DATE_COLUMNS`).

- **The sort.** One column at a time, by `sortBy` — `startDate` or `endDate`
  — and `sortOrder`. It is sent as `_sort` on `start-date` or `end-date`, `-`
  first for descending, ties broken by id: `_sort=-start-date,_id`. With no
  `sortBy`, no `_sort` is sent.
- **The filters** narrow the history, combined with AND, each optional
  (`HspFilter`):

  | Column        | Our API                        | Sent to the source                         |
  | ------------- | ------------------------------ | ------------------------------------------ |
  | Outcome       | `outcome`, repeated            | `outcome=<a>,<b>`                          |
  | Start Date    | `startDateFrom`, `startDateTo` | `start-date=ge<from>`, `start-date=le<to>` |
  | End Date      | `endDateFrom`, `endDateTo`     | `end-date=ge<from>`, `end-date=le<to>`     |
  | Submitting MC | `submittingMc`, repeated       | `submitting-mc=<a>,<b>`                    |
  | Status        | `status`, absent for either    | `status`                                   |

- **Checked as the request is read in.** A column or direction spelled any
  other way, a code outside its closed set, a date that is no calendar date
  and a from-date after its to-date are each `400`, before the source is
  called.

## Our API

`GET /api/patient-hsp/{patientId}?startIndex=…&count=…`, with any filters and
sort: plain JSON, `{totalResults, startIndex, records: [{id, outcome,
startDate, endDate, submittingMc, status, invalid}]}`. Every field is always
there: one missing or in the wrong format is `null`, and `invalid` names, in
the row's order, those whose format was wrong. Its statuses are the FFI
History's ([ffi](ffi.md), "Our API"): `400` for a malformed id, sort, filter or
paging value, before the source is called; `401`; `403` without
`patient:read`; `404` for the source's `404`; `502` for the source failing.

## Failures and logging

The failures, their `event.reason` and `error.category`, and the logging rules
are the FFI History's ([ffi](ffi.md), "Failures" and "Logging"), every paged
read of the source sharing them: one `ERROR` per failure, naming neither the
patient id, a filter's value nor anything of the body; a response holding
fields in the wrong format is one `patient_source.invalid_fields` `WARN`, its
`app.patient_source.read` `hsp`, and counted under `read=hsp`.

## Not audited

An HSP read writes **no audit row**, sorted or filtered too (ADR 0019). A
refused read is audited as `ACCESS_DENIED`, as every authorization refusal is.

## The HSP page

`/hsp`, linked **HSP** in the sidebar after ARMC for a session holding
`patient:read`, and guarded by it as every history page is. Titled "HSP
History".

- **The Patient card heads it**, as on the Demographic page: the patient in
  context's banner, and beneath it a card headed "HSP History" holding the
  table, its Filter button at the card's top right. The page's title is
  hidden while the banner heads the page.
- **No patient in context** — the tab holds none, or the source holds no
  patient under its id — shows the page's title above the Patient card's plain
  "No patient in context" card, and no HSP History card; nothing is read.
- **The table**: a leading column of warnings ([Mandatory
  elements](#mandatory-elements)), then Outcome, Start Date, End Date,
  Submitting MC and Status — the Outcome as a badge, both dates `dd MMM yyyy`,
  the Submitting MC by its label, the Status by its label in its colour; a code
  outside its set as it came; a missing field "—" and one in the wrong format
  "Error". Five records a page, through the shared paged listing, "Showing x
  to y of N records" named on a single page too. Read-only, with no row action.
- **Sort.** Only the Start Date and End Date headings sort, as on the FFI page.
- **Filter.** The Filter button opens a panel of the five filters, counting
  those applied: a searchable multi-select for the Outcome and for the
  Submitting MC; a from–to range of `<input type="date">` for the Start Date
  and for the End Date; and the Status — All (the default), Active or
  Inactive. Apply, Reset, the refusal of a from-date after its to-date, and
  reading from the first page are the FFI page's.
- **A patient with no HSP Records** shows "No records found."; a filter
  matching none, "No records match the filters."
- **The source failing** shows "Couldn't load HSP records" on the page's error
  line, and Try again.

## The patient source's HSP fixtures

The mock (`backend/patient-source-mock`) serves the HSP Histories of the
development fixtures, and the backend's tests read the same stub files
(`PatientSourceStubs`).

- **Tan Wei Ming** (`eq081-VQEgP8drUUqCWzHfw3`) has exactly three HSP Records,
  on one page (`hsp-tan-wei-ming`), in the source's order:

  | Outcome  | Start Date  | End Date    | Submitting MC       | Status   |
  | -------- | ----------- | ----------- | ------------------- | -------- |
  | MODERATE | 14 Oct 2023 | 13 Oct 2024 | Mandai Hill Camp MC | Inactive |
  | LOW      | 11 Mar 2024 | 10 Mar 2025 | Nee Soon Camp MC    | Active   |
  | HIGH     | 22 Nov 2022 | 10 Oct 2023 | Hendon Camp MC      | Inactive |

- **Tan Boon Huat** (`dev-patient-10`) has three HSP Records, on one page,
  shown as received (`hsp-tan-boon-huat`): one with no outcome and a `null`
  Submitting MC; one whose Start Date is a number, whose End Date is a month,
  not a day, and whose Status is a boolean; and one with no id whose codes are
  outside their sets — `critical`, `tengah-air-base` and `expired`:

  | Outcome  | Start Date  | End Date    | Submitting MC   | Status  |
  | -------- | ----------- | ----------- | --------------- | ------- |
  | —        | 16 Mar 2022 | 15 Mar 2023 | —               | Active  |
  | LOW      | Error       | Error       | Kranji Camp MC  | Error   |
  | critical | 16 Mar 2020 | 15 Mar 2021 | tengah-air-base | expired |

  The first, its Outcome missing, and the second, its Status invalid, carry a
  warning, the second's naming its Status alone; the third, its codes outside
  their sets, does not.

- **Every other fixture** has none, answered by `hsp-fallback` with an empty
  searchset, `total: 0`, as is any page, filter or sort of a fixture's history
  the canned stubs do not name.
- **Any other well-formed id** is the source's `404` (`hsp-not-found`).

Each canned page is matched on the whole query the backend sends — `_count=5`,
`_offset`, `_total=accurate`, the `_sort`, and each filter pinned to its exact
values or to absent.

**Sort and filter stubs**, of Tan Wei Ming's three, whose own order is by
neither date in either direction, so each sorted read answers an order no
unsorted read does (`PatientSourceFixturesTests` holds his own order to none
of the four sorted ones):

- `hsp-tan-wei-ming-by-start-date` (`_sort=start-date,_id`, earliest first:
  HIGH, MODERATE, LOW) and `-by-start-date-descending` (`-start-date`, the
  reverse); `-by-end-date` and `-by-end-date-descending` (`end-date`, the same
  two orders).
- `hsp-tan-wei-ming-active` (`status=active`, `total: 1`: LOW) and
  `hsp-tan-wei-ming-low-or-high` (`outcome=low,high`, `total: 2`: LOW, HIGH).

**Fault stubs**, `fault-hsp-<failure>`, each under a well-formed patient id no
fixture uses and marked with `metadata.fault`: `unreachable`, `slow`,
`server-error`, `unexpected-status`, `not-a-searchset` and `no-total` — the
failures every history shares.
