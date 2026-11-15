---
type: Design Reference
title: Design system
description: The component library's design language — what each colour token and type level is for, and how the shared components behave — without the values, which live in src/index.css.
tags: [design, tokens, components, accessibility]
---

# Design system

What each token is for and how each shared component behaves. **The values —
every colour, size, radius and spacing — live in `src/index.css` and the
components' own classes, and nowhere here**: read them there, and change them
there. `ARCHITECTURE.md` ("Styling and the token pipeline") has how a token
reaches a utility.

## Brand & style

A clinical and administrative system of record: dense data tables, status
badges and patient banners in a cool, procedural navy-and-mint palette, on a
light theme only. Shapes are understated and squared-off, and depth comes from
a hairline border and a surface tint, never from a shadow on a resting card.
Every component shares the one palette, type scale and shape language, with no
per-page theme.

## Colours

A colour is named by its role, through a token of `src/index.css`, never by a
raw value or a shade of Tailwind's own palette.

| Token                     | Role                                                                        |
| ------------------------- | --------------------------------------------------------------------------- |
| `primary`                 | Brand chrome: active navigation, primary buttons, the page shown in a pager |
| `secondary`               | The fallback badge, for a value with no status of its own                   |
| `destructive`             | An action that ends or removes something: Sign out                          |
| `card`, `muted`, `accent` | Surface tiers: a card's body, a nested panel, a table's header row          |
| `border`                  | Hairline borders only, never text                                           |
| `muted-foreground`        | Secondary text: captions, subtitles, empty copy                             |

Status badges draw on four **soft status** pairs, each a light fill under dark
text of the same hue that meets WCAG AA contrast: `success-soft`,
`warning-soft`, `danger-soft` and `info-soft`, each with its `-foreground`.
"Badge" below says which status takes which.

A Patient's gender badge in Patient Search's results is no status: Male draws
on `male-soft` and Female on `female-soft`, applied by the page, and any other
gender keeps the `secondary` badge.

## Typography

One named type scale replaces Tailwind's generic sizes: `body-sm`, `body-md`,
`body-lg`, `label-md`, `data-mono`, and `headline-sm`, `headline-md`,
`headline-lg`. Each level is one utility, `text-<level>`, that sets the level's
size, line height, weight and letter spacing together; a `leading-*`, `font-*`
or `tracking-*` beside it overrides just that property.
`src/type-scale.test.ts` states each level's values and holds `src/index.css`
to them.

- **Faces.** Every sans level, headlines included, is IBM Plex Sans, so a
  headline differs from body text by its size and weight alone. `data-mono` is
  IBM Plex Mono, so an NRIC or an id renders in the same face on every OS.
- **Pair a level with its face.** The family is not part of a text utility:
  the headline levels take `font-headline`, `data-mono` takes `font-mono`, and
  the body and label levels take nothing, the sans being the default.
- **A text colour never drops a level.** `cn` knows the levels are font sizes,
  so a caller's `text-muted-foreground` merges beside one.
- **Which level.** The signed-in page title is `headline-lg`, a card title
  (`CardTitle`) `headline-sm`, and a table's column heading `label-md`.

The faces are self-hosted from `public/fonts/` (latin subset), never a font
CDN, because the backend's CSP admits fonts from the app's own origin only
(`BACKEND_CONTRACT.md`). They are licensed under the SIL Open Font License
(`public/fonts/IBM-Plex-LICENSE.txt`). A weight with no vendored face renders
as the nearest one that is, so a new weight needs its file in `public/fonts/`
and an `@font-face` beside the others.

## Components

### Button

Solid primary, outline, icon-only ghost (a chevron, a close), and a text link
styled as a button for a row action. A disabled button is `disabled` and
dimmed.

### Select and filters

A filter over a few values, as the Audit page's Outcome filter is, is a
`ToggleGroup` of `type="single"`. A filter over a long closed set, as the Audit
page's Operation filter is, is a searchable multi-select: a `Popover` holding a
`Command` list with a checkbox per value, its trigger naming the filter and how
many are selected.

### Badge

A status takes one of five variants, each drawn from a theme token pair — the
four soft pairs under "Colours", or `muted` — and never from a raw colour:

- `success` — a positive state: an audit event's Success, an active User, a
  FIT Outcome (an FFI's, NUMC's, ARMC's), a DISPENSED Dispense Status.
- `warning` — a state to notice that is no fault: a Deactivated User, which
  reads "Deactivated", never "inactive".
- `danger` — a refusal or failure: an audit event's Failure, an UNFIT Outcome
  (an FFI's, NUMC's, ARMC's), an UNCOLLECTED Dispense Status.
- `info` — a granted waiver: NUMC's WAIVER GRANTED.
- `muted` — a state set aside, neither good nor bad: an FFI's Inactive, NUMC's
  WAIVER REJECTED, a PENDING Dispense Status.

`src/pages/status-badge.tsx` maps an audit event's outcome and a User's status
to its label and variant. Each history page maps the closed sets it shows as
badges to their variants beside the table that shows them (`ffi/ffi.tsx`,
`numc/numc.tsx`, `armc/armc.tsx`, `medication/medication.tsx`), by the rules
above.

### Signed-in chip

The top bar names who is signed in with text alone, never an avatar: the
username as stored, and beneath it one line per Group, in capitals and
truncated to the column's width. More than two Groups show the first two and a
`+N more` line whose tooltip lists them all, so the bar keeps its height
(`src/shell/top-bar.tsx`).

### Table

The header row sits on a tinted surface that a hover leaves alone, and a
heading stands over its column's values, a sortable heading's button included.
Identifiers — UUIDs, correlation ids, routes — are set in monospace, and
timestamps in tabular figures, so a column of them aligns.

### Sidebar

The persistent navigation rail (`src/shell/navigation-sidebar.tsx`).

- Its labels are shown in capitals by styling alone, so their accessible names
  keep their case.
- Its items stand edge to edge with no gap between them, so each is hovered
  and clicked across its whole row.
- Sign out, at the foot, is `destructive`.
- Accounts and Audit logs sit in a collapsible ADMIN group, whose button
  carries the group's icon and a chevron that turns when open. The group starts
  open; its links grow and fade in as it opens and shrink and fade out as it
  closes, at once with reduced motion.
- On a sidebar folded to its icons, the group's button opens the sidebar out
  to the group.

### Pagination

Every paged table shares one shape: a muted "Showing X to Y of Z <rows>"
caption on the left, and on the right a row of compact buttons — `«`, one per
page number (the first, the last, and the shown page's neighbours once past
seven, with an ellipsis for each run left out), `»`. The page shown is filled
with `primary`. A listing whose rows all fit on the first page shows no
pagination at all.

### Expandable rows

An expandable table row keeps its scannable columns in the row and moves the
rest into a detail row beneath it.

- The row's last cell holds a ghost icon `Button` — a chevron that turns when
  open — named for the row (`Details of Login failed at 2026-01-03 03:04`),
  with `aria-expanded` stating its state and `aria-controls` naming the detail.
- The detail row is one cell spanning every column, rendered while open and
  through its closing animation, holding a `region` of the same name with the
  fields as a `<dl>`.
- Each row opens on its own, and a native button gives Enter and Space.
- The detail grows and fades in as it opens and shrinks and fades out as it
  closes; with reduced motion it appears and leaves at once.

The Audit page's rows are this pattern, and also toggle on a click anywhere on
the row or its detail — except a click that ends a text selection — with the
button stopping propagation so it toggles once. The Accounts page's Group
member lists open the same way from the member count.

`Collapsible` is for open state that is not mutually exclusive; `Accordion` is
reserved for real single- or multiple-open semantics.

### Loading & empty states

Every listing renders these through the one listing body
(`src/pages/listing-body.tsx`), so they look the same on every page:

- **Loading** — while a listing's first read is in flight, full-width
  `Skeleton` rows stand where its rows will be. The skeletons are
  `aria-hidden`; the listing's loading copy ("Loading users…") sits beside
  them in a `status` region, visually hidden, so a screen reader still hears
  it.
- **A later read of a paged listing** — a move, a sort, a filter — holds the
  page last read in place, dimmed after a short delay, `aria-busy` and out of
  the pointer's reach, with the pager disabled, so the listing never drops
  back to its skeletons; its loading copy is still said in a visually hidden
  `status` region. A page with no rows is not held, and a failed read drops
  the held page. A page short of rows stands as tall as the listing's last
  full page did, so the pager stays where it was.
- **Empty** — a successful read with no rows shows a centred, muted,
  decorative `Inbox` icon above the listing's empty copy. The copy is the
  listing's own: "No connectors exist.", or a search's "No users match …", or,
  for a later page that emptied, the pager's "This page is now empty."
- **Failed or not permitted** — the body is blank. A failure is never drawn as
  empty or as a load still under way; the page's one error line says what went
  wrong. Whether a failed refresh keeps the last rows is each listing's own
  `staleRows` choice; every paged listing (`src/pages/paged-listing.tsx`) hides
  them.
- **Read again** — a listing whose read failed offers a "Try again" button
  beneath the body (beside the pager on a paged listing), which reads the same
  page, or the Connectors listing, again. The audit trail's "Refresh" is the
  same button, offered whatever the read came to, in the card's header in
  place of "Try again".

The Patient card and the Group Permissions dialog are not listings and keep
their plain loading lines.
