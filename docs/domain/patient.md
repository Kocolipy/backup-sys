---
type: Domain Rules
title: Patients
description: The FHIR R5 Patient the service serves — its element rules, each of its four elements and its Demographic details shown as received, the NRIC system, the authoritative name text, derived age, the in-house patient source it is read from and the elements copied, no caching, Patient search shown as received with its Last visit, a match without a FHIR id dropped and counted, the data classification, the error contract, the logging rules, the absence of auditing for reads by id and the audit of every search, the follow-ups before real data, the Patient card's patient in context, the Search page, the development fixtures, received stubs, search stubs and fault stubs in the source's mock, and how R5 conformance is proven.
tags: [patient, fhir]
---

# Patients

**Patient** — the person care is about, as this service reads them. The backend's
`patient` module reads each from the **in-house patient source** on every read and
serves it, read-only, as a FHIR **R5** `Patient` resource at
`GET /api/patient/{id}`, to a session holding `patient:read`: a delegatable
Permission a User holds only through a Group that carries it, or as the
Superuser ([authorization](authorization.md)). A User in no Group reads no
Patient and runs no Patient search. The
resource is rendered from the domain's Patient, copied from the source's R4 one;
nothing stores it. Why R5, and why the in-house source, read through, is ADR
0014; the wire contract is `backend/docs/openapi.yaml`. The SPA never reaches
the source: SPA → `GET /api/patient/{id}` → backend → `GET {base}/Patient/{id}`.
Patients are also found by a **Patient search**, by name or full NRIC, which is
the source's own search passed through (ADR 0018, ["Patient search"](#patient-search)).

## The resource

Base R5 `Patient`, with no `meta` and so no `meta.profile`, and no element beyond
these, the [Demographic details](#demographic-details) and the
[invalid-field extensions](#shown-as-received), each left out when the patient
has none:

```json
{
  "resourceType": "Patient",
  "id": "eq081-VQEgP8drUUqCWzHfw3",
  "identifier": [
    {
      "system": "https://fhir.synapxe.sg/NamingSystem/nric",
      "value": "S1234567A"
    }
  ],
  "name": [
    {
      "use": "official",
      "text": "Tan Wei Ming",
      "family": "Tan",
      "given": ["Wei", "Ming"]
    }
  ],
  "gender": "male",
  "birthDate": "1985-03-12"
}
```

- **`id`** — the Patient's **EHR's FHIR id**, its id at the patient source and its
  FHIR resource id here, so the patient the EHR context names is read by that id
  directly, unchanged: there is no mapping between ids. The service assigns no id
  of its own. Any value in FHIR's id grammar, `[A-Za-z0-9\-.]{1,64}` (letters,
  digits, `-` and `.`, 1 to 64 of them), is well-formed; anything else is refused
  `400` before the source is called. The grammar lives in the domain's
  `PatientId`, which the read binds its path segment through.
- **`identifier`** — at most one, the NRIC, under Synapxe's NRIC NamingSystem,
  `https://fhir.synapxe.sg/NamingSystem/nric` (`Patient.NRIC_SYSTEM`). Its shape
  is not checked.
- **`name`** — at most one `HumanName`, `use: official`.
  - **`text` is authoritative**: it is the name as the person writes it, and it
    is what the SPA shows. It is never rebuilt from `family` and `given`.
  - `family` and `given` are optional, and are left out rather than sent empty when
    the name is not split. Many Singapore names do not split cleanly into a family
    and given names, so nothing may assume the split exists or rebuild the name
    from it.
- **`gender`** — FHIR's administrative gender, one of `male`, `female`, `other`
  or `unknown`, or any other code the source sent, as it came.
- **`birthDate`** — to the day.

The path follows the repo's convention: an unversioned `/api` base, held in code.

### Shown as received

The patient source is the truth (ADR 0020): the NRIC, the official name's
`text`, `gender` and `birthDate` are each **present**, **missing** — a key not
there, `null`, or text empty or only whitespace — or **in the wrong format** —
the wrong JSON type, or a birth date that is no calendar date — and none fails
the read. Nothing of a value's meaning is checked: a birth date in the future is
present.

- **Missing**: the element is left out — FHIR JSON has no `null`. No NRIC is no
  `identifier`; no official name is no `name`; an official name with no `text`
  keeps its `use`, `family` and `given`.
- **In the wrong format**: the element is left out too, and an extension names
  it by its path in the resource — `identifier[0].value`, `name[0].text`,
  `gender` or `birthDate` — under the service's own URL,
  `http://example.org/fhir/StructureDefinition/invalid-field` (a placeholder, as
  the military service's is), its path the `valueString`. An invalid NRIC keeps
  its identifier's `system`. The extensions follow the military service, in
  that order. An element left out that no extension names is missing.
- **A code outside the four** genders is present, and served as its code.

So a Patient shown as received stays base R5 — the names travel in base R5's
own extension mechanism — but for a gender outside the four, which base R5's
required value set does not admit (ADR 0014).

The SPA shows each as its value, **"—"** when missing, or **"Error"** when in the
wrong format, on the Patient card and in the top bar, a gender outside the four
as its code; Age follows the date of birth, "—" or "Error" as it is.

### Demographic details

A read by id also serves what the Demographic page shows of the patient, each
element only when the patient has it, never empty. A search serves none of it.
Each detail is [shown as received](#shown-as-received), independently of the
others: present, missing or in the wrong format — the wrong JSON type, or a
service date that is no calendar date — and none fails the read.

- **`extension`** — the **military service**, under
  `http://example.org/fhir/StructureDefinition/military-service`, a placeholder
  URL until the in-house source names its own, first among the extensions: a
  `serviceRecord` sub-extension per service record, in the source's order, each
  holding whichever of `serviceType`, `rank`, `unit`, `service` and `vocation`
  (`valueString`) it has present, then whichever of `enlistmentDate`,
  `ordDate`, `mrDate` and `rodDate` (`valueDate`, to the day) the patient has
  present. A field or a date missing or in the wrong format is left out: base
  R5 admits no extension with neither a value nor extensions of its own. One in
  the wrong format is named by the url it would be served under —
  `extension[0].extension[1].extension('rank').valueString` for the second
  record's rank, `extension[0].extension('ordDate').valueDate` — since one left
  out has no place of its own.
- **`telecom`** — the phone (`system: phone`), then the email
  (`system: email`), each its `system` alone when its value was in the wrong
  format (`telecom[0].value`, and the email `telecom[1].value` after a phone).
- **`address`** — the residential address, `use: home`, as its `text` alone,
  or its `use` alone when the text was in the wrong format (`address[0].text`).
- **`contact`** — the **next of kin**: a relationship coded v2-0131 `N`
  (`http://terminology.hl7.org/CodeSystem/v2-0131`), then their relationship as
  text (`Mother`) when present (`contact[0].relationship[1].text`); their name
  as `text` when present (`contact[0].name.text`); their phone when the source
  sent one, its `system` alone when in the wrong format
  (`contact[0].telecom[0].value`). A next of kin with neither a name nor a phone
  is their coding alone, and a service record with no field present is its url
  alone: base R5's invariants on a contact and an extension admit neither, the
  only other ways besides a gender outside the four that a Patient shown as
  received leaves base R5.

The Demographic page shows each detail as its value, **"—"** when missing, or
**"Error"** when in the wrong format; a patient with no contact coded as next of
kin shows "No next of kin recorded", and one with no military service every
service record slot and date as "—".

## The in-house patient source

The backend reads every Patient from the in-house patient source through the
HTTP adapter behind the domain's `PatientStore` (`PatientSourceAdapter`, ADR
0014). The source contract implemented here is **FHIR R4** at
`GET {base}/Patient/{id}`, with **no request authorisation**: the call presents
no credential. `APP_PATIENT_SOURCE_BASE_URL` is the base, required, with no
default (`backend/docs/configuration/patient-source.md`).

- **The elements copied.** From the R4 `Patient`, the adapter copies exactly the
  value of the first `identifier` under the NRIC NamingSystem, the first `name`
  whose `use` is `official`, `gender` and `birthDate`, and ignores every other
  element — another identifier, a `usual` or `maiden` name, `meta` and the rest
  are never read. R4 and R5 agree on all four, so the copy is straight and the R5
  response has exactly the shape above. The NRIC's value, the official name's
  `text`, `gender` and `birthDate` are each bound as the raw JSON they arrived as
  — so a number is not coerced into text — and read [as received](#shown-as-received).
- **The Demographic details copied**, by a read by id alone, each value bound
  as the raw JSON it arrived as and read [as received](#shown-as-received): the
  `value` of the first `telecom` with `system: phone`, and of the first with
  `system: email`; the `text` of the first `address` with `use: home`, never
  rebuilt from its lines; the first `contact` with a relationship coded v2-0131
  `N` — its name's `text`, the first relationship `text` that is not blank, and
  its first phone's `value`; and the military service extension, each service
  record's field and each date the first sub-extension under its url, a
  sub-extension it does not name left unread. A detail with no element to read
  it from is missing; none is ever a failure of the source.
- **Only a `404` is no patient**, and stays the `404` below. Every other answer
  is a failure of the source, the `502` below: the source unreachable, no
  answer within 2 seconds to connect and 5 to answer — the whole answer, its
  body included — a connection lost before the answer was in, a `5xx` or any other
  status, or a `200` the service cannot hold — a body that is no Patient, or a
  Patient under another patient's `id`. **Another patient's id stays a failure**: the Patient is read by
  the id the EHR context names, and so are its FFI, NUMC, ARMC and Medication
  Histories, so a Patient under another id is not the patient asked for. What
  the NRIC, the name, the gender, the birth date or a Demographic detail hold
  is [never a failure](#shown-as-received).
- **No caching.** No Patient is kept in Redis or in memory: every read, of the
  same patient too, is a call to the source.
- **No grants.** No table holds a Patient, so no database login holds a grant
  on patient data.

## Patient search

A signed-in User finds Patients by typing a name or a full NRIC into the Search
page's one box, and may narrow the results by gender.
`GET /api/patient-search?q=…&startIndex=…&count=…`, with any gender filter,
passes the search to the patient source, which matches, filters, orders, pages
and counts it (ADR 0018); the service filters, orders and pages nothing of its
own, and holds and caches nothing.

- **One box, two searches.** The query is trimmed. A full NRIC — `S`, `T`, `F`,
  `G` or `M`, seven digits and a letter, in either case — is an exact NRIC
  search, sent upper-cased as `identifier=<NRIC NamingSystem>|<NRIC>`. Anything
  else is a name search, `name:contains=<query>`, matched as the source decides:
  it may match a usual or maiden name, and the match is still shown by its
  official name. A name search needs **at least 3 characters**. There is no
  partial NRIC search — `S851` is a name search — and nothing lists every
  Patient. The domain's `PatientQuery` decides the kind; `q` binds through it, so
  a query too short, or none, is `400` before the source is called.
- **The source's page.** `startIndex` and `count` are read by ADR 0017's rules
  (`PageParameter`, `PageRequest`: coerced, never refused; a non-integer is
  `400`), and sent as `_offset` (`startIndex - 1`) and `_count`, with
  `_total=accurate`. `totalResults` is the source's `total`. The SPA asks for 5.
- **The source's order.** No `_sort` is sent: the in-house patient source
  answers every search in a stable order of its own, so the pages of one
  search are consistent, and the results appear in that order.
- **The filter** narrows the query it is sent with, is optional, and the source
  applies it (`PatientFilter`): `gender`, `male` or `female`, sent as `gender`.
  A Patient whose gender is `other` or `unknown` is found only with no gender
  filter.
- **Checked as the request is read in.** A gender spelled any other way is
  `400`, before the source is called. Any other parameter is ignored.
- **The answer** is plain JSON, `{totalResults, startIndex, droppedResults,
patients: [{id, name, nric, gender, birthDate, lastVisit, invalid}]}`: the
  Patient's id (the EHR's FHIR id, which `GET /api/patient/{id}` reads it by),
  the official name's text, the NRIC, the gender code, and both dates as
  `YYYY-MM-DD`.
- **Shown as received** (ADR 0020, ["Shown as received"](#shown-as-received)).
  Each match's NRIC, official name's text, gender and birth date are copied as a
  read by id copies them, and its Last visit as ["Last visit"](#last-visit)
  has it: each is `null` when missing or in the wrong format, and `invalid`
  names the second kind, in the row's order (`name`, `nric`, `gender`,
  `birthDate`, `lastVisit`). A gender outside the four codes is its raw code.
  Nothing a match holds fails the search.
- **The id is validated.** A row is selected by its Patient's id, so a match
  is shown only under one in FHIR's id grammar, `[A-Za-z0-9\-.]{1,64}`, sent as
  text (ADR 0020, D1). A match with no `id`, one of another JSON type or one
  outside the grammar is dropped from `patients` and counted in
  `droppedResults`; `totalResults` stays the source's `total`. Nothing else of
  a dropped match is answered.
- **An entry that is no Patient** — another resource, or one naming no type —
  is a row whose `id` and every other field is `null`, each but the id named in
  `invalid`: a row of "Error" cells that selects nothing.
- **Reported, never a value.** A page that dropped a match, held an entry that
  is no Patient or a field in the wrong format writes one `WARN`
  `patient_source.invalid_fields`, its read `search`, naming each by its path in
  the source's answer — a dropped match's `entry[1].id`, an entry that is no
  Patient's `entry[2].resourceType`, a field's `entry[0].identifier[1].value`,
  `entry[0].name[1].text`, `entry[0].gender`, `entry[0].birthDate` or
  `entry[0].extension[0].valueDate` — and each counts once in the
  `patient_source.invalid_fields` counter, tagged `read` `search` and `field`
  (`id`, `resourceType`, `name`, `nric`, `gender`, `birthDate` or `lastVisit`)
  (`backend/docs/LOG_RECORDS.md`).
- **Errors are the application API's `ApiError`**, not an `OperationOutcome`:
  the search is outside `/api/patient`. `400` for a query too short, a gender
  the search cannot take, or a paging value that is no integer, `401` and
  `403` as everywhere (`patient:read`), and
  `502` for the source failing as a read by id's `502` — no connection, no answer
  within 2 seconds to connect and 5 to answer, any status but a `200` (a `404`
  included) — or answering a `200` that is no searchset Bundle, has no `total`
  or a negative one, or holds an entry with no resource. What a match holds
  never fails it.
- **Audited, unlike a read by id.** Each search the source answers appends one
  `PATIENT_SEARCH` (["Patient reads are not audited"](#patient-reads-are-not-audited)).

### Last visit

**Last visit** is the date of a Patient's latest visit. FHIR's base `Patient`
has no element for it, so the source carries it as an extension on each Patient
in a search's answer, `https://fhir.synapxe.sg/StructureDefinition/last-visit`,
a `valueDate` to the day, absent for a Patient with none. A read by id ignores
it, and the R5 `Patient` does not carry it.

A search shows it as received (ADR 0020), on the rule the histories follow: the
first `last-visit` extension's `valueDate` is present when it is a calendar
date, missing when there is no such extension or its `valueDate` is absent,
`null` or blank, and invalid — "Error" on the Search page — when it is the wrong
JSON type or no date to the day (`2026-10`, a month alone). It never fails the
search; an invalid one is reported as any field of a match is, by its path,
`entry[0].extension[0].valueDate`, under `field` `lastVisit`.

### The Search page

`/search`, the first link in the sidebar for every signed-in User, titled
"Patient Search" with no subtitle. One box, placeholder "Search by name or
NRIC", searched only on submit (Enter or the Search button).

- **Before the first search** it reads nothing and shows only the box: the
  "Search Results" card, its Filter button with it, appears with the first
  search submitted.
- **A query too short** — trimmed, under 3 characters and no full NRIC — shows
  "Enter at least 3 characters to search by name." beside the box and sends
  nothing; the results last read stay.
- **Results**, under the heading "Search Results": a table of Patient Name (the
  official name only), NRIC (in full), Gender (a badge, Male blue and Female
  pink, a code outside the four as itself on the plain badge), Date of Birth
  and Last Visit (`dd MMM yyyy`), each cell shown as received — its value, `—`
  when missing, "Error" in the wrong format — five to a page with the numbered
  pager and "Showing x to y of N records" — shown whenever there is a match, on
  a single page too. Each row keeps the Patient's id, never shown. An entry
  that is no Patient is a row of "Error" cells, with no pointer, that selects
  nothing.
- **Matches not shown.** A page that dropped matches for want of a FHIR id says
  how many beneath the table — "1 record couldn't be shown", "2 records
  couldn't be shown" — and nothing else of them. `N` stays the source's total,
  and the range and the next page count the dropped among the page's records,
  so paging on skips none of the source's.
- **Selecting a result** puts the row's Patient, by that id, into the tab's
  **EHR context** and goes to Home, whose Patient card and top bar show them.
  A click anywhere on the row selects it, and the Patient Name is a button, so
  the keyboard and assistive technology select it too — whatever its fields
  hold, a name missing showing `—` on the button; rows show a hover state
  and a pointer cursor. A selection replaces the patient already in context
  with no confirmation; selecting the patient already in context only goes to
  Home, the context unchanged and the patient not read again. No row marks the
  patient in context. Coming back to Search starts afresh: only the box, with
  no query, page or filter kept.
- **Order.** The results appear in the patient source's own order; no column
  heading sorts.
- **Filter.** A Filter button beside "Search Results" opens a panel of the
  filter, the button counting it when applied ("Filter, 1 set", a badge of 1):
  Gender — All (the default), Male or Female. Nothing in the panel is sent
  until Apply; Reset clears the filter and applies none.
- **The filter narrows the search submitted.** It keeps across a new search.
  Any change of filter reads the results from their first page.
- **No matches** shows "No records match your search.", with no table and no
  range; "Searching records…" is said while a search is read. **The source
  failing** shows "Unable to search for patients. Please try again." on the
  page's error line, and a Retry button that reads the same page
  of the same search again.
- **The search lives on the page alone.** No query, name, NRIC or filter
  reaches the URL or the browser history; leaving the page forgets them.
  Submitting the same query again reads its first page afresh.

## Data classification

Patient data is **Restricted, Sensitive Normal**: the NRIC, the name, the date
of birth and the Demographic details, and the resource or its JSON as a whole. It is shown to a signed-in
user holding `patient:read`, through a Group that carries it or as the Superuser,
and goes nowhere else — not into a log, an error body
or an audit row.

## Errors

**Every answer is `application/fhir+json; fhirVersion=5.0`**, success or error.
Every error body is an `OperationOutcome` with exactly one issue, `severity:
error`, whose `code` is chosen by the status alone and whose `diagnostics` are a
fixed sentence per code:

| Case                               | Status | `code`          | `diagnostics`                       |
| ---------------------------------- | ------ | --------------- | ----------------------------------- |
| Id outside the FHIR id grammar     | `400`  | `invalid`       | The request is not valid.           |
| No session                         | `401`  | `login`         | A signed-in session is required.    |
| Session lacks `patient:read`       | `403`  | `forbidden`     | The request is not permitted.       |
| Well-formed id, the source's `404` | `404`  | `not-found`     | The resource was not found.         |
| `POST`, `PUT`, `PATCH` or `DELETE` | `405`  | `not-supported` | The request is not supported.       |
| Unexpected failure on this side    | `500`  | `exception`     | The request could not be completed. |
| The patient source failing         | `502`  | `exception`     | The request could not be completed. |

An outcome never contains an exception message, SQL, a stack trace or the
submitted id — markup or a line break in the id included. Malformed covers an id
over 64 characters, markup, a line break, an empty segment (`a//b`) and an
encoded slash (`a%2F%2Fb`); the last three never reach the dispatcher, since the
request firewall refuses them, and are answered with the same outcome. An
encoded slash reaches the firewall at all because Tomcat passes `%2F` through,
still encoded, rather than refusing it with its own HTML error page
(`web.EncodedSlashPassthrough`): passed through, it is never decoded into a path
separator, and the firewall refuses it on every path, so path security is
unchanged and only the answer becomes the outcome. A well-formed id the patient
source answers `404` for — `not-a-uuid` is one — is the `404`, and any other
answer of the source the `502` (["The in-house patient source"](#the-in-house-patient-source)). The SPA reads the `404` as "No patient in
context", an ordinary answer rather than a failure; every other error, the
`502` among them, is the Patient card's failed state.

Every layer answers in this one shape, and the backend's `web` module owns it
(`FhirOperationOutcome`), so the `patient` module keeps its one edge (ADR 0014,
"The module's edges"):

- **The app-wide exception handler** answers a handler's failure — the malformed
  id, the unknown one (the controller raises `NoSuchPatientException`, carrying no
  message and no id), the patient source failing (the adapter raises
  `PatientSourceException`, an `observability.UpstreamFaultException`, so that
  `web` answers it with no edge to `patient`) and a fault on this side — and maps it
  to an `error.category`: `data` for the `400` and the `404`; for the `502`,
  in its one `ERROR` fault record, the failure's own, as ADR 0014's table has
  it for an outbound call (through the classifier every outbound call uses) — `network` for no answer (unreachable or timed out, while
  reading the body too) and `server` for the source's `5xx`, neither needing
  follow-up, and `data`, needing follow-up, for another status or a body that
  arrived whole and the service cannot hold; and
  `application` (or the database mapping, for a database failure) for the
  `500`, in its one `ERROR` fault record. The `400` and
  the `404` get no record of the handler's own: under `/api/patient` the handler
  writes no `http.request.refusal` record and puts its classification
  (`error.code`, `error.category` `data`, `error.follow_up_action` `false`) on
  the request record instead (`RequestRefusal`), so the classification exists
  and the request still has one record. Everywhere else a refusal keeps its own
  `WARN` refusal record.
- **The security chain** refuses a session without `patient:read` — by the
  handler's `@PreAuthorize`, or by its backstop, which matches a read (`GET`,
  `HEAD`) only. The `403` is logged at `WARN` through the
  authorization-failure path (`access.denied`, reason `insufficient-permissions`,
  `error.category` `cert/auth`) and audited as an `ACCESS_DENIED`, as every
  authorization refusal is.
- **The dispatcher** answers a write method `405` before any handler runs. The
  chain lets one through for a session holding baseline access, with or without
  `patient:read`, so that the answer
  says what is true — the resource takes no write — rather than `403`.

Every layer asks `web.ErrorNamespaces` which document the request's namespace
answers with, and `/api/patient` is the FHIR namespace: the chain's entry point
and access-denied handler write the outcome themselves, and the firewall's and
the dispatcher's refusals, which are `sendError`s a servlet container would
answer with its error page — not FHIR, and quoting the request's path, so the id
— are written as the outcome by `ErrorDocumentFilter`, the one `sendError`
adapter every namespace shares. A new FHIR resource served beneath
`/api/patient` is answered as FHIR by every layer with no further change.

## Logging

- **A patient is identified in a log only by its id.** The NRIC, the name, the
  `birthDate` and the Demographic details are never logged, and neither is the
  resource or its JSON. Each domain record holding a Demographic detail
  (`Demographics`, `NextOfKin`, `MilitaryHistory` and its `ServiceRecord`)
  prints as its type alone.
- **`Patient.toString()` prints the id alone.** A Java record's generated
  `toString` prints every field, so a Patient printed into a message or an
  exception would carry all three.
- **A read adds no record of its own.** A success, a `400` and a `404` each
  have the request record alone, which names the route template, never the raw
  `{id}`; the `400`'s and the `404`'s carries their `data` classification. A
  `403` adds the authorization refusal's `WARN` record and a `500` or `502` the
  fault's `ERROR` record, neither naming the id.
- **A failure of the patient source is exactly one `ERROR`**, the `502`'s
  `http.request.fault` record, whose `event.reason` names the failure —
  `unreachable` (no connection, or one refused, reset or dropped before the
  answer was in, its body included), `timed-out` (no connection within the
  connect timeout, or no whole answer within the read timeout: headers late,
  or a body that stalled past it), `server-error`, `unexpected-status`,
  `unreadable` (a body that arrived whole and is not JSON or not a Patient) —
  or the rule the Patient broke — `another-resource` — or, for a search, what
  its answer lacked — `not-a-searchset`, `no-total`, `another-resource` (an
  entry with no resource) — and whose error message is the fault's fixed
  sentence. It never names the patient id, the NRIC, the name, the birth date,
  the query searched for or anything of the source's body.
- **A Patient holding elements in the wrong format is one `WARN`**,
  `patient_source.invalid_fields`, written by `observability.InvalidSourceFields`
  (the module writes no record of its own), its read `patient` and each element
  by its path in the source's answer, nested as deep as it was found —
  `identifier[1].value`, `name[2].text`, `gender`, `birthDate`,
  `telecom[3].value`, `contact[1].relationship[2].text`,
  `extension[1].extension[0].extension[2].valueString` — never its value; and
  each counts once in the `patient_source.invalid_fields` counter, tagged
  `read=patient` and `field`: one of the four by its path in the served
  resource (`name[0].text`), a Demographic detail by its name, the same for
  every Patient and service record — `phone`, `email`, `residentialAddress`,
  `nextOfKin.name`, `nextOfKin.relationship`, `nextOfKin.phone`,
  `serviceRecord.<field>` and each date's url (`ordDate`). A Patient with none
  writes nothing. `PatientSourceAdapterContractTests` holds the record and the counter.
  A search's page is reported the same way, its read `search`
  (["Patient search"](#patient-search)), and one with nothing to report writes
  nothing.
- **A failure of the patient source names nothing of the patient.** Its
  exception's message is fixed text, naming at most a status or an element, and
  its cause is redacted to its type (`RedactedFaultException`): an HTTP client's
  message quotes the URL, and so the id, and a parser's quotes the body. An error
  answer's body is never read.
- **The NRIC's name is masked at the logging boundary**, as defence in depth: the
  Semgrep rule `be-log-sensitive-value` refuses any value named `nric` (ADR 0003,
  "Redaction gates").

`PatientErrorContractIntegrationTests` captures the stream of a successful read, a
`400` and a `404` and asserts that none of the patient's NRIC, name or date of
birth, and none of the ids asked for, appear in it; and of each fault stub's
read, that it writes exactly one `ERROR`, the fault record naming its failure
under its category, and that the stream names neither the id nor anything of
the stub's answer. `PatientControllerTests` does the same for a source nothing
answers on, which no stub can serve.

## Patient reads are not audited

A read of one patient by id writes **no audit row**. The audit trail records
security-relevant changes and refusals, not reads; a refused read is audited as
`ACCESS_DENIED`. The deployed stack supplies no real patient source, and a
deployment that adds one must account for this audit boundary.

**A Patient search is the exception** (ADR 0018): it is a bulk read of
Restricted data, the shape enumeration takes. Each search the source answers
appends one `PATIENT_SEARCH`, fail-closed as a SCIM bulk read is (ADR 0004):
the signed-in User as actor, resource type `Patient` with no subject or
resource, the source's `total` — every Patient the search matched, whatever
the answered page dropped — as `resultCount`, and as
`filterShape` the query's kind — `NRIC` or `NAME` — then the gender filter
when it was used, with `?` for its value: `NAME and gender eq ?` for a name
search filtered by gender (`AuditPatientSearchFilter`). It never holds the
query, a name, an NRIC or the filter's value. A row stored with another shape
is kept as stored, and the Audit page shows it verbatim. A search refused with
`400`, or that the source failed, read nothing and records nothing.
`PatientSearchIntegrationTests` holds the row, and that neither the query, a
filter's value nor a match reaches the log stream.

## Source authentication limit

The source call has no request credential. The adapter supports a source that
accepts this contract. A source requiring authentication needs an adapter and
external-configuration change before it can be used; credentials have no
fallback in this repository.

## Age is derived

Age is **never stored**, by the source's adapter or on the wire. Wherever it is shown it is
derived from `birthDate` as **completed years**: a year counts from its birthday
on, and a 29 February birthday counts from 1 March in a common year. The SPA
counts it on today's date **in Asia/Singapore**, read from an injectable clock, so
a test states the day it runs on and the age never depends on the viewer's zone.

## The patient in context

The **Demographic page** (`/demographic`, guarded by `patient:read`, which
selecting a Patient on the Search page opens) shows the patient in context's
**Patient card**, then their Demographic details: the Military Overview,
Contact Details and Next of Kin / Emergency, each detail
[as received](#demographic-details) — "—" when missing, "Error" when in the
wrong format — and nothing beyond the card while no patient is loaded. A
malformed detail never changes what the Patient card, the top bar or a history
page shows. Home shows the
Patient card alone, and the navigation sidebar links it for no session. The
Patient card shows the patient in context, and only that patient: the
patient of the tab's **EHR context**, which selecting a Patient on the Search
page sets. A patient leaves context only when another Patient is selected or
the session ends. With none in context it
shows "No patient in context" and reads nothing, and a `404` for the id in
context shows the same. For a session without `patient:read` it reads nothing
and says "Your account has no access to patients.", and the top bar names no
patient. Home is open to every signed-in session; Patient Search, the Landing
page of a session holding `patient:read`, is guarded by it, so a session
without it is offered no Search link and is routed to Home. The top bar names
the same patient by name and NRIC.
Both, and the FFI, NUMC, ARMC and Medication pages ([ffi](ffi.md), [numc](numc.md), [armc](armc.md), [medication](medication.md)), render from **one reader** of the patient in context, which the signed-in
layout renders (`frontend/src/pages/patient-in-context.ts`): each change
of the patient in context is one `GET /api/patient/{id}` — one read of the
uncached patient source — on Home as on every other signed-in page, and a
failed read is one "Couldn't load patient" on the page's error line — a body
that is no Patient and a Patient under another id among them. The SPA
holds no patient id beyond the EHR context, and the read is not bound to it:
`patient:read` reads any id. The FFI, NUMC, ARMC and Medication pages read
their histories by the EHR context's id, never by one the Patient carries. The
top bar names the patient, and the Patient card shows them,
[as received](#shown-as-received).

## The patient source's fixtures

Patients are **development fixtures** only, and they live in the in-house
patient source's **mock**: WireMock, under `backend/patient-source-mock`, whose
stub files (`mappings/`) answer FHIR R4 `Patient/{id}` for the development fixtures
below, the **fault stubs** beside them, and `404` for any other well-formed
id; and FHIR R4 `Patient?…` for the **search stubs**. Compose runs it beside SQL Server
and Redis (`patient-source`), so `make infra-up` and `make integration-test`
start it; nothing under `infra/` builds or deploys it, so no dummy Patient
reaches a deployed environment. The backend holds no fixture of its own.

- **One set of stub files, read by the mock and by the tests.** The backend's
  patient tests serve the same files with WireMock in process
  (`PatientSourceStubs`), so the mock a local run and E2E read and the fixtures
  the tests assert cannot drift. WireMock is a **test-scope** dependency only:
  the Enforcer's `wiremock-test-scope-only` (`backend/pom.xml`) fails the build
  if it resolves in a scope that reaches the packaged JAR.
- **Received stubs.** A Patient shown as received has no data-fault stubs
  (ADR 0020): beside the fixtures, each `200` Patient whose elements the source
  holds missing or in the wrong format has a stub of its own, marked with
  `metadata.received` and under a well-formed id no fixture uses —
  `received-no-nric`, `received-no-official-name`, `received-no-name-text`,
  `received-no-gender` and `received-no-birth-date` each lack that one
  element, `received-wrong-format` holds its NRIC, its official name's
  text, its gender and its birth date each in the wrong format, its NRIC and
  official name each second in their lists, and `received-demographics` (Lim
  Kok Leong, `S8800010A`) holds his Demographic details missing and malformed,
  each apart from the others: a phone that is a number beside a present email,
  a home address with no `text`, a next of kin with no name text, a relationship
  text that is a number and a present phone, and a military service past the
  `last-visit` extension whose one service record has a `rank` that is a number
  and no `unit`, an `enlistmentDate` to the month, a present `ordDate`, no
  `mrDate` and a `rodDate` that is a number. A received stub is no fixture: every
  reader of the fixtures leaves it out, and it matches no search. Its NRIC,
  where it has one as text, is as dummy as a fixture's, and the tests hold it to
  the same check. `PatientStoreContract` holds what each reads as, written out.
- **The received search.** A search shown as received has no data-fault stubs
  either: `search-received`, marked with `metadata.received`, answers the name
  search `teo`, which names no fixture, with a page of five, `total: 5` — a
  Patient whose NRIC is a number, whose gender is `F` and whose birth date is
  `1971-03`; a Patient with no `id`; an `Observation`; a Patient whose `id`
  holds a space; and a Patient with no NRIC, no official name, no gender, no
  birth date and no Last visit. None of its entries is a fixture, and none
  carries an NRIC as text. `PatientStoreContract` holds what it reads as: two
  matches dropped, a row with no Patient, and two matches shown as received.
- **Fault stubs.** Beside them, each failure a stub can serve has a
  stub of its own, marked with `metadata.fault` and under a well-formed id no
  fixture uses: `fault-slow` answers only after the read timeout,
  `fault-slow-body` sends its headers at once and dribbles its body out past
  the read timeout, and `fault-server-error` answers `503`. A fault
  stub is no fixture: every reader of the fixtures leaves it out. Its NRIC, where it has one, is as
  dummy as a fixture's, and the tests hold it to the same check.
- **Search stubs.** Each documented search but the received one has a canned
  stub answering a searchset Bundle whose entries are the fixtures themselves, exactly as their
  own stubs serve them, in the mock's one stable order — newest Last visit
  first, ties by id, a Patient with none last — matched on the query the
  backend sends, with the `gender` filter pinned to its exact value or, where
  the search uses none, to absent, so a filtered search never falls to an
  unfiltered stub. `backend/docs/configuration/patient-source.md` ("The search
  set") lists each stub and what it answers.

  The FFI Histories of the fixtures, and the FFI fault stubs, are
  [ffi](ffi.md)'s ("The patient source's FFI fixtures"); their NUMC Histories,
  and the NUMC fault stubs, [numc](numc.md)'s ("The patient source's NUMC
  fixtures"); their ARMC Histories, and the ARMC fault stubs, [armc](armc.md)'s
  ("The patient source's ARMC fixtures"); their Medication Histories, and the
  Medication fault stubs, [medication](medication.md)'s ("The patient source's
  Medication fixtures").

  A low-priority fallback, `search-fallback`, answers any other `/Patient`
  search — a gender filter on any other search or page included — with an
  empty searchset, `total: 0`. `PatientSourceFixturesTests` holds each search's
  entries to the fixtures, the received search's to none of them, the `tan` pages to every fixture whose name contains
  it, and each `tan` stub to the fixtures its gender filter selects in the
  mock's order; `PatientStoreContract` holds what each canned search reads,
  written out.

- **A Last visit to the day on every fixture but two.** Each fixture carries the
  `last-visit` extension (["Last visit"](#last-visit)) but Tan Boon Huat, who
  has none; Rajesh Kumar's is `2026-10`, to the month alone, which his NRIC
  search answers as invalid.
- **More than the five elements.** Each stub is an R4 Patient as a real source
  would answer it — an MRN identifier ahead of the NRIC, `usual` and `maiden`
  names beside the official one, `meta`, `active`, `telecom`, `address`,
  `contact`, `extension` — so the tests read the selection, not merely the
  copy.
- **Tan Wei Ming carries every Demographic detail.** His stub holds a mobile
  phone and an email (`telecom`), a `home` address with its `text`, a next of
  kin (a `contact` whose relationship is coded v2-0131 `N`, with the text
  `Mother`, a name and a phone), and his military service: two service records
  and his `enlistmentDate`, `ordDate` and `rodDate`, with no `mrDate`. Every
  other fixture carries none, but Siti Nurhaliza's `home` address has no
  `text`, so her residential address is missing. His copies in the search stubs are the fixture
  itself, though a search serves none of it.
- **Dummy NRICs.** Every fixture's NRIC has the NRIC shape — a prefix letter,
  seven digits and a check letter — but carries a **deliberately wrong check
  letter**, so it cannot be a real person's: Tan Wei Ming's `S1234567A`
  would be `S1234567D` if it were valid. No two fixtures share an NRIC. The check
  letter is the seven digits weighted 2, 7, 6, 5, 4, 3, 2, plus 4 for a `T` or `G`
  prefix, taken mod 11 to index `J Z I H G F E D C B A` (`S`/`T`) or
  `X W U T R Q P N M L K` (`F`/`G`). That algorithm lives in test code only
  (`PatientSourceFixturesTests`), which reads the stub files and asserts that
  every fixture's NRIC **fails** it, so a real-looking value cannot slip in
  later; the service itself never checks an NRIC.

### The fixtures

Twelve dummy Patients. Between them they cover Singapore's name styles, every way
a name splits — family and given, given only, neither — all four gender
codes and a range of dates of birth, so each rendering path the resource has is
exercised by real data; and seven of them have a name containing `tan`, so the
name search `tan` spans two pages of five.

A Patient's id is its EHR's FHIR id, so each of the first four fixtures borrows
the id of one of an EHR vendor's public sandbox test patients, named in the
table. Only the id is borrowed: the name, NRIC, gender and birth date are the
fixture's own. The other eight carry development ids of the mock's own,
`dev-patient-05` to `dev-patient-12`.

| Id (sandbox patient it borrows from)         | `name.text`                  | `family`    | `given`              | Gender    | NRIC (valid letter) | Purpose                                                                                         |
| -------------------------------------------- | ---------------------------- | ----------- | -------------------- | --------- | ------------------- | ----------------------------------------------------------------------------------------------- |
| `eq081-VQEgP8drUUqCWzHfw3` (Derrick Lin)     | `Tan Wei Ming`               | `Tan`       | `Wei`, `Ming`        | `male`    | `S1234567A` (`D`)   | A Chinese name, family name first                                                               |
| `erXuFYUfucBZaryVksYEcMg3` (Camila Lopez)    | `Siti Nurhaliza binte Ahmad` | —           | —                    | `female`  | `S9212345A` (`F`)   | A Malay name: `binte Ahmad` is a patronymic, not a family name, so the name is not split at all |
| `e0w0LEDCYtfckT6N.CkJKCw3` (Warren McGinnis) | `Kumaravel`                  | —           | `Kumaravel`          | `unknown` | `T0312345C` (`B`)   | An Indian single name: one given name and no family name                                        |
| `egqBHVfQlt4Bw3XGXoxVxHg3` (Elijah Davis)    | `Marcus Anthony de Souza`    | `de Souza`  | `Marcus`, `Anthony`  | `other`   | `S7812345B` (`A`)   | A Eurasian name: given names first, a family name of two words                                  |
| `dev-patient-05`                             | `Tan Hui Min`                | `Tan`       | `Hui`, `Min`         | `female`  | `S9012345A` (`I`)   | The name search `tan`'s newest Last visit                                                       |
| `dev-patient-06`                             | `Stanley Goh Kok Wee`        | `Goh`       | `Stanley`, `Kok Wee` | `male`    | `S7212345A` (`Z`)   | Matches `tan` inside a given name                                                               |
| `dev-patient-07`                             | `Lee Mei Ling`               | `Lee`       | `Mei`, `Ling`        | `female`  | `S6812345A` (`C`)   | Matches `tan` by her maiden name, `Tan Mei Ling`, and is shown by her official one              |
| `dev-patient-08`                             | `Nur Intan binte Yusof`      | —           | —                    | `female`  | `T0112345A` (`E`)   | A Malay name matching `tan` inside it                                                           |
| `dev-patient-09`                             | `Kavitha Tanabalan`          | `Tanabalan` | `Kavitha`            | `female`  | `S9512345A` (`G`)   | Matches `tan` at the start of a family name; first on the search's second page                  |
| `dev-patient-10`                             | `Tan Boon Huat`              | `Tan`       | `Boon`, `Huat`       | `male`    | `S5512345A` (`D`)   | The one Patient with no Last visit, last of the search                                          |
| `dev-patient-11`                             | `Ahmad bin Ismail`           | —           | —                    | `male`    | `S8312345B` (`A`)   | Matches no canned search                                                                        |
| `dev-patient-12`                             | `Rajesh Kumar`               | `Kumar`     | `Rajesh`             | `male`    | `S9912345B` (`A`)   | A Last visit not to the day, which his NRIC search shows as "Error"                             |

Their Last visits, in the mock's order: Tan Hui Min `2026-11-02`, Stanley
Goh Kok Wee `2026-10-28`, Tan Wei Ming `2026-10-15`, Lee Mei Ling `2026-09-30`,
Nur Intan binte Yusof `2026-08-19` (the name search's first page), Kavitha
Tanabalan `2026-05-07` and Tan Boon Huat, none (its second); and beside them
Rajesh Kumar `2026-10` (to the month alone), Siti Nurhaliza binte Ahmad `2026-07-11`, Ahmad bin
Ismail `2026-06-14`, Marcus Anthony de Souza `2026-03-22` and Kumaravel
`2025-12-01`.

A part a fixture does not hold is left out of the JSON entirely, never sent as
`null` or empty. `PatientStoreContract` holds each fixture, as the service
reads it, to this table.

## R5 conformance is proven, not assumed

The service writes its FHIR JSON by hand (ADR 0014), so a test holds it against
the specification: `PatientR5ConformanceTests` reads every fixture the stub files
serve through `GET /api/patient/{id}` and the HTTP adapter, and every received
stub's, and validates the response body exactly as
written with **HAPI FHIR's R5 validator** against base R5 `Patient`: an element
missing or in the wrong format is left out, and the invalid-field extensions are
extensions base R5 admits. A gender outside the four codes, which base R5's
required value set refuses, is held by `FhirPatientTests` and no stub. It does the
same for the `404`, `400` and `502` bodies against base R5 `OperationOutcome`, answered
by the app-wide exception handler as in the running service. Any validation message
of severity error or fatal fails the test; a warning, such as a best-practice
rule, does not. The same class checks that the
validator rejects a Patient that is not base R5, so a pass means something.

HAPI is a **test-scope dependency only**. The production code carries no FHIR
library, and the backend build's Enforcer (`hapi-test-scope-only` in
`backend/pom.xml`) fails the build if any `ca.uhn.hapi.fhir` artifact, direct or
transitive, resolves in a scope that reaches the packaged JAR.
