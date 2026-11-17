import { matchPath, Outlet, useLocation } from "react-router-dom";

import { useAuth } from "@/auth/auth-context-value";
import { PageStatusScope, useStatusReporter } from "@/auth/page-status";
import { useSessionAction } from "@/auth/use-session-action";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { SidebarInset, SidebarProvider } from "@/components/ui/sidebar";
import { cn } from "@/lib/utils";
import {
  isWithoutPatient,
  PatientInContextScope,
  usePatientInContext,
} from "@/pages/patient-in-context";
import { NavigationSidebar } from "@/shell/navigation-sidebar";
import { HOME_PAGE, SIGNED_IN_PAGES, type SignedInPage } from "@/shell/signed-in-pages";
import { TopBar } from "@/shell/top-bar";

/**
 * The layout route of every signed-in page: the navigation sidebar
 * (`navigation-sidebar.tsx`, which folds to its icons and holds Sign out), the
 * top bar (the patient in context, who is signed in), the page title and the
 * page's one error line.
 *
 * The layout owns the page status (`page-status.ts`) and renders its line, the
 * only error line a signed-in page has. The page, its panels and Sign out each
 * report to it and none draws a line of its own; the latest failure shows, and
 * the line starts empty on every path, so a failure on one page never follows
 * the user to the next.
 *
 * A Sign out that fails, or comes back CSRF-expired, leaves the session
 * standing — the auth context's `logout` resolves that outcome without ending
 * anything, so the CSRF token stays with its session (ADR-0009) — and its
 * session action reports the line that says so to the page status. A
 * `signed-out` one has already ended the session, and the guards send the user
 * to login with no Sign-in reason. The Idle sign-out is a separate path that
 * always ends it.
 *
 * The layout reads the patient in context once (`PatientInContextScope`),
 * inside the page status its failure reports to: the top bar, the page title
 * and every page showing the patient render from that one read, and none
 * reads the patient itself.
 */
export function SignedInShell() {
  const { pathname } = useLocation();
  return (
    <PageStatusScope resetKey={pathname}>
      {(error) => (
        <PatientInContextScope>
          <SignedInLayout error={error} pathname={pathname} />
        </PatientInContextScope>
      )}
    </PageStatusScope>
  );
}

/**
 * The page's one title, in the headline type, kept for screen readers only as
 * its `titleHidden` says — for a page the patient banner heads, only while
 * there is a patient in context to show.
 */
function PageTitle({ page }: { page: SignedInPage }) {
  const view = usePatientInContext();
  const hidden =
    page.titleHidden === "while-patient-in-context" ? !isWithoutPatient(view) : page.titleHidden;
  return (
    <h1 className={cn("font-headline text-headline-lg", hidden && "sr-only")}>{page.title}</h1>
  );
}

/** The layout inside the page status, so Sign out reports to it like the page does. */
function SignedInLayout({ error, pathname }: { error: string | null; pathname: string }) {
  const { user } = useAuth();
  const signOut = useSessionAction("logout", useStatusReporter());
  const page = SIGNED_IN_PAGES.find((entry) => matchPath(entry.path, pathname)) ?? HOME_PAGE;

  return (
    <SidebarProvider>
      <NavigationSidebar
        onSignOut={() => void signOut.run()}
        pathname={pathname}
        signingOut={signOut.pending}
      />
      <div className="flex min-h-svh min-w-0 flex-1 flex-col">
        <TopBar user={user} />
        <SidebarInset className="flex flex-1 flex-col gap-6 p-8">
          <PageTitle page={page} />
          {error ? (
            <Alert className="w-full" variant="destructive">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          ) : null}

          <Outlet />
        </SidebarInset>
      </div>
    </SidebarProvider>
  );
}
