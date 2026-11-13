import { describe, expect, it } from "vitest";

import { blankComments, readSources, violationsIn } from "./sources";

// Architecture rule `no_web_storage_outside_its_keeper` (docs/BACKEND_CONTRACT.md,
// the EHR context). The SPA keeps exactly one thing in the browser, per tab in
// `sessionStorage`: the EHR context. Its keeper decodes what it reads back and
// clears it on every session end; the CSRF token is never kept at all
// (src/lib/http.ts). `localStorage` and `sessionStorage` are globals, so a page
// that writes to them imports nothing, and dependency-cruiser never sees it.
const EHR_CONTEXT = "src/auth/ehr-context.ts";

/** Every module allowed to touch browser storage, and nothing else. */
const STORAGE_MODULES = [EHR_CONTEXT];

const WEB_STORAGE = /\b(localStorage|sessionStorage|indexedDB)\b/;

const sources = readSources("src", [".ts", ".tsx"]).filter(
  ({ path }) => !/\.(test|spec|testHelpers)\.[tj]sx?$/.test(path),
);
const files = sources
  .filter(({ path }) => !STORAGE_MODULES.includes(path))
  .map(({ path, code }) => ({ path, code: blankComments(code) }));

describe("browser storage", () => {
  it("finds the sources to check", () => {
    expect(files.length).toBeGreaterThan(0);
  });

  it.each(STORAGE_MODULES)("keeps %s as a storage module", (path) => {
    // Guards the exemption: if the module moves and its entry is not moved
    // with it, the exemption would cover a file that no longer keeps anything.
    const module = sources.find((source) => source.path === path);

    expect(module, `${path} is an exempt storage module.`).toBeDefined();
    expect(WEB_STORAGE.test(blankComments(module?.code ?? ""))).toBe(true);
  });

  it("reports each kind of storage reached outside its keeper, and not in a comment", () => {
    // One planted line per storage the pattern names, so dropping any one of
    // them from WEB_STORAGE fails here.
    const planted = [
      { path: "src/pages/planted-local.tsx", code: 'window.localStorage.setItem("token", value);' },
      { path: "src/pages/planted-session.tsx", code: 'sessionStorage.setItem("token", value);' },
      { path: "src/pages/planted-indexed.tsx", code: 'indexedDB.open("cache");' },
      {
        path: "src/pages/planted-comment.tsx",
        code: blankComments("// sessionStorage is not ours"),
      },
    ];

    expect(violationsIn(planted, WEB_STORAGE)).toEqual([
      'src/pages/planted-local.tsx:1: window.localStorage.setItem("token", value);',
      'src/pages/planted-session.tsx:1: sessionStorage.setItem("token", value);',
      'src/pages/planted-indexed.tsx:1: indexedDB.open("cache");',
    ]);
  });

  it("has no no_web_storage_outside_its_keeper violation", () => {
    const violations = violationsIn(files, WEB_STORAGE);

    const why =
      `The tab keeps the EHR context (${EHR_CONTEXT}), and nothing else: its keeper decodes ` +
      "what it reads back and clears it when the session ends. Anything new the tab keeps " +
      "gets a keeper of its own, " +
      "added here and to docs/BACKEND_CONTRACT.md together.\n";

    expect(violations, why + violations.join("\n")).toEqual([]);
  });
});
