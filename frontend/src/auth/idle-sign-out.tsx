/**
 * Signs a user out of the SPA after a period of inactivity.
 *
 * The backend ends an idle session by itself, but nothing tells the page: an
 * unattended Accounts view would stay on screen until the next request came
 * back `401`. This component closes that gap from the SPA's side.
 *
 * - **The limit is the backend's.** It is the session's own idle timeout, read
 *   from `GET /api/auth/me` (or the login response), never a constant of ours.
 * - **Only user input renews the backend's idle clock.** Input — pointer, key,
 *   touch, wheel and scroll — when the last renewal this tab knows of is older
 *   than the renewal throttle, sends `GET /api/auth/me` through
 *   `useSessionRequest`. No timer, poll or background request sends it, and a
 *   request does not count as activity, so an unattended page still signs out
 *   at the limit.
 * - **The clock counts from the last renewal this tab knows of**, stamped when
 *   its request went out, never from the last input: the warning and the
 *   sign-out never come later than the backend's own end of the session.
 * - **Every tab shares the renewals.** Every tab holds the same session, so a
 *   renewal is broadcast with its time over a `BroadcastChannel`, and another
 *   tab's renewal restarts this tab's clock and dismisses its warning. Input
 *   that renewed nothing is not shared.
 * - **A warning comes first.** Shortly before the limit an `alertdialog` offers
 *   to keep the session. Staying is a renewal; passive input while it is open
 *   neither dismisses it nor renews anything.
 * - **A renewal that fails** with a `401` takes the ordinary expiry path in
 *   `useSessionRequest`; any other failure leaves the clock where it was, and
 *   the next input tries again. A renewal never extends the absolute session
 *   lifetime.
 */

import { useCallback, useEffect, useRef, useState } from "react";

import { Alert, AlertDescription } from "@/components/ui/alert";
import {
  AlertDialog,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";

import { ME_PATH } from "./api";
import { useAuthState } from "./auth-context-value";
import { useSessionAction } from "./use-session-action";
import { useSessionRequest, type SessionRequest } from "./use-session-request";

/** How long before the limit the warning opens: about a minute, per the session contract. */
const WARNING_LEAD_MS = 60_000;

/**
 * How old the last renewal must be before input sends another, capped at a
 * quarter of the limit so a limit under two minutes still renews well before
 * its warning. It bounds how much sooner than the limit after their last input
 * an active User can be signed out.
 */
const RENEWAL_THROTTLE_MS = 60_000;

/** The channel every tab of this origin shares its renewals on. */
const RENEWAL_CHANNEL = "mb-session-renewal";

/** The user input that counts as activity. Captured, so scrolling an inner element counts too. */
const ACTIVITY_EVENTS = [
  "keydown",
  "pointerdown",
  "pointermove",
  "scroll",
  "touchstart",
  "wheel",
] as const;

/**
 * The idle clock, counted from the last renewal of the backend's idle clock
 * this tab knows of: when it runs out the session is signed out, and in the
 * last stretch before that it reports `warning`.
 *
 * `renew` sends the renewing request through the session seam and, once it
 * succeeds, restarts the clock from the moment it went out and tells the other
 * tabs.
 */
function useIdleClock(limitMs: number, onIdle: () => void, request: SessionRequest) {
  const [warning, setWarning] = useState(false);
  const renew = useRef<() => Promise<void>>(() => Promise.resolve());

  useEffect(() => {
    // A session with no idle bound never idles out on the backend either.
    if (!(limitMs > 0)) return;

    const warnAtMs = limitMs - Math.min(WARNING_LEAD_MS, limitMs / 2);
    const throttleMs = Math.min(RENEWAL_THROTTLE_MS, limitMs / 4);
    const channel =
      typeof BroadcastChannel === "undefined" ? null : new BroadcastChannel(RENEWAL_CHANNEL);
    // The session check or the Login this clock starts after was a request too.
    let lastRenewal = Date.now();
    let renewing = false;
    let warned = false;
    let stopped = false;
    let timer: ReturnType<typeof setTimeout> | undefined;

    // Measured against the wall clock rather than counted down, so a timer a
    // backgrounded or sleeping machine fired late still lands in the right phase.
    const check = () => {
      const idleMs = Date.now() - lastRenewal;
      if (idleMs >= limitMs) {
        onIdle();
        return;
      }
      warned = idleMs >= warnAtMs;
      setWarning(warned);
      timer = setTimeout(check, (warned ? limitMs : warnAtMs) - idleMs);
    };

    // A renewal no later than the last one known changes nothing, and none
    // stamped later than now is real.
    const renewed = (at: number) => {
      if (!(at > lastRenewal && at <= Date.now())) return;
      clearTimeout(timer);
      lastRenewal = at;
      check();
    };

    // Stamped as it goes out: the backend renews on arrival, never earlier,
    // so this clock never runs out later than the backend's.
    renew.current = async () => {
      const sentAt = Date.now();
      const result = await request(ME_PATH);
      if (stopped || result.kind !== "ok") return;
      renewed(sentAt);
      channel?.postMessage(sentAt);
    };

    // One renewal out at a time: input while it is out sends nothing more.
    const onInput = () => {
      if (warned || renewing || Date.now() - lastRenewal < throttleMs) return;
      renewing = true;
      void renew.current().finally(() => {
        renewing = false;
      });
    };

    if (channel) {
      channel.onmessage = ({ data }: MessageEvent<unknown>) => {
        if (typeof data === "number") renewed(data);
      };
    }
    for (const type of ACTIVITY_EVENTS) {
      document.addEventListener(type, onInput, { capture: true, passive: true });
    }
    check();

    return () => {
      stopped = true;
      clearTimeout(timer);
      channel?.close();
      for (const type of ACTIVITY_EVENTS) {
        document.removeEventListener(type, onInput, { capture: true });
      }
    };
  }, [limitMs, onIdle, request]);

  return { renew: () => renew.current(), warning };
}

export function IdleSignOut({ idleTimeoutSeconds }: { idleTimeoutSeconds: number }) {
  const { signOutForInactivity } = useAuthState();
  const request = useSessionRequest();
  const onIdle = useCallback(() => void signOutForInactivity(), [signOutForInactivity]);
  const { renew, warning } = useIdleClock(idleTimeoutSeconds * 1000, onIdle, request);
  const [staying, setStaying] = useState(false);

  /**
   * Keeps the session with one renewal. A session that turns out to have
   * ended already is expired by the request seam's one `401` rule; any other
   * failure leaves the warning up rather than claim the session was kept, and
   * the limit decides.
   */
  const stay = async () => {
    setStaying(true);
    await renew();
    setStaying(false);
  };

  /**
   * The User's own Sign out, the same session action as the shell's
   * (ADR-0009): a logout that may have left the backend's session standing
   * keeps it here too, and the dialog shows the line it hands back. A
   * `signed-out` one has already ended the session, which unmounts this
   * component. The limit's own sign-out is `onIdle`'s, which ends the session
   * whatever the logout came to.
   */
  const signOut = useSessionAction("logout");

  if (!warning) return null;
  return (
    <IdleWarning
      onSignOut={() => void signOut.run()}
      onStay={() => void stay()}
      signOutError={signOut.error}
      signingOut={signOut.pending}
      staying={staying}
    />
  );
}

function IdleWarning({
  onSignOut,
  onStay,
  signOutError,
  signingOut,
  staying,
}: {
  onSignOut: () => void;
  onStay: () => void;
  /** What a Sign out that left the session standing said, or `null`. */
  signOutError: string | null;
  signingOut: boolean;
  staying: boolean;
}) {
  return (
    <AlertDialog
      onOpenChange={(open) => {
        // Escape means "not now", which here means staying: the dialog closes only by a choice.
        if (!open) onStay();
      }}
      open
    >
      <AlertDialogContent
        onOpenAutoFocus={(event) => {
          // The dialog itself takes focus, not either action, so Tab reaches both evenly.
          event.preventDefault();
          (event.target as HTMLElement).focus();
        }}
      >
        <AlertDialogHeader>
          <AlertDialogTitle>Are you still there?</AlertDialogTitle>
          <AlertDialogDescription>
            You will be signed out soon because you have been inactive.
          </AlertDialogDescription>
        </AlertDialogHeader>
        {signOutError ? (
          <Alert variant="destructive">
            <AlertDescription>{signOutError}</AlertDescription>
          </Alert>
        ) : null}
        <AlertDialogFooter>
          <Button disabled={signingOut} onClick={onSignOut} type="button" variant="outline">
            {signingOut ? "Signing out…" : "Sign out"}
          </Button>
          <Button disabled={staying} onClick={onStay} type="button">
            {staying ? "Staying signed in…" : "Stay signed in"}
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
