import type { LucideIcon } from "lucide-react";
import {
  ClipboardCheck,
  ClipboardList,
  House,
  Pill,
  Plane,
  ScrollText,
  Search as SearchIcon,
  Settings,
  UserRound,
  Users,
  Waves,
} from "lucide-react";
import type { ReactNode } from "react";

import type { PageRequirement } from "@/auth/permissions";
import {
  DEMOGRAPHIC_PAGE_PATH,
  HOME_PATH,
  PATIENT_SEARCH_PAGE_PATH,
  SETTINGS_PATH,
} from "@/auth/session-route";
import { Accounts } from "@/pages/accounts/accounts";
import { ADMINISTRATION_PERMISSIONS } from "@/pages/accounts/accounts-api";
import { ArmcBoards } from "@/pages/armc/armc";
import { ARMC_HISTORY } from "@/pages/armc/armc-api";
import { Audit } from "@/pages/audit/audit";
import { AUDIT_EVENTS } from "@/pages/audit/audit-api";
import { ChangePassword } from "@/pages/change-password/change-password";
import { Demographic } from "@/pages/demographic/demographic";
import { Ffis } from "@/pages/ffi/ffi";
import { FFI_HISTORY } from "@/pages/ffi/ffi-api";
import { Home } from "@/pages/home/home";
import { HspRecords } from "@/pages/hsp/hsp";
import { HSP_HISTORY } from "@/pages/hsp/hsp-api";
import { Medications } from "@/pages/medication/medication";
import { MEDICATION_HISTORY } from "@/pages/medication/medication-api";
import { NumcRecords } from "@/pages/numc/numc";
import { NUMC_HISTORY } from "@/pages/numc/numc-api";
import { Search } from "@/pages/search/search";
import { PATIENT_SEARCH } from "@/pages/search/search-api";

/** A collapsible group of links in the navigation sidebar. */
export type SidebarGroupName = "Admin";

/** A signed-in page, and what the navigation sidebar reads to link it. */
export interface SignedInPage {
  element: ReactNode;
  /**
   * The collapsible sidebar group its link sits in, beneath the group's own
   * label; read only alongside `homeLink`. Omit to link it at the top level.
   */
  group?: SidebarGroupName;
  /** Its label in the navigation sidebar. Omit to leave it off the sidebar. */
  homeLink?: string;
  /** Its icon in the navigation sidebar; read only alongside `homeLink`. */
  icon: LucideIcon;
  path: string;
  /**
   * What its route guard requires of the session, which the sidebar reads to
   * link it only to a session the guard would open it to. Omit for every
   * signed-in session.
   */
  requires?: PageRequirement;
  title: string;
  /**
   * Keeps the title for screen readers only: `true` always, and
   * `"while-patient-in-context"` while the page has a patient in context to
   * show, whose banner heads the page — the title showing whenever there is
   * none (`isWithoutPatient`). A page the sidebar or the top bar reaches hides
   * it, the link marking where the user is, unless the page is headed by the
   * title alone, as Patient Search is. `"dropped-while-patient-in-context"`
   * renders no title at all while there is a patient in context, for a page
   * whose own heading is then its one `<h1>`, as the HSP page's is; with none
   * the title shows as for `"while-patient-in-context"`.
   */
  titleHidden?: boolean | "while-patient-in-context" | "dropped-while-patient-in-context";
}

/** Every signed-in page but Home, in the order the sidebar links them. */
const PAGES_BEYOND_HOME: readonly SignedInPage[] = [
  {
    element: <Accounts />,
    group: "Admin",
    homeLink: "Accounts",
    icon: Users,
    path: "/accounts",
    requires: { anyOf: ADMINISTRATION_PERMISSIONS },
    title: "Accounts",
    titleHidden: true,
  },
  {
    element: <Audit />,
    group: "Admin",
    homeLink: "Audit logs",
    icon: ScrollText,
    path: "/audit",
    requires: { anyOf: [AUDIT_EVENTS.permission] },
    title: "Audit",
    titleHidden: true,
  },
];

/**
 * Home: the landing page of a session without `patient:read`, and what the
 * shell falls back to for a path it does not know. Refused to a session
 * holding `patient:read`, which its guard sends on to its landing page, Patient
 * Search, so every session's landing page opens to it. Linked from no sidebar.
 * Its title heads the page.
 */
export const HOME_PAGE: SignedInPage = {
  element: <Home />,
  icon: House,
  path: HOME_PATH,
  requires: { noneOf: ["patient:read"] },
  title: "Home",
};

/**
 * The Demographic page: the patient in context's Patient card and their
 * Demographic details, which selecting a Patient on Patient Search opens.
 * Guarded by `patient:read`, the Permission the patient in context is read
 * with, named here rather than through `PATIENT`: only the one reader of the
 * patient in context imports `patient-api.ts` (`mb-patient-in-context-has-one-reader`).
 * Its title is hidden while a patient is in context, the patient banner
 * heading the page, and heads it when none is, as on the history pages.
 */
const DEMOGRAPHIC_PAGE: SignedInPage = {
  element: <Demographic />,
  homeLink: "Demographic",
  icon: UserRound,
  path: DEMOGRAPHIC_PAGE_PATH,
  requires: { anyOf: ["patient:read"] },
  title: "Demographic",
  titleHidden: "while-patient-in-context",
};

/**
 * Settings, which holds the password change; linked from the top bar rather
 * than the navigation sidebar. It holds both the voluntary change and the one
 * the change-required flag requires: a flagged session is confined to it.
 */
export const SETTINGS_PAGE: SignedInPage = {
  element: <ChangePassword />,
  icon: Settings,
  path: SETTINGS_PATH,
  title: "Settings",
  titleHidden: true,
};

/**
 * Patient Search: the landing page of a session holding `patient:read`, the
 * Permission its search reads with, and first in the sidebar for such a
 * session. Guarded by that Permission, so a session without it is routed to
 * its landing page and offered no link. Its title heads the page, with no
 * subtitle.
 */
const SEARCH_PAGE: SignedInPage = {
  element: <Search />,
  homeLink: "Search",
  icon: SearchIcon,
  path: PATIENT_SEARCH_PAGE_PATH,
  requires: { anyOf: [PATIENT_SEARCH.permission] },
  title: "Patient Search",
};

/**
 * Medication: the Medication History of the patient in context, linked after
 * Demographic and above FFI for a session holding `patient:read`, the Permission
 * its read requires, and guarded by it as Patient Search is, so a session
 * without it is routed to its landing page and offered no link. Its title
 * heads the page.
 */
const MEDICATION_PAGE: SignedInPage = {
  element: <Medications />,
  homeLink: "Medication",
  icon: Pill,
  path: "/medication",
  requires: { anyOf: [MEDICATION_HISTORY.permission] },
  title: "Medication",
};

/**
 * FFI: the FFI History of the patient in context, linked after Medication for
 * a session holding `patient:read`, the Permission its read requires, and
 * guarded by it as Patient Search is, so a session without it is routed to
 * its landing page and offered no link. Its title heads the page.
 */
const FFI_PAGE: SignedInPage = {
  element: <Ffis />,
  homeLink: "FFI",
  icon: ClipboardCheck,
  path: "/ffi",
  requires: { anyOf: [FFI_HISTORY.permission] },
  title: "FFI History",
};

/**
 * NUMC: the NUMC History of the patient in context, linked after FFI for a
 * session holding `patient:read`, the Permission its read requires, and
 * guarded by it as FFI is, so a session without it is routed to its landing
 * page and offered no link. Its title heads the page.
 */
const NUMC_PAGE: SignedInPage = {
  element: <NumcRecords />,
  homeLink: "NUMC",
  icon: Waves,
  path: "/numc",
  requires: { anyOf: [NUMC_HISTORY.permission] },
  title: "NUMC",
};

/**
 * ARMC: the ARMC History of the patient in context, linked after NUMC for a
 * session holding `patient:read`, the Permission its read requires, and
 * guarded by it as NUMC is, so a session without it is routed to its landing
 * page and offered no link. Its title heads the page.
 */
const ARMC_PAGE: SignedInPage = {
  element: <ArmcBoards />,
  homeLink: "ARMC",
  icon: Plane,
  path: "/armc",
  requires: { anyOf: [ARMC_HISTORY.permission] },
  title: "ARMC",
};

/**
 * HSP: the patient in context's Patient card above their HSP History, linked
 * after ARMC for a session holding `patient:read`, the Permission its read
 * requires, and guarded by it as ARMC is, so a session without it is routed to
 * its landing page and offered no link. Its title is dropped while a patient is
 * in context, the page's own "HSP History" card heading its `<h1>` beneath the
 * patient banner, so the heading is never announced twice.
 */
const HSP_PAGE: SignedInPage = {
  element: <HspRecords />,
  homeLink: "HSP",
  icon: ClipboardList,
  path: "/hsp",
  requires: { anyOf: [HSP_HISTORY.permission] },
  title: "HSP History",
  titleHidden: "dropped-while-patient-in-context",
};

/**
 * Every signed-in page, declared once: `App` turns each entry into a route
 * (behind the guard its `requires` states, if any), `SignedInShell` reads the matching
 * entry for the title, and links each entry
 * that carries a `homeLink` in the navigation sidebar. A new signed-in page
 * is one entry here, and the sidebar links them in this order.
 */
export const SIGNED_IN_PAGES: readonly SignedInPage[] = [
  SEARCH_PAGE,
  DEMOGRAPHIC_PAGE,
  HOME_PAGE,
  MEDICATION_PAGE,
  FFI_PAGE,
  NUMC_PAGE,
  ARMC_PAGE,
  HSP_PAGE,
  ...PAGES_BEYOND_HOME,
  SETTINGS_PAGE,
];
