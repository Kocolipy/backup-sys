import type { LucideIcon } from "lucide-react";
import {
  IdCard,
  LayoutTemplate,
  ScrollText,
  Search as SearchIcon,
  Settings,
  Users,
} from "lucide-react";
import type { ReactNode } from "react";

import type { Permission } from "@/auth/api";
import { HOME_PATH, PATIENT_SEARCH_PAGE_PATH, SETTINGS_PATH } from "@/auth/session-route";
import { Accounts } from "@/pages/accounts/accounts";
import { ADMINISTRATION_PERMISSIONS } from "@/pages/accounts/accounts-api";
import { Audit } from "@/pages/audit/audit";
import { BannerPrototype } from "@/pages/banner-prototype/banner-prototype";
import { AUDIT_EVENTS } from "@/pages/audit/audit-api";
import { ChangePassword } from "@/pages/change-password/change-password";
import { Home } from "@/pages/home/home";
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
  /** Renders in the sidebar only for a session holding at least one of these. Omit for every signed-in session. */
  permissions?: readonly Permission[];
  title: string;
  /**
   * Keeps the title for screen readers only. A page the sidebar or the top
   * bar reaches hides it, the link marking where the user is, unless the page
   * is headed by the title alone, as Patient Search is.
   */
  titleHidden?: boolean;
}

/** Every signed-in page but Home, in the order the sidebar links them. */
const PAGES_BEYOND_HOME: readonly SignedInPage[] = [
  {
    element: <Accounts />,
    group: "Admin",
    homeLink: "Accounts",
    icon: Users,
    path: "/accounts",
    permissions: ADMINISTRATION_PERMISSIONS,
    title: "Accounts",
    titleHidden: true,
  },
  {
    element: <BannerPrototype />,
    homeLink: "Banner prototype",
    icon: LayoutTemplate,
    path: "/prototype/banner",
    title: "Patient banner prototype",
  },
  {
    element: <Audit />,
    group: "Admin",
    homeLink: "Audit logs",
    icon: ScrollText,
    path: "/audit",
    permissions: [AUDIT_EVENTS.permission],
    title: "Audit",
    titleHidden: true,
  },
];

/**
 * Home: the landing page of a session without `patient:read`, and what the
 * shell falls back to for a path it does not know. Unguarded, so every
 * signed-in session may open it. Its title is hidden: the patient banner
 * heads the page.
 */
export const HOME_PAGE: SignedInPage = {
  element: <Home />,
  homeLink: "Profile",
  icon: IdCard,
  path: HOME_PATH,
  title: "Profile",
  titleHidden: true,
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
  permissions: [PATIENT_SEARCH.permission],
  title: "Patient Search",
};

/**
 * Every signed-in page, declared once: `App` turns each entry into a route
 * (behind its Permission guard, if any), `SignedInShell` reads the matching
 * entry for the title, and links each entry
 * that carries a `homeLink` in the navigation sidebar. A new signed-in page
 * is one entry here, and the sidebar links them in this order.
 */
export const SIGNED_IN_PAGES: readonly SignedInPage[] = [
  SEARCH_PAGE,
  HOME_PAGE,
  ...PAGES_BEYOND_HOME,
  SETTINGS_PAGE,
];
