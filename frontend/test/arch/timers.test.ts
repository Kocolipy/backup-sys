import { describe, expect, it } from "vitest";

import { blankComments, readSources, violationsIn } from "./sources";

// Architecture rule `no_timer_outside_idle_sign_out` (docs/ARCHITECTURE.md,
// "Why nothing reads on a timer"). Every request renews the server session, so
// a page that read on a timer would keep the session alive for as long as the
// tab stays open and the idle timeout would never come. A timer is a global
// call that imports nothing, so dependency-cruiser never sees it, and a unit
// test sees it only where it happens to advance the clock.
/** Every module allowed a timer, each with what the timer is for. */
const TIMER_MODULES: Record<string, string> = {
  "src/auth/idle-sign-out.tsx":
    "the idle warning and the Idle sign-out: it times the session's own window and sends nothing on a schedule",
};

// The browser's scheduling entry points, bare or reached through the global
// object they hang off (`window.setTimeout(f)` is the same global). The
// lookbehind keeps a method of the same name on another object out, and the
// `\s*\(` a type reference (`ReturnType<typeof setTimeout>`) that schedules
// nothing.
const TIMER =
  /(?:(?<![.\w])|(?<=\b(?:window|globalThis|self)\.))(?:setTimeout|setInterval|requestAnimationFrame|requestIdleCallback)\s*\(/;

const sources = readSources("src", [".ts", ".tsx"]).filter(
  ({ path }) => !/\.(test|spec|testHelpers)\.[tj]sx?$/.test(path),
);
const files = sources
  .filter(({ path }) => !(path in TIMER_MODULES))
  .map(({ path, code }) => ({ path, code: blankComments(code) }));

describe("timers", () => {
  it("finds the sources to check", () => {
    expect(files.length).toBeGreaterThan(0);
  });

  it.each(Object.keys(TIMER_MODULES))("keeps %s as a timer module", (path) => {
    // Guards the exemption: if the module moves and its entry is not moved
    // with it, the exemption would cover a file that no longer sets a timer.
    const module = sources.find((source) => source.path === path);

    expect(module, `${path} is an exempt timer module.`).toBeDefined();
    expect(TIMER.test(blankComments(module?.code ?? ""))).toBe(true);
  });

  it("reports every way to schedule, and not a method, a type or a comment", () => {
    // Proves the rule fires: one planted line per entry point and spelling,
    // plus the four that must stay out.
    const planted = [
      { path: "src/pages/planted-timeout.tsx", code: "setTimeout(read, 1000);" },
      { path: "src/pages/planted-interval.tsx", code: "const id = setInterval(read, 30_000);" },
      { path: "src/pages/planted-window.tsx", code: "window.setInterval(read, 5000);" },
      { path: "src/pages/planted-global.tsx", code: "globalThis.setTimeout(read);" },
      { path: "src/pages/planted-frame.tsx", code: "requestAnimationFrame(tick);" },
      { path: "src/pages/planted-idle.tsx", code: "requestIdleCallback(read);" },
      { path: "src/pages/planted-method.tsx", code: "scheduler.setTimeout(read);" },
      { path: "src/pages/planted-clear.tsx", code: "clearTimeout(timer);" },
      {
        path: "src/pages/planted-type.tsx",
        code: "let timer: ReturnType<typeof setTimeout>;",
      },
      {
        path: "src/pages/planted-comment.tsx",
        code: blankComments("// setInterval(read) is not ours"),
      },
    ];

    expect(violationsIn(planted, TIMER)).toEqual([
      "src/pages/planted-timeout.tsx:1: setTimeout(read, 1000);",
      "src/pages/planted-interval.tsx:1: const id = setInterval(read, 30_000);",
      "src/pages/planted-window.tsx:1: window.setInterval(read, 5000);",
      "src/pages/planted-global.tsx:1: globalThis.setTimeout(read);",
      "src/pages/planted-frame.tsx:1: requestAnimationFrame(tick);",
      "src/pages/planted-idle.tsx:1: requestIdleCallback(read);",
    ]);
  });

  it("has no no_timer_outside_idle_sign_out violation", () => {
    const violations = violationsIn(files, TIMER);

    const why =
      "A page reads on mount and on the user's action, never on a timer: every request " +
      "renews the server session, so a schedule would hold off the idle timeout for as long " +
      "as the tab is open. A timer that sends nothing joins TIMER_MODULES with its reason.\n";

    expect(violations, why + violations.join("\n")).toEqual([]);
  });
});
