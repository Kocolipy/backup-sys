---
type: Architecture Reference
title: Frontend pages
description: src/pages/ file by file — each page and the API module beside it, the parts the pages share (the paged read, the listing body, the pager, the filter parts, the patient in context), and why each sits where it does.
tags: [architecture, react, pages]
---

# The pages

`src/pages/`, file by file: what each owns and why it sits where it does. What
a page shows and does, rule by rule, is its area's file in `/docs/domain/`;
this file holds the structure.

Pages consume `auth/`; they hold no session or Permission logic and never
decide where a visitor goes. A page's folder never imports another's: a part
two pages share moves up to `src/pages/` itself, and imports no page folder
(`mb-pages-do-not-import-another-page`, `mb-shared-page-parts-do-not-import-a-page`).
Each page's `*-api.ts` is data alone — its endpoints' declarations, wire types
and decoders — and imports no hook (`lb-api-modules-are-data`).

## Accounts and Audit

- `accounts/accounts.tsx` — the Users and Groups projections, each read a page
  at a time with a search box and the shared pager; Unlock of a locked User by
  stable id; a Group's members a page at a time behind its member count (only
  for a session holding `group:read` and `user:read`, the one read declared
  `{ allOf }`); and — for the Superuser alone, on every Group but the Admin
  group — "Edit permissions". Nothing the directory owns has a control.
  - **An action replaces its row** on the page shown from its response, which
    _is_ that User's or Group's new state, rather than re-reading.
  - **The page hides the actions the backend would refuse**: its own row (the
    one whose stable id the session's `AuthUser.id` holds), Unlock on a User
    that is not locked — the Bootstrap Admin always among them — and the Admin
    group's Permissions. An Unlock the backend still refuses `409` — another
    administrator unlocked the User first — says the User is no longer locked.
  - **The backend sorts and pages.** The page owns only the sort headings and
    which Groups' members are open; a search or sort, the search shown
    submitted again included, reads the first page afresh.
- `accounts/group-permissions-dialog.tsx` — the Group Permissions editor, not a
  page: a modal checkbox dialog fed by `GET /api/admin/permissions` and
  pre-ticked with the Group's current set. It hands the complete set to the
  page, which owns the `PUT` — the dialog closes once the save settles, and a
  reporter that unmounts withdraws what it reported, so the write's outcome
  must belong to the page's line.
- `accounts/connectors.tsx` — the connector panel, not a page: it lists,
  creates and deletes connectors and issues, rotates and revokes their tokens.
  Each connector section reads its own active tokens through the shared pager
  and re-reads that page after every token change (one change can touch more
  than one token); creating or deleting a connector re-reads the listing. A
  token's plaintext lives only in component state, shown once. Create, delete
  and every token action are offered only to a Superuser (the declarations
  require it); a `connector:read` holder sees the listing alone. A delete is
  sent only from its confirmation dialog. The issue form offers every token
  Permission, Users before Groups and read before write, while the request
  keeps the backend's order; a rotation refused `409` has its own copy.
- `accounts/accounts-api.ts` — the wire types both share and every endpoint
  they call; derives `ADMINISTRATION_PERMISSIONS` from the listing
  declarations.
- `audit/audit.tsx`, `audit-filter-popover.tsx`, `audit-api.ts` — the audit
  listing, filtered on the server by one or more operations, outcome, actor
  id, resource id and a time range.
  - The API module checks each filter before the request is built, so a
    malformed one is refused in the filter panel, beside its field. Ids stay
    ids (resolving them would need `user:read`).
  - It reads on mount, on Apply, Reset, paging and Refresh, **never on a
    timer** ("Why nothing reads on a timer", `ARCHITECTURE.md`). It offers
    "Refresh" always, in place of "Try again", since the trail grows with
    nothing on the page to say so.
  - The pager names a total only once a read of the current query has stated
    one.
  - A row is an expandable row (`DESIGN.md`, "Expandable rows").
  - The panel is built from the shared filter parts; its time range is its
    own, two `datetime-local` fields read by `parseTypedTime`.
- `audit/audit-operation-labels.ts` — each operation's label, in the rows and
  in the Operation picker, which searches and shows labels and sends the
  operation. A `Record` keyed by `AuditOperation`, so a new operation fails
  typecheck until it has one.
- `status-badge.tsx` — the one status-to-Badge-variant mapping, for an audit
  outcome and a User's active state, on Accounts and Audit.

## Patient pages

- `search/search.tsx`, `search-api.ts`, `patient-filter-popover.tsx` — Patient
  Search. One box; `parsePatientQuery` sends a full NRIC, or a name of at
  least three characters, trimmed, and refuses anything shorter beside the box
  with nothing sent. The search and its gender filter are component state
  alone, never the URL, and read only on submit: each submit remounts the
  results, which read `GET /api/patient-search` through `PATIENT_SEARCH` and
  the shared paged listing, naming the range whenever there is a match, in the
  patient source's own order — every heading is a `PlainHeading`. A change of
  the filter reads from the first page. A failure is reported on the page's
  line, with a Retry that reads the same page again. Each row keeps the
  Patient's id, which it never shows. Every cell is shown as received through
  `shown` (ADR 0020): its value, "—" when missing, "Error" when invalid, and
  the gender's label and badge tint through `byCode`, a code outside the four
  shown as itself on the plain badge. A row with no id — an entry that is no
  Patient — is "Error" in every cell and selects nothing. A page the backend
  dropped matches from (`droppedResults`) says how many beneath the table,
  "N records couldn't be shown", and the shared pager counts them among the
  page's records (`Page.droppedResults`, `src/lib/paging.ts`).
- `home/home.tsx` — Home: its title, shown, above a card saying the account
  has no access to patients. The landing page of a session without
  `patient:read`, refused to one holding it, and linked from no sidebar.
- `patient-card.tsx` — the Patient card, heading the Demographic and HSP pages,
  rendered from `usePatientInContext()`: the plain card the history pages
  show, "No patient in context", for none or a `404` (`isWithoutPatient`, by
  which the shell shows the page's title then), "Loading the patient…", an empty card for a failure (its
  copy is on the error line), or the banner. Every field renders as a plain
  text node, shown as received through `shown` — its value, "—" when missing,
  "Error" when invalid, a gender outside the four codes by `labelled` as its
  code; the age is computed from the card's injectable `Clock`, and follows the
  date of birth, "—" or "Error" as it is. The top bar names the patient by
  name and NRIC on the same rule.
- `demographic/demographic.tsx` — the Demographic page, guarded by
  `patient:read`, which Patient Search opens on selecting a result: the
  Patient card, then the Demographic details. With no patient in context it
  is its title, shown, above the Patient card's plain "No patient in context"
  card, as a history page is.
- `demographic/demographic-details.tsx` — the Demographic details of the
  patient in context, rendered from `usePatientInContext()` (importing
  `patient-api.ts` for its types alone), and nothing while no patient is
  loaded: the Military Overview (at least three service record slots, a
  slot holding any field open and an empty one folded at first, then the
  service dates as `dd MMM yyyy`), Contact Details and Next of Kin / Emergency,
  "No next of kin recorded" for a patient with none. Each detail is shown as
  received through `shown`, independently of the others — its value, "—" when
  missing, "Error" when invalid — and Unit / Service joins the unit and the
  service each so shown, one missing left out; every value renders as a plain
  text node.
- `patient-in-context.ts`, `patient-api.ts` — the **one reader of the patient
  in context**. `PatientInContextScope`, which the signed-in layout renders
  once inside its page status, reads `GET /api/patient/{id}` through `PATIENT`
  (opting in to `absent`, a `403` reported as failure) for the EHR context's
  patient, decoding the R5 `Patient` (typed by `@types/fhir`'s `fhir5`) down
  to the fields shown, each a `SourceField` (ADR 0020), the Demographic
  details' values among them; the patient it hands on carries the id it was
  asked for — the EHR context's — which the history pages read by, never one
  the Patient carries.
  With none in context it reads nothing.
  - A new id is a new path, so a new first read, and a late answer for the id
    before never lands. A failure is reported to the page's error line once,
    by this read.
  - `usePatientInContext()` hands its view — `none`, or the gated read's — to
    the top bar, the Patient card, the Demographic details and the history
    pages, which only render it,
    so each patient change sends one request on every page.
  - The read is a sibling of the layout's children, never their parent, so a
    patient coming into context or leaving it never remounts the page.
  - It lives in `src/pages/` itself because that is the lowest layer the
    shell, the Demographic page and the history pages may all import:
    `mb-pages-do-not-import-the-shell` rules out `src/shell/`, and
    `mb-pages-do-not-import-another-page` a page folder.
- `for-patient-in-context.tsx` — the part of a history page the patient in
  context decides. `ForPatientInContext` is handed the page's history: with
  none in context, a `404` for the id there, or a session without
  `patient:read`, `NoPatientInContext` ("No patient in context") and no
  history; while the patient is read, "Loading records…" in a `status`; a
  failed read, nothing, its copy on the error line; and once read, the history
  of that patient, by its id, keyed by it. A history whose own read answers
  `absent` renders `NoPatientInContext` too.

### The history pages

Medication, FFI, NUMC, ARMC and HSP are one page shape over five histories
(`medication/`, `ffi/`, `numc/`, `armc/`, `hsp/`): each folder holds the page, its
`*-api.ts` and its `*-filter-popover.tsx`. Each page is linked for, and
guarded by, `patient:read`, is read-only with no row action, and:

- renders through `ForPatientInContext`, then `FilteredHistory`;
- reads `GET /api/patient-<history>/{patientId}` through its paged
  declaration, which reads a `404` as `absent`;
- decodes strictly: each date through `calendarDate` or `dateTime`
  (`src/lib/decode.ts`), an optional element through `optionalString` — absent
  when the source lacks it, shown as `—` — and each closed set as a `Record`
  of labels keyed by its codes, so a code added fails typecheck until it has a
  label. FFI, NUMC, ARMC and HSP, shown as received (ADR 0020), decode their rows'
  shape strictly and each field's value through `readObject(…).sourceFields()`,
  each cell through `shown` and each code's label or badge through `byCode`
  (`src/lib/source-field.ts`): "—" for a missing field, "Error" for one in the
  wrong format, the raw code for one outside its set;
- sorts by its two date columns through `SortHeading`, every other column a
  `PlainHeading`, and with no column sorted sends no `sortBy`, so the source's
  own order applies;
- sends each filter only when set: a code set as its parameter repeated in the
  set's own order (`codeParameters`, `src/lib/code-parameters.ts`), the dates
  through `historyDateParameters`, then the sort through `sortParameters`.

What differs is each history's own:

| Page       | Date columns                        | Filters beside the date ranges                                | Shown as a badge | Mandatory elements                     |
| ---------- | ----------------------------------- | ------------------------------------------------------------- | ---------------- | -------------------------------------- |
| Medication | Start Date, End Date, as date-times | Dispense Status (any of)                                      | Dispense Status  | Medication, Dispense Status            |
| FFI        | Issue Date, Expiry Date             | each closed set but the Status (any of), and the Status (one) | Outcome, Status  | FFI Type, Outcome, Status              |
| NUMC       | Issue Date, Expiry Date             | Type and Outcome (any of)                                     | NUMC Outcome     | Type, Outcome, Issue Date, Expiry Date |
| ARMC       | Start Date, End Date                | Outcome (one)                                                 | ARMC Outcome     | Board Type, Outcome, Start Date        |
| HSP        | Start Date, End Date                | Outcome and Submitting MC (any of), and the Status (one)      | HSP Outcome      | Outcome, Status                        |

`DESIGN.md` ("Badge") has which variant each status takes. The Medication
table is wider than its card: the table primitive keeps each cell to one line
and scrolls sideways in its own container. Each of the five tables declares
its Mandatory elements beside it, in column order, each label its column's
heading, so the two cannot drift; a page where some row misses one opens its
table with a narrow leading column of warnings (`mandatory-elements.tsx`), and
a page where none does has no such column.

HSP alone is headed by the Patient card, as the Demographic page is: the
patient in context's banner, then a card headed "HSP History" holding the
listing, that heading beside its Filter button (`FilteredHistory`'s
`heading`). Its title is kept for screen readers only while the banner heads
the page (`titleHidden`). With no patient in context — none, or a `404` for
the id there — it is its title, shown, above the Patient card's plain "No
patient in context" card, with no HSP History card, and its history is not
read. Its Status is coloured text, not a badge.

- `filtered-history.tsx` — `FilteredHistory`, the filtered, sorted listing
  every history page renders once the patient in context is read. Each page
  hands it its paged declaration, its page size, its Filter panel, its
  no-filter value and filter count, its failure copy and pager label, and its
  table. It holds the filters and the sort as component state alone, never the URL, so a new
  patient in context starts with neither; reads through `usePagedRead`, a
  change of either from the first page; and renders the Filter panel above the
  shared `PagedListing`, beside the history's own heading when the page hands
  one, its range named on a single page too. A `404` is
  `NoPatientInContext`; with a filter applied, the listing is narrowed by
  filters, so an empty history says "No records match the filters."; and the
  table is told while a read is pending, so its sort headings hold still.
- `history-api.ts`, `history-filter-popover.tsx` — what the histories share,
  naming no history's column. Each history's `*-api.ts` **declares its date
  columns** (`FFI_DATE_COLUMNS`, …), each a `DateColumn` (`lib/date-range.ts`):
  its name — the prefix of its range's `<column>From` and `<column>To`
  parameters, and the `sortBy` that sorts by it — its label, and its
  **precision**, `date` (a calendar date) or `date-time` (a date-time to the
  minute, Singapore's wall clock with no offset). The history's sort
  (`ColumnSort<DateColumnOf<…>>`, `lib/sorting.ts`) and its ranges
  (`HistoryDates`) are typed from that declaration.
  - `history-api.ts` reads any history's declared columns: `noHistoryDates`
    leaves every end open, `countHistoryDates` counts each end set as one
    filter, `historyDateParameters` sends each end set as its own parameter in
    the order the columns are declared, and `checkDateRanges` checks each
    range at its column's precision.
  - `HistoryFilterPopover` is the panel the history pages open: the shared
    `FilterPopover` counting the filters applied, and a form — named by the
    page, its fields laid out by the page around a `DateRangeFields` for each
    declared column — that keeps its own draft, applies only on Apply or Enter
    in a date, refuses a bad range beneath its field with nothing sent, and
    resets to the page's own no-filter value.
- `medical-centre-api.ts` — Medical Centre, the closed set the FFI's and the
  HSP Record's Submitting MC and the Medication Record's Dispensary share:
  `MEDICAL_CENTRES`, which the SPA–API contract check holds to `openapi.yaml`'s
  `MedicalCentre`, and `MEDICAL_CENTRE_LABELS`. It sits in `src/pages/` because
  three pages' API modules read it.

## Shared parts

- `paged-read.ts` — **the paged read**, `usePagedRead`: the one hook every
  paged listing reads through. A listing hands it its declaration, its own
  arguments (a search and sort, a Group's id, a parsed filter set) and its
  page size; the hook holds the `startIndex`, sends the gated read, and hands
  `PagedListing` the view, the move, the page size and the read again. New
  arguments read the first page again, in the same render, and `restart` reads
  the first page afresh as one read, even for the arguments shown. The
  declarations are built from the paging vocabulary in `src/lib/paging.ts`
  (ADR 0017): `pagedPath`, the one query string, the listing's own parameters
  then `startIndex` and `count`; and `pageDecoder`, the one decoder of a
  page's `totalResults`, `startIndex` and rows, which refuses any other field
  — but `droppedResults`, which a read that drops entries (Patient search)
  declares with `dropsEntries`, and its page then always carries.
- `paged-listing.tsx` — the one paged listing, which every paged read renders
  through: the listing body, the pager and "Try again", around the rows the
  page renders as its own table or list. It decides, once, for every page:
  rows a failed re-read could not confirm are hidden; the loading and empty
  copy come from the caller's `narrowing` alone (`listing-copy.ts`); the pager
  shows for the page the latest read stated for the current path and never
  while a first read is in flight or after one failed with nothing read; and
  "Try again" re-reads the current path through the read's `reload`, keeping
  the page shown. Filters, search
  boxes and toolbars stay the page's.
- `listing-copy.ts` — what every listing says of its rows, in one place: the
  loading and empty copy of each `Narrowing` (none, filters, search, or the
  Search page's `patient-search`) and the pager's count of records. No caller
  passes copy of its own.
- `listing-body.tsx` — the one listing body, with the loading, empty and
  failed states of `DESIGN.md` ("Loading & empty states"), its copy chosen by
  the caller's `narrowing`. Each caller names its `staleRows` choice:
  Connectors keeps the last rows, and `paged-listing.tsx` hides them. Its `ReadAgain` is the one "Try again",
  offered only once the latest read was refused or failed.
- `pager.tsx`, `pager-move.tsx`, `page-empty-copy.ts` — the one pager, which
  `paged-listing.tsx` places: the position line in records ("Showing 1 to 20
  of 45 records", "… of 1 record"), « and » each disabled at its end, a
  number per page, and nothing while every row sits on the first page unless
  the listing asks for its range named anyway (`onePage="shown"`).
  `PagerMove` is one of its buttons, a `Button` rather than shadcn's
  `PaginationLink`, whose `<a>` would have no `href` because paging is
  component state rather than a URL. `page-empty-copy.ts` chooses the body's
  empty line: a later page says it emptied, where the first gives the
  listing's empty copy.
- `sort-heading.tsx` — the sortable column heading: `aria-sort`, a direction
  icon, and a press that sorts its column ascending first, then toggles
  descending → ascending → …, never returning a sorted column to the listing's
  default order — and `PlainHeading`, the heading of a column that does not
  sort. The sort it sets, `ColumnSort`, lives in `src/lib/sorting.ts` beside
  `sortParameters`, the one encoding of a sort as its `sortBy` and `sortOrder`
  query parameters.
- `mandatory-elements.tsx` — the warning on a row whose Mandatory element is
  missing or in the wrong format (ADR 0020). A history declares its Mandatory
  elements beside its table, each a `MandatoryElement`: the record's field and
  its column's exact heading, in column order. `MissingInformationHeading` is
  the leading column's heading, visually hidden and read as "Missing
  information"; `MissingInformationCell` is each row's leading cell, empty or
  holding a `TriangleAlert` icon in the warning tone that is a tab stop, whose
  tooltip — "Critical information missing: " and the affected columns'
  headings — is its accessible name. `anyWarns` says whether any row of the
  page warns: a table renders the heading and every leading cell only then,
  so a page with no warning has no leading column. It reads only each field's
  `SourceField` state, never a value, and the row is shown in full.
- `filter-popover.tsx`, `multi-select-picker.tsx`, `choice-toggle.tsx`,
  `date-range-fields.tsx` — the shared filter parts every Filter panel is
  built from.
  - `FilterPopover` is the Filter button and its panel: the button names how
    many applied filters are set, and the panel's form is mounted each time it
    opens, so an edit never applied is forgotten, and closes only once an
    Apply or Reset has applied.
  - `MultiSelectPicker` is a searchable multi-select over a closed set, by
    label, with "Clear selection" first and last while anything is selected.
  - `ChoiceToggle` is a one-of-several toggle row (All, or one value) that
    never leaves the choice empty.
  - `DateRangeFields` is a from–to pair of fields in its column's precision's
    native control — `<input type="date">` or `<input type="datetime-local">`,
    which holds no value until a time is chosen beside the date — both marked
    invalid with the refusal shown beneath. A range is checked by
    `src/lib/date-range.ts`: `dateRangeRefusal` refuses an end not written at
    its column's precision and a from-end after its to-end, both ends
    inclusive, as the backend does.

## Change password

- `change-password/change-password.tsx` — runs `changePassword` through
  `useSessionAction`, not `useSessionRequest`: its marked `401` is about the
  current password, and the seam would end the session on it
  (`BACKEND_CONTRACT.md`). Its inputs are uncontrolled, because React mirrors a
  controlled input's value into the DOM `value` attribute.
