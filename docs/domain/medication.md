---
type: Domain Rules
title: Medication
description: A patient's Medication History — the in-house patient source's own Medication Records, read through it at GET {base}/Patient/{id}/medication on the FFI History's terms and shown as received, the twelve elements, the free text shown as the source gives it, the Start Date and End Date as date-times with an offset shown in Singapore time, the Dispense Status and the Dispensary's Medical Centre carried as codes, a field missing, in the wrong format or outside its set, the Mandatory elements and the warning on a row missing one, the source's order and paging, the sort by either date-time and the filters on the Dispense Status and a date-time range over either date, the failures, the logging rules and the invalid-fields WARN and counter, no caching, no auditing, the Medication page and its states, and the mock's Medication fixtures, sort and filter stubs and fault stubs.
tags: [patient, medication]
---

# Medication

**Medication Record** — one prescription of a patient's, with how it was
dispensed and who prescribed it, as the in-house patient source holds it. A
patient's **Medication History** is every Medication Record the source holds
for them, in the source's own order. The backend's `patient` module reads it
from the source on every read and serves it, read-only, at
`GET /api/patient-medication/{patientId}` to a session holding `patient:read`
([authorization](authorization.md)), as plain JSON a page at a time. Nothing
stores it.

It is read on the same terms as an FFI History (ADR 0019, D7): what the
[FFI rules](ffi.md) say of the source's paged read, its client and timeouts,
its failures, its logging, caching and auditing holds here too, and this file
says only where a Medication History differs — the endpoint, the twelve
elements, the date-times, the closed sets, and its sort and filters — and what
is its own. As an FFI History is, it is **shown as received** (ADR 0020): no
Medication Record fails the read for what its fields hold. The wire
contract is `backend/docs/openapi.yaml`. The SPA never reaches the source:
SPA → `GET /api/patient-medication/{patientId}` → backend →
`GET {base}/Patient/{id}/medication`.

## The elements

A Medication Record has twelve elements, each read as the patient source sent
it ([Shown as received](#shown-as-received)).

| Element            | On the wire (both sides)                       | Shown as                                                                                                      |
| ------------------ | ---------------------------------------------- | ------------------------------------------------------------------------------------------------------------- |
| Medication         | `medication`, free text                        | as the source gives it, in bold                                                                               |
| Dosage             | `dosage`, free text                            | as the source gives it                                                                                        |
| Route              | `route`, free text                             | as the source gives it                                                                                        |
| Frequency          | `frequency`, free text                         | as the source gives it                                                                                        |
| Step               | `step`, free text                              | as the source gives it                                                                                        |
| Quantity           | `quantity`, free text                          | as the source gives it                                                                                        |
| Dispense Status    | `dispenseStatus`, a code                       | `pending` PENDING (muted badge) · `dispensed` DISPENSED (green badge) · `uncollected` UNCOLLECTED (red badge) |
| Duration           | `duration`, free text                          | as the source gives it                                                                                        |
| Start Date         | `startDate`, ISO-8601 date-time with an offset | `18 Oct 2026 17:30`, in Singapore time                                                                        |
| End Date           | `endDate`, ISO-8601 date-time with an offset   | `22 Oct 2026 17:30`, in Singapore time                                                                        |
| Dispensary         | `dispensary`, a code                           | a Medical Centre's label ([ffi](ffi.md), "The closed sets")                                                   |
| Prescribing Doctor | `prescribingDoctor`, a name                    | the name, as the source gives it                                                                              |

Each Medication Record also carries its own `id` at the source, never checked,
which the page keys its row by and never shows; a row with none is keyed by
its place.

- **Free text is the source's.** The Medication, Dosage, Route, Frequency,
  Step, Quantity, Duration and Prescribing Doctor are shown exactly as the
  source gives them, and nothing reads them. The Duration is its own element
  ("5 days"): nothing works it out from the dates. A prescription may lack any
  of them, the Dosage, Route, Frequency, Step, Quantity and Duration most
  often: a missing one is shown "—".
- **The Prescribing Doctor is a name.** It is not linked to our Users or
  accounts, and nothing looks it up.
- **The Start Date and End Date are date-times**, to the minute. The source
  gives each as ISO-8601 with an offset (`Z` among them), and our API passes
  each on with the offset the source gave. The page shows each in Singapore
  time, `dd MMM yyyy HH:mm`, its seconds dropped. One with no offset names no
  instant: it is in the wrong format, "Error" in its cell.
- **Nothing compares the two dates.** An End Date before the Start Date is
  shown as the source sent it, as is a record ending the minute it starts.

### The closed sets

The Dispense Status and the Dispensary are closed sets, carried as an FFI's are
([ffi](ffi.md), "The closed sets"): each value travels as its code, from the
source and to the SPA, and the SPA alone names a known code with its label. **A
code outside its set is shown as the raw code**, the Dispense Status's on a
neutral badge (ADR 0020, D2) — never a failure of the source, and never a blank
cell. The sets check what the service is asked: a filter's Dispense Status is
matched exactly, and the Filter panel offers only the known codes
([Sort and filters](#sort-and-filters)).

- **Dispense Status** is one Java enum (`DispenseStatus`, codes through
  `SourceCode`), one enum in `backend/docs/openapi.yaml` (`DispenseStatus`) and
  one SPA set (`src/pages/medication/medication-api.ts`).
- **The Dispensary draws on Medical Centre**, the set an FFI's Submitting MC
  draws on: one Java enum (`MedicalCentre`), one enum in
  `backend/docs/openapi.yaml` (`MedicalCentre`) and one SPA set
  (`src/pages/medical-centre-api.ts`), shared by both histories.

Each is held set-equal by `VocabularyContractTests` and by
`make verify-contract` (`/scripts/spa-contract/README.md`).

### Shown as received

The patient source is the truth (ADR 0020): nothing refuses a Medication
Record, or the response, for what a field holds. Each field — the twelve
elements and the `id` — is one of three things, read by the adapter from the
raw JSON it arrived as (`ReceivedFields`, so a number is never coerced into a
string):

| The source sent                                                                                                                                             | The field is | Our API                              | The page shows |
| ----------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------ | ------------------------------------ | -------------- |
| No key, `null`, `""`, or only whitespace                                                                                                                    | missing      | `null`                               | "—"            |
| A number, a boolean, an object or an array, free text included; a date that is no date-time with an offset — a date alone, one with no offset, no such time | invalid      | `null`, named in the row's `invalid` | "Error"        |
| Any other string, as it came: a code outside its set, an id outside FHIR's grammar, any date-time with an offset                                            | present      | the value                            | the value      |

**No semantic or range check is made**: a date-time in the future, or an End
Date before its Start Date, is shown as received. The domain's
`MedicationRecord` holds each element as a `SourceField` and refuses none.

### Mandatory elements

A Medication Record's **Mandatory elements** are its **Medication** and its
**Dispense Status** (ADR 0020, D7). A row whose Mandatory element is missing
or invalid carries a warning; the record is still shown in full, each such
cell "—" or "Error", and is never refused. The page decides it from the
backend's classification alone and reads no value: a Dispense Status outside
its [closed set](#the-closed-sets) is present and flags nothing, and no other
element flags a row, missing or invalid.

- **The warning** is a warning-coloured triangle (`TriangleAlert`) in a narrow
  leading column before the Medication, headed "Missing information" for a
  screen reader alone. The column shows
  only on a page where at least one row is flagged, where an unflagged row's
  cell is empty; a page with no flagged row has no leading column.
- **One per row.** Its tooltip, which is also its accessible name, is
  "Critical information missing: " and each affected column's heading, in the
  table's order, joined by commas —
  `Critical information missing: Medication, Dispense Status` — for a missing
  element and one in the wrong format alike.
- **It only informs.** It is a tab stop, its tooltip opening on hover and on
  keyboard focus; a press does nothing, and nothing counts, filters or sorts
  the flagged rows.

The mechanism is shared (`src/pages/mandatory-elements.tsx`), each history
declaring its own Mandatory elements beside its table; the Medication History
is the one that declares any.

## The source contract

As an FFI History's ([ffi](ffi.md), "The source contract"), FHIR-shaped paging
only — not FHIR-conformant:

- **The call.**
  `GET {base}/Patient/{id}/medication?_offset=…&_count=…&_total=accurate`, on
  the same client, base URL, timeouts and absent credential, the id sent
  unchanged, with each filter and the sort the read asks for
  ([Sort and filters](#sort-and-filters)). With no sort asked for, no `_sort`
  is sent, so the source's own order is the order shown.
- **The answer.** A FHIR-R4-shaped `searchset` `Bundle` — its `total` and each
  `entry[].resource` — whose every resource is a custom
  `"resourceType": "Medication"` object of flat fields: `id`, `medication`,
  `dosage`, `route`, `frequency`, `step`, `quantity`, `dispenseStatus`,
  `duration`, `startDate`, `endDate`, `dispensary` and `prescribingDoctor`.
  Nothing else in the Bundle is read, and any other field of a record is never
  bound.
- **The page.** Read by ADR 0017's rules and sent as `_offset`
  (`startIndex - 1`) and `_count`; `totalResults` is the source's `total`, and
  the source is asked even for `count=0` or a page past the end (ADR 0018, D3).
  The SPA asks for 5. `SourceSearchset` reads it, as it reads an FFI History's,
  and `ReceivedFields` each field, a date-time with an offset for each date.
- **Only a `404` is no patient**, our API's `404` too. Every other status, and
  a `200` that is no searchset of Medication Records, is a failure of the
  source, our `502` ([Failures](#failures)); what a record's fields hold never
  is.
- **No caching.** No Medication Record is kept in Redis or in memory: every
  read, of the same page too, is a call to the source.

## Sort and filters

The patient source sorts, filters, pages and counts a Medication History; the
service checks the sort and the filters, passes them through and does none of
it itself, as for an FFI, a NUMC or an ARMC History ([ffi](ffi.md), "Sort and
filters"; [armc](armc.md), "Sort and filters"; ADR 0019, D4 and D7). Its two
date columns are declared on the shared sort and range as **date-times**
(`MedicationHistory.DATE_COLUMNS`, `MEDICATION_DATE_COLUMNS`; [ffi](ffi.md),
"Sort and filters", the precisions).

- **The sort.** One column at a time, by `sortBy` — `startDate` or `endDate`
  — and `sortOrder` — `ascending` (the default with a `sortBy`) or
  `descending`. It orders by the whole date-time, to the minute, not the day
  alone, and is sent as `_sort` on the source's own search parameters,
  `start-date` or `end-date`, `-` first for descending, the column alone:
  `_sort=-start-date`. **The source breaks ties**: no `_id` tie-break is sent.
  **With no `sortBy`** — whatever `sortOrder` says — **no `_sort` is sent**,
  and the source's default order is the order shown. The domain's
  `HistorySort` holds it, as for the other histories.
- **The filters** narrow the history, combined with AND, each optional
  (`MedicationFilter`):

  | Column          | Our API                        | Sent to the source                                         |
  | --------------- | ------------------------------ | ---------------------------------------------------------- |
  | Dispense Status | `dispenseStatus`, repeated     | `dispense-status=<a>,<b>`                                  |
  | Start Date      | `startDateFrom`, `startDateTo` | `start-date=ge<from>:00Z`, `start-date=lt<to + 1 min>:00Z` |
  | End Date        | `endDateFrom`, `endDateTo`     | `end-date=ge<from>:00Z`, `end-date=lt<to + 1 min>:00Z`     |

  The Dispense Statuses are any of their codes: they reach the source as one
  parameter, joined by commas — FHIR's OR — in their [closed
  set](#the-closed-sets)'s own order, each matched exactly; none chosen is no
  filter. Each end of a date-time range is a minute in Singapore time,
  `YYYY-MM-DDTHH:mm` with no offset, optional and **inclusive**: it is sent as
  the instant it names in UTC, `ge` the from-minute's first instant and `lt`
  the first instant after the to-minute, so a record starting at 16:20 is in a
  range to 16:20, and in one from 16:20. A from-end may not be after its
  to-end (`DateRange`); a range of one minute is a from-end equal to its
  to-end. There is no filter on the Dispensary or on any free-text element: a
  parameter naming one, or another history's — `outcome`, `issueDateFrom`,
  `status` — is no parameter the read takes, and is not passed on.

- **Checked as the request is read in.** A column or direction spelled any
  other way, a Dispense Status outside its closed set — a label, another
  case, a Java constant's name, or another set's code — an end that is no
  date-time to the minute — a date alone, seconds, an offset, a day or minute
  the calendar or the clock has not — and a from-end after its to-end are each
  `400`, before the source is called. A record may carry a code no filter can
  name ([Shown as received](#shown-as-received)).

## Our API

`GET /api/patient-medication/{patientId}?startIndex=…&count=…`, with any
filters and sort, outside the
`/api/patient` FHIR namespace: plain JSON, `{totalResults, startIndex,
records: [{id, medication, dosage, route, frequency, step, quantity,
dispenseStatus, duration, startDate, endDate, dispensary, prescribingDoctor,
invalid}]}`, each code as the source sent it, known to its set or not, the free
text as the source gave it, and both date-times ISO-8601 with the offset the
source gave. Every field is always there: one missing or in the wrong format is
`null`, and `invalid` names, in the row's order, those whose format was wrong —
a `null` it does not name is missing. `totalResults` counts the Medication
Records the filters select. Errors are the application API's `ApiError`:

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
`event.reason`: a failure of the call or of the searchset, the one every paged
read of the source shares. **No Medication Record is a failure**, whatever its
fields hold ([Shown as received](#shown-as-received)), so no reason names
Medication.

| Failure                                                     | `event.reason`      | `error.category` |
| ----------------------------------------------------------- | ------------------- | ---------------- |
| No connection, or one refused, reset or dropped             | `unreachable`       | `network`        |
| No connection within 2 seconds, or no whole answer within 5 | `timed-out`         | `network`        |
| A `5xx`                                                     | `server-error`      | `server`         |
| Any other status but a `200` or a `404`                     | `unexpected-status` | `data`           |
| A body that arrived whole and is not JSON or not the shape  | `unreadable`        | `data`           |
| A `200` that is no searchset `Bundle`                       | `not-a-searchset`   | `data`           |
| A searchset with no `total`, or a negative one              | `no-total`          | `data`           |
| An entry with no resource, or one that is no `Medication`   | `another-resource`  | `data`           |
| An entry whose resource is no object                        | `unreadable`        | `data`           |

The adapter raises each as a `PatientSourceException`, which the app-wide
handler answers `502` with the generic `server-error` `ApiError`, as for an FFI
History.

## Logging

As an FFI History's ([ffi](ffi.md), "Logging"): each failure of the source is
exactly one `ERROR`, the `502`'s `http.request.fault` record naming its reason;
neither the log nor the exception's message names the patient id or anything of
the source's body — not a record's id, text, code or date, nor an error
answer's body; and the request record names the route template
(`/api/patient-medication/{patientId}`), never the id.

**A response holding fields in the wrong format is one `WARN`** beside the
request record, `patient_source.invalid_fields` (`observability.InvalidSourceFields`,
ADR 0020): its `app.patient_source.read` is `medication`, and its
`app.patient_source.invalid_fields` lists each such field by its path in the
source's answer, the entry counted from zero — `entry[1].startDate` — never its
value, a record's id or the patient. The `patient_source.invalid_fields` counter
counts each such field, tagged `read=medication` and `field`. A response with
none, a field missing included, writes and counts nothing.
`PatientHistoriesIntegrationTests` holds each Medication fault stub's read to
exactly one `ERROR` naming its failure under its category, Tan Boon Huat's read
to exactly one `WARN` naming its invalid fields' paths, and the stream, for
those reads and for successful ones, to naming neither the patient id nor any
record's id, Medication, dates or Prescribing Doctor.

## Not audited

A Medication History read writes **no audit row**, sorted and filtered too, as
an FFI History read and a read of one Patient by id write none (ADR 0019, D3
and D7), and the log names none of a filter's values. A refused read is
audited as `ACCESS_DENIED`, as every authorization refusal is.

## The Medication page

`/medication`, titled **Medication**, linked **Medication** in the sidebar
directly above FFI — Search, Demographic, Medication, FFI, NUMC, … — with the
`Pill` icon, for a session holding `patient:read`, and guarded by it as FFI is:
a session without it is offered no link and is routed to its landing page.

- **It reads the patient in context** as the FFI page does, from the signed-in
  layout's one reader of it ([patient](patient.md), "The patient in context"),
  through the same shared wrapper: it reads that patient's Medication History
  once the patient is read, says "Loading records…" while the patient or the
  first page is read, and a failed read of the patient leaves
  it empty. It is read-only, five Medication Records a page, with no row
  action.
- **The table**: a leading column of warnings, one on each row whose
  [Mandatory element](#mandatory-elements) is missing or invalid, then
  Medication, Dosage, Route, Frequency, Step, Quantity, Dispense
  Status, Duration, Start Date, End Date, Dispensary, Prescribing Doctor — the
  Medication in bold, the free text as the source gave it, the Dispense Status
  as its badge ([the elements](#the-elements)), both date-times in Singapore
  time as `dd MMM yyyy HH:mm`, and the Dispensary by its Medical Centre's
  label; a code outside its set as it came, the Dispense Status's on a neutral
  badge; a missing element "—" and one in the wrong format "Error", the rest
  of the row as any other ([Shown as received](#shown-as-received)). The
  table is wider than its card: each
  cell keeps to one line and the table scrolls sideways inside the card rather
  than squeezing its columns.
- **The shared paged listing**: the numbered pager and "Showing x to y of N
  records", named on a single page too, the page shown held while the next
  loads.
- **Sort.** As on the other history pages ([ffi](ffi.md), "The FFI page"):
  only the Start Date and End Date headings sort, one at a time, each the
  shared sortable column heading stating its direction with `aria-sort`, by
  the whole date-time; every other heading is plain. The history opens in the
  source's own order, which neither heading shows as sorted. The first press
  on a heading sorts it ascending, and each later press toggles it descending
  and ascending, never back to the source's order; another heading replaces
  the sort, starting ascending. The source breaks ties. Both headings are
  disabled while a read is pending.
- **Filter.** A Filter button at the top right of the Medication History
  opens a panel of the three filters, the button counting those applied
  ("Filter, 2 set", a badge of 2 — the Dispense Statuses chosen as one, and
  each end of a range one each): a Dispense Status multi-select, listing
  PENDING, DISPENSED and UNCOLLECTED in that order, as the NUMC page's closed
  sets are (`MultiSelectPicker`), none chosen being no filter; and a from–to
  range of `<input type="datetime-local">` for Start Date and for End Date,
  each end a Singapore minute, optional and inclusive, and holding no value
  until a time is chosen beside the date. Nothing in the panel is sent until
  Apply, or Enter in a date-time; Reset clears every filter and applies none.
  A from-end after its to-end is refused beneath its range — "The End Date
  from-date must not be after its to-date." — the panel stays open and nothing
  is read. The panel is the other histories' (`HistoryFilterPopover`), with
  the Medication History's own fields.
- **Filters and sort read from the first page.** Applying filters, or any
  change of sort, reads the history from its first page, the filters combined
  with AND and with the current sort. Both live on the page alone: neither
  reaches the URL or the browser history, and a new patient in context starts
  with neither.
- **No patient in context** — the tab holds none, or the source holds no
  patient under its id (its `404`, our `404`) — shows "No patient in context",
  as the other history pages do, and reads no Medication Records or reports
  nothing.
- **A patient with no Medication Records** — a `200` with `total: 0` — shows
  "No records found." A filter matching none of a patient's Medication
  Records shows "No records match the filters."
- **The source failing** shows "Couldn't load medication records" on the page's
  error line, and Try again, which reads the same page again.

## The patient source's Medication fixtures

The patient source's mock (`backend/patient-source-mock`, [patient](patient.md),
"The patient source's fixtures") serves the Medication Histories of the
development fixtures beside their FFI, NUMC and ARMC Histories, on the same
patients, and the backend's tests read the same stub files
(`PatientSourceStubs`).

- **Tan Wei Ming** (`eq081-VQEgP8drUUqCWzHfw3`) has six Medication Records over
  two pages (`medication-tan-wei-ming-page-1`, `-page-2`). The first five, none
  with a Step:

  | Medication  | Dosage | Route | Frequency | Quantity   | Dispense Status | Duration | Start Date        | End Date          | Dispensary       | Prescribing Doctor         |
  | ----------- | ------ | ----- | --------- | ---------- | --------------- | -------- | ----------------- | ----------------- | ---------------- | -------------------------- |
  | Paracetamol | 750 mg | PO    | QDS       | 30 tablets | PENDING         | 5 days   | 18 Oct 2026 17:30 | 22 Oct 2026 17:30 | Nee Soon Camp MC | Dr Lim Wei Jie             |
  | Amoxicillin | 300 mg | PO    | TDS       | 20 tablets | PENDING         | 5 days   | 18 Oct 2026 17:31 | 22 Oct 2026 17:31 | Khatib Camp MC   | Dr Lim Wei Jie             |
  | Paracetamol | 750 mg | PO    | QDS       | 30 tablets | PENDING         | 5 days   | 18 Oct 2026 17:30 | 22 Oct 2026 17:30 | Nee Soon Camp MC | Dr Chen Mei Ling           |
  | Paracetamol | 500 mg | PO    | QDS       | 20 tablets | DISPENSED       | 3 days   | 13 Oct 2026 14:30 | 15 Oct 2026 14:30 | Khatib Camp MC   | Dr Rajesh Kumar            |
  | Amoxicillin | 300 mg | PO    | TDS       | 20 tablets | UNCOLLECTED     | 3 days   | 22 Apr 2025 10:00 | 24 Apr 2025 10:00 | Kranji Camp MC   | Dr Nur Aisyah binte Rahman |

  The sixth, on the second page, is Prednisolone 30 mg PO OM, Step 1, 14
  tablets, DISPENSED, 7 days, 02 Dec 2024 09:15–09 Dec 2024 09:15, Hendon Camp
  MC, Dr Chen Mei Ling.

- **Tan Hui Min** (`dev-patient-05`) has four Medication Records on one page
  (`medication-tan-hui-min`), between them every Dispense Status and some
  elements left out: the first's dates are given in UTC (`Z`) and shown
  in Singapore time, the second lacks its Dosage, Frequency, Step and Duration,
  and the fourth has no Dosage, Route, Frequency, Step, Quantity or Duration
  and ends the minute it starts. The two histories hold every Medical Centre between them as their
  Dispensaries.
- **Tan Boon Huat** (`dev-patient-10`) has four Medication Records, on one
  page, shown as received (`medication-tan-boon-huat`): a Paracetamol record
  whose Dosage is `null`, Route empty and Quantity a number, its Dispense
  Status `on-hold` and Dispensary `tengah-air-base` outside their sets, and
  its End Date two days before its Start Date; an Ibuprofen record whose Start
  Date has no offset, End Date is a number and Prescribing Doctor is blank;
  one whose id is outside FHIR's grammar, whose Medication is an object, its
  Dispense Status `null`, its End Date a date alone and its Dispensary empty;
  and an Oral rehydration salts one with no id, a blank Start Date, a `null`
  End Date and its Dosage, Route, Frequency, Step, Quantity, Duration,
  Dispensary and Prescribing Doctor left out:

  | Medication             | Dosage | Route | Frequency | Step | Quantity   | Dispense Status | Duration | Start Date        | End Date          | Dispensary      | Prescribing Doctor |
  | ---------------------- | ------ | ----- | --------- | ---- | ---------- | --------------- | -------- | ----------------- | ----------------- | --------------- | ------------------ |
  | Paracetamol            | —      | —     | —         | —    | Error      | on-hold         | 3 days   | 16 Mar 2022 09:00 | 14 Mar 2022 09:00 | tengah-air-base | Dr Lim Wei Jie     |
  | Ibuprofen              | 400 mg | PO    | TDS       | —    | 15 tablets | DISPENSED       | 5 days   | Error             | Error             | Kranji Camp MC  | —                  |
  | Error                  | —      | —     | —         | —    | —          | —               | —        | 16 Mar 2020 09:00 | Error             | —               | Dr Chen Mei Ling   |
  | Oral rehydration salts | —      | —     | —         | —    | —          | PENDING         | —        | —                 | —                 | —               | —                  |

  The third, its Medication invalid and its Dispense Status missing, is the
  one row carrying a warning, naming the Medication and the Dispense Status.

- **Every other fixture** has none — Siti Nurhaliza binte Ahmad
  (`erXuFYUfucBZaryVksYEcMg3`) is the one the tests read — answered by
  `medication-fallback`, which also answers any page of a fixture's history
  the canned stubs do not name, with an empty searchset, `total: 0`.
- **Any other well-formed id** is the source's `404` (`medication-not-found`).

Each canned page is matched on the whole query the backend sends — `_count=5`,
`_offset`, `_total=accurate`, the `_sort`, and each filter pinned to its exact
value or, where the read uses none, to absent — so a filtered or sorted read
never falls to an unfiltered page, and a read of any other page, filter or
sort falls to the fallback. Each fixture is selectable on Patient Search (the
name search `tan` finds the three Tans, Tan Boon Huat on its second page).

**Sort and filter stubs.** Beside the pages in the source's order:

- **The sorts**, of Tan Wei Ming's six Medication Records, the first page of
  five, `total: 6`: `medication-tan-wei-ming-by-start-date`
  (`_sort=start-date`, earliest first: the sixth, fifth and fourth, then the
  first and third, which start on the same minute, ahead of the second, a
  minute later), `-by-start-date-descending` (`-start-date`: the second, then
  the first and third, the fourth and the fifth), and `-by-end-date` and
  `-by-end-date-descending` (`end-date`, the same two orders). Each tie keeps
  the source's own order.
- **The filters**, of Tan Hui Min's four, in the source's order:
  `medication-tan-hui-min-pending`, `-dispensed` and `-uncollected`
  (`dispense-status`, one code each, `total` 1, 2 and 1);
  `-pending-or-uncollected` (`dispense-status=pending,uncollected`,
  `total: 2`); `-started-to-14-jul-2025-16-20`
  (`startDateTo=2025-07-14T16:20`, sent as
  `start-date=lt2025-07-14T08:21:00Z`, `total: 3`, her second record, which
  starts on that minute, among them); `-started-from-1-mar-2026-10-00`
  (`startDateFrom=2026-03-01T10:00`, `start-date=ge2026-03-01T02:00:00Z`, her
  first record alone, which starts on that minute and is given in UTC);
  `-ended-2025` (`endDateFrom=2025-01-01T00:00` and
  `endDateTo=2025-12-31T23:59`, `total: 2`); `-dispensed-started-from-2025`
  (DISPENSED and `startDateFrom=2025-01-01T00:00`, two filters combined,
  `total: 1`); and `-ended-to-2023` (`endDateTo=2023-12-31T23:59`, which no
  Medication Record matches, `total: 0`).

`PatientSourceFixturesTests` holds each sorted or filtered stub to the
Medication Records its filters select from the history in the source's order,
each date-time compared as an instant, in the order its sort names, ties as
the source keeps them, and the stubs to covering each direction of each
date-time, every filter and each Dispense Status, a from-bound and a to-bound
each on a record's exact minute and keeping it, filters combined and a filter
matching none.

**Fault stubs.** Beside them, each failure above that the mock can serve has a
stub of its own under a well-formed patient id no fixture uses,
`fault-medication-<failure>`, marked with `metadata.fault`: `unreachable` (the
connection reset), `slow` (the answer after 7 seconds), `server-error`
(`503`), `unexpected-status` (`400`), `not-a-searchset` and `no-total` — the
failures every history shares. A Medication Record's data is no fault: what
its fields may hold is Tan Boon Huat's page. A fault stub is no fixture.
`PatientSourceFixturesTests` holds the fixtures to these rules, Tan Boon
Huat's written out, `PatientStoreContract` holds what each history reads,
written out, `PatientSourceHistoriesAdapterTests` each fault stub's failure,
and `PatientSourceMedicationAdapterTests` how each field is read.
