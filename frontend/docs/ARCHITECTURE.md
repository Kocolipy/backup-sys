---
type: Architecture Reference
title: Frontend architecture
description: The SPA's layers file by file, why every request goes through lib/http.ts and every time through lib/time.ts, the gated read and write, the @/ alias, the token pipeline, routing, and what is deliberately absent.
tags: [architecture, react, spa]
---

# Architecture

What is here, and where a new concern belongs.

## The layers

```
src/
  main.tsx            composition root — mounts React, imports index.css
  App.tsx             app root — BrowserRouter, AuthProvider, the route table
  index.css           Tailwind entry + the design tokens
  vite-env.d.ts       /// <reference types="vite/client" />
  auth/               session state, Permission checks, route guards, request seam
  shell/              the signed-in layout every page renders inside, and the page table (SIGNED_IN_PAGES)
  pages/              one folder per page — its component, its API module declaring its endpoints, and the parts only it uses
  components/         shared non-primitive components (error-boundary.tsx)
  components/ui/      shadcn primitives (see "Why components/ui/ is fenced off")
  lib/                framework-agnostic helpers; a leaf
```

Imports run one way: `main` → `App` → `shell` → `pages` → `auth` →
`components/ui` → `lib`. A layer may skip a step (a page may call `cn` directly, `auth/` reaches
straight into `lib/http`) but may never point back up.
`test/.dependency-cruiser.cjs` encodes that, and `npm run test:arch` runs it.

### `auth/`, file by file

`src/auth/` is a _concern_, not a screen. This is the authoritative per-file map.

- `api.ts` — the `/api/auth/*` calls, each mapping a status to a domain outcome
  (`401` on `/me` is a guest, not an error), the decoder for the session's
  `permissions` and `superuser`, and `PERMISSIONS`, the closed set the backend's
  `Permission` enum defines (a session reporting another name fails decoding).
  `superuser` is the Superuser's standing, a boolean beside the Permissions and
  never one of them. The **session actions** — `login`, `logout`,
  `changePassword` — and the start-up check, `getCurrentUser`, each resolve to a closed outcome (`LoginOutcome`,
  `LogoutOutcome`, `PasswordChangeOutcome`, `SessionCheckOutcome`) and never
  throw.
- `session-action-copy.ts` — the copy for every session-action outcome, and the
  only place it lives (`loginMessage`, `logoutMessage`, `passwordChangeMessage`,
  `sessionCheckMessage`).
- `use-session-action.ts` — `useSessionAction`, the one way a page runs a
  session action: it withdraws the earlier line, holds the pending flag, and
  shows nothing for an outcome that moved the session (the guards move the
  visitor on) or else the copy's line. Inside the signed-in layout it reports
  to the page status; outside it (Login, the idle warning's Sign out) it hands
  back `error`. No caller reads an outcome or catches. `run` resolves `"stayed"`
  or `"moved-on"`.
- `permissions.ts` — `holds` / `holdsAny`, `SUPERUSER`, and the one rule for
  whether a session may open a page. `holds` answers a `Requirement`: a
  Permission, or the Superuser, which no number of Permissions satisfies. The one
  rule: `requirementOf` reads a page's `permissions` as "any
  one of these", `mayOpen` decides. Which Permission a _request_ needs is
  declared on the endpoint instead.
- `declarations.ts` — an endpoint as data: a `ReadDeclaration` (Permission,
  path, decoder, opt-in `absentOnNotFound`) and a `WriteDeclaration` (plus method
  and optional JSON body, a Permission or the Superuser as its requirement, and
  an optional `decodeFailure` reading the one refusal its page shows itself).
  The API module that exports one builds its path from the call's arguments, so
  no page concatenates one. A page reads
  `declaration.permission` to offer a control and hands the same declaration to
  the hook that sends it, so the two cannot drift — each control asks about the
  declaration it sends, never a neighbour's that happens to share its Permission.
- `page-status.ts` — the signed-in page's single error line: `PageStatusScope`
  holds it, `useStatusReporter` is a source's handle on it. Every gated read and
  write reports there, and **the latest event wins**: a failure becomes the
  line; a success withdraws only its own source's failure; a write starting
  withdraws every failure. The scope exists once, rendered by `SignedInShell`
  (it throws when nested; a reporter throws outside one and can never read). It
  resets on every path change, and a source that unmounts withdraws its failure.
- `session-transitions.ts` — `useSessionTransitions`, the one place the auth
  state (status, user, sign-in reason) changes, as `signIn(user)` or
  `end(reason)`, each forgetting the CSRF token in the same step (ADR 0009);
  `end` forgets the tab's EHR context too.
  `checkFailed` (status `unchecked`) and `recheck` change the status without
  changing the session, so they keep the token. It also owns the **session
  generation** — which session is current: every `signIn` and every `end`
  starts the next. `generation()` reads it, and `expire(generation)` ends the
  session as `expired` only while that generation is still current, so a
  `401` that lands after a sign-out or a fresh sign-in changes nothing.
- `ehr-context.ts` — the tab's **EHR context**, the patient the tab has
  in context, as a subscribable store under one `sessionStorage` key:
  `setEhrContext()` replaces it, holding the patient to FHIR's id grammar, and
  the Search page calls it when a result is selected; `ehrContext()` reads it back, decoded rather
  than trusted, the same value until it changes, and `null` when the tab holds
  none; `subscribeEhrContext()` / `useEhrContext()` re-render a reader when it
  changes; and `clearEhrContext()` forgets it, on every session end and a
  password Login.
- `auth-context.tsx` — the `AuthProvider`: checks the session on mount and on
  `retrySessionCheck` (`ok` signs in, `401` ends, anything else is
  `checkFailed`) and maps each session event onto a transition with its sign-in
  reason.
- `auth-context-value.ts` — the context and `useAuth`, split out so the hook
  does not pull in the provider. `useAuth` exposes no way to _end_ a session and
  no sign-in reason; that is `useAuthState`'s, internal to `src/auth`.
- `sign-in-reason.ts` — the closed set of **sign-in reasons**, how each
  reaches the login page (router state), and the copy. Router state is decoded
  against the set, never cast.
  `signInNoticeFor` is all the login page calls.
- `session-route.ts` — `resolveSessionRoute`, the whole routing contract as one
  pure transition table (see "Routing").
- `route-guards.tsx` — `ProtectedRoute` and `GuestRoute`, thin adapters over
  that table sharing one pending view and one `unchecked` view (an error line
  and "Check again"). An `unchecked` session is not a Guest, so neither guard
  renders its page or redirects.
- `use-session-request.ts` — the seam features request through. It acts on
  `unauthenticated` itself and returns a `SessionResult` with no such member,
  so no page can forget to relay a session ending. It reads the session
  generation as it sends a request, and on that request's `401` expires that
  generation, never a later session.
- `use-gated-read.ts` / `use-gated-write.ts` — the gated read and write (below).
- `idle-sign-out.tsx` — `IdleSignOut`, mounted by `AuthProvider` for an
  authenticated session (`docs/BACKEND_CONTRACT.md` has the rule it keeps).
- `password-policy.ts` — the backend's password length bounds, mirrored so the
  form can state them; the backend still decides.

### The gated read and write

**Every listing read** — Users, Groups, connectors, the audit trail, the
patient in context — goes through `useGatedRead`, handed a declaration, its
arguments and the page's failure copy. The hook keeps no shared cache, so each
caller is its own read: a value two parts of the screen show has one reader,
as the patient in context has (`pages/patient-in-context.ts`). It sends nothing without the Permission, resets when
the Permission or path changes, and **the newest read wins**: every read it
sends (on opening, a key change, `reload()`, or a write's `after`) supersedes
the one in flight, in the view and the page status alike. A `reload()` in the
same event as a change of arguments is that change's one read, not a second.
It returns one closed `ReadView` — `not-permitted`, `loading`, `failed`,
`ready`, or `stale` (a refresh failed, the last data kept) — so a page switches
on the view instead of re-checking flags.

- **`absentOnNotFound`** (on the declaration, because whether "not found" is
  news is a fact about the endpoint) adds a sixth view, `absent`, for an
  endpoint whose `404` means "nothing there". It reports nothing and keeps
  nothing (`ready` → `absent`, never `stale`). Opt in only where `404` means
  nothing there, never where it means "this went away under you". The view type
  follows the declaration (`ReadView<T, N>`), so a read that never opts in has
  no `absent` case.
- **`forbiddenAsFailure`** (the page's option, since it chooses copy) reports a
  `403` with the page's failure copy, still as `failed`, still without ending
  the session. The read of the patient in context is its one user.

**Every page action** — Unlock, a Group's Permissions, connector and token changes —
goes through `useGatedWrite`, handed a declaration, its arguments, the success
handling, the copy for the statuses the page cares about, and optionally an
`after` step run inside the pending window (Connectors' listing re-read). It
sends nothing for a session lacking the Permission (settling `forbidden`), owns
the pending flag and the refusal copy, and withdraws every page-status failure
as it starts. It has no branch for ADR 0015's protected-resource `400 scimType: mutability`: no
endpoint the pages call answers it.

`mb-transport-is-behind-the-session-seam` holds the direction: only `src/auth/`
and `src/lib/` may import `lib/http.ts`, so a page cannot call `apiFetch`.
`mb-pages-request-through-the-gated-hooks` holds the step above it: no page,
shell or shared component imports `use-session-request.ts`, so every request
they send passes through the gated read or write, where its declaration's
Permission is applied. `mb-session-transitions-have-one-caller` keeps
`session-transitions.ts` to its one production importer, `auth-context.tsx`.

### The pages

Pages consume `auth/`; they hold no session or Permission logic and never
decide where a visitor goes. A page's folder never imports another's: a part
two pages share moves up to `src/pages/` itself, and imports no page folder
(`mb-pages-do-not-import-another-page`, `mb-shared-page-parts-do-not-import-a-page`).

**`PAGES.md` is the per-file map of `src/pages/`** — read it before adding or
changing a page, its API module, or a part the pages share (the paged read,
the listing body, the pager, the filter parts, the patient in context).

### Why every request goes through `lib/http.ts`

`apiFetch()` is the one place that knows the CSRF rule
(`docs/BACKEND_CONTRACT.md`). It returns an `ApiResult`: `ok` carries data from
an explicit decoder (`void` when the request omits one), `unauthenticated` means
the session ended, `forbidden` is an authorization refusal, `csrf-expired` is a
token fetch that itself failed, and `failed` covers every other transport,
HTTP or decoding failure. Pages keep their own copy; the meaning is shared.

A body is never cast (`as T`). A hand-written decoder takes `unknown` and
returns the value or throws `DecodeError`, built from `lib/decode.ts`'s
primitives and living beside the wire type it decodes; a success body that does
not decode is `failed`.

It lives in `lib/` because `auth/` sits above it and it must not know about
sessions or React — `mb-lib-is-a-leaf` holds that.

`test/arch/rawNetwork.test.ts` (`no_raw_network_outside_transport`) names
`lib/http.ts` and no other module.

### Why nothing reads on a timer

Every request renews the server session, so a page that read on a schedule
would hold off the idle timeout for as long as its tab stayed open. A page reads
on mount and on the user's action — Apply, Reset, paging, Refresh — and never on
a timer. `test/arch/timers.test.ts` (`no_timer_outside_idle_sign_out`) fails
any `setTimeout`, `setInterval`, `requestAnimationFrame` or
`requestIdleCallback` outside `auth/idle-sign-out.tsx`, whose timer times the
session's own window and sends nothing on a schedule.

### Why every time goes through `lib/time.ts`

The SPA reads and shows every time in Asia/Singapore, the zone the backend
names for operators, whatever the viewer's machine. Singapore is a fixed +08:00,
so `lib/time.ts` states the offset as a constant and is the one module that
knows it: an instant is shown as `formatDate` / `formatInstant`; a typed
`datetime-local` (Audit's From and To) is read by `parseTypedTime`; a FHIR
`date` is checked by `isCalendarDate` and shown by `formatCalendarDate`; and
`today` reads a `Clock` a component is handed, so no page writes `new Date()`.

The unit suite pins its machine zone to one that is neither UTC nor Singapore
(`vitest.config.ts`), so a slip back to the machine's zone shows.
`test/arch/timeModule.test.ts` fails any other source that builds a `Date`,
formats through `Intl` or a local getter, or names a zone.

### Why `components/ui/` is fenced off

It holds the in-house shadcn component library, in source form rather than as
a published package — `DESIGN.md` documents its design language: what each
token is for and how each component behaves. It stays leaf-only
on purpose: `mb-ui-primitives-are-leaves` holds its contents to `cn()` and its
own siblings, so a primitive can never depend on a page or a feature, and
stays swappable for the published package the day that exists. Shared
non-primitive components go in `src/components/` or beside the page that owns
them.

Its style is shadcn new-york-v4: `data-slot` attributes, `cva` variant tables,
`asChild`/`Slot.Root` through the `radix-ui` meta-package, and `lucide-react`
icons. `src/lib/hooks/use-mobile.ts` is its one supporting hook (`Sidebar`'s
`useIsMobile`), where `components.json`'s `hooks` alias points.
`components.json` is configured for the shadcn CLI (`src/index.css` as the token
source, `@/lib/utils` as `cn`, `@/components/ui` as the target), so
`npx shadcn@latest add <component>` adds a primitive this set lacks. If the
library is ever published as an npm dependency: add it to `dependencies`, delete
`src/components/ui/` and `src/lib/hooks/`, and repoint `@/components/ui` at the
package.

### Why there is no `src/types/`, `src/hooks/` or `src/utils/`

Those collect by _kind_ rather than _concern_, smearing a feature across
folders. Types live where they are owned; shared helpers and hooks go in
`src/lib/` (where `components.json` points the shadcn CLI's hooks).
`mb-no-top-level-catchall-dirs` enforces the ban, and `mb-src-layout-is-declared`
and its `-for-importers` twin fail any file outside `auth/`, `components/`,
`lib/`, `pages/`, `shell/` and the composition root — so a new folder under `src/` is
added to those rules and to this document in the same change.
`lb-api-modules-are-data` keeps `pages/*/*-api.ts` free of React and components.

## The `@/` alias

`@/x` resolves to `src/x`, declared in the places below, which must agree:

| File                           | Mechanism                              |
| ------------------------------ | -------------------------------------- |
| `tsconfig.json`                | `compilerOptions.paths`                |
| `vite.config.ts`               | `resolve.alias`                        |
| `vitest.config.ts`             | `resolve.alias`                        |
| `test/.dependency-cruiser.cjs` | `options.tsConfig` (reads the `paths`) |

Vitest does not inherit `vite.config.ts` (the unit suite deliberately skips
`@tailwindcss/vite`). Missing dependency-cruiser is the quiet failure: aliased
imports read as unresolvable and every rule silently stops matching them.

## Styling and the token pipeline

`src/index.css` is the whole Tailwind v4 configuration; `@tailwindcss/vite` is
the whole build-side setup:

1. `@import "tailwindcss"`.
2. `@custom-variant dark (&:is(.dark *))` — dark mode is a class on an ancestor.
3. `:root` and `.dark` hold the palette as raw `oklch()` — **the only place a
   raw colour value belongs.**
4. `@theme inline` maps each variable onto utilities (`--color-card` →
   `bg-card`, `text-card`, …).
5. `@layer base` applies the default border colour and the body's colours.

A new shade goes in all three of `:root`, `.dark` and `@theme inline` — a token
with no dark value is a bug only dark mode shows. `test/arch/designTokens.test.ts`
fails a colour literal anywhere else; Tailwind compiles `bg-[#0f172a]` happily.
Step 1 brings Tailwind's own palette with it — nothing resets `--color-*`, so
`bg-red-500`, `text-white` and `bg-black` also compile, render identically in
both modes and survive a rebrand untouched. The same test fails a utility
naming one, outside `src/components/ui/`, which arrived with the framework's
defaults in its overlays and thumbs and is exempt until the published package
replaces it.
The unit suite stubs CSS imports, so the E2E suite exercises the pipeline in a
browser: `smoke.spec.ts` asserts the body's computed `background-color`
resolved, and `packaged-type.spec.ts` the packaged bundle's type scale and
IBM Plex Sans 500 face. The one unit test that reads the pipeline is
`src/type-scale.test.ts`, which compiles `src/index.css` with Tailwind itself
and checks each type-scale utility against the values it states.

## Build

`vite build` emits one `vendor` chunk for `node_modules` and the app in `index`,
with `.gz` and `.br` siblings. There is **no PWA plugin and no service worker**.
Adding one means `vite-plugin-pwa`, an `entry` line in `.fallowrc.jsonc` for the
worker source (nothing imports it), and a `registerType` decision.

A directory a test run writes `.html` into belongs on `vite.config.ts`'s
`server.watch.ignored`, or a dev server you started full-reloads every open
page. The server Playwright starts for E2E (`E2E_DEV_SERVER=1`) watches nothing
and opens no HMR socket, so no page reloads under a spec.

## Routing

`App.tsx` owns the route table: `/login` behind `GuestRoute`, one layout route —
`ProtectedRoute` around `SignedInShell` — holding every signed-in page, and `*`
falling back to `/`.

**Each signed-in page is declared once**, as an entry in `SIGNED_IN_PAGES`
(`shell/signed-in-pages.tsx`): path, element, optional `permissions` guard,
optional `homeLink`, and the shell's metadata (the title, and whether it is kept
for screen readers only). `App.tsx` maps the table to routes, wrapping an entry
with `permissions` in its own `ProtectedRoute`; the shell finds the current
entry with `matchPath`, and its navigation sidebar links each entry with a
`homeLink` that `mayOpen` admits — beneath its collapsible group's button where
the entry names a `group` (Accounts and Audit are both in Admin), a group
standing where its first entry would and showing only while it links one.
**Adding a signed-in page is one entry there**, nothing in `App.tsx` or the
shell. (`BrowserRouter` has no route `handle` / `useMatches`, so the table is
the single source.)

`SignedInShell` owns the navigation sidebar (`shell/navigation-sidebar.tsx`,
which folds to its icons from its top-right control, is a sheet on a phone,
and holds Sign out at its foot), the top bar (`shell/top-bar.tsx`:
the patient in the tab's EHR context by name and NRIC, rendered from the
layout's one read of them (`PatientInContextScope`), which the Patient card
and the Demographic details render from too, then Notifications, the one link to Settings,
Help and who is signed in: the username, and beneath it the Groups the session
was signed in with), the page title (kept for screen readers only on every page the
sidebar or the top bar reaches, `titleHidden`) and the page's one error line (the page-status
scope); a page renders only its body. Settings (`SETTINGS_PATH`, `/settings`)
holds the password change, both the voluntary one and the one the
change-required flag requires: a flagged session is confined to Settings, in
its usual layout. A failed or CSRF-expired logout ends nothing — the
session and its token stand (ADR 0009) — and the line goes to the page status.
The idle warning's Sign out runs the same session action; the Idle sign-out at
the limit is a separate path, and ends the session even when its logout fails.

`resolveSessionRoute` is the transition table behind both guards. It sends a
Guest to login with a return destination and the provider's sign-in reason;
**confines a flagged session** (`passwordChangeRequired: true` on `/me`) to
Settings (`/settings`) from every path, ahead of the Permission check and any
return destination;
renders a Permission-guarded route for a session holding any of its
Permissions; and redirects every other session to its landing page
(`landingPath`: Patient Search for a session holding `patient:read`, Home
otherwise), as it does an authenticated visitor on `/login` with no return
destination and one on a path with no route (`LandingRoute`). The guards decide what is
_rendered_; Spring Security decides what is _permitted_.

`BrowserRouter` means real paths, so the backend serves `index.html` for any
unmatched path; a deep link 404s without that. Lazy route chunks are unused
until a page is heavy enough to earn one.

## What is deliberately absent

| Concern              | Where it goes                                                                        |
| -------------------- | ------------------------------------------------------------------------------------ |
| Global state         | beside the feature that owns it; hoist to `src/lib/` only when a second one needs it |
| Server-state caching | a query library wrapping `apiFetch`, wired in `App.tsx` beside `AuthProvider`        |
| Environment config   | `VITE_`-prefixed variables, read through `import.meta.env`, documented in README.md  |
| Lazy routes          | `src/App.tsx`, when a page is heavy                                                  |
| PWA / service worker | `vite-plugin-pwa` in `vite.config.ts` + a `.fallowrc.jsonc` `entry` line             |

The top-level error boundary (`src/components/error-boundary.tsx`) wraps the
whole tree in `App.tsx`, outside the shell. Its fallback offers a reload, logs
to `console.error` only, and never puts the error's message or stack in the DOM.
