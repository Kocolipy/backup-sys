import { readdirSync } from "node:fs";
import { createRequire } from "node:module";
import { join } from "node:path";

import { describe, expect, it } from "vitest";

import { readSources } from "./sources";

// Architecture rule `every_path_a_cruiser_rule_names_exists`
// (docs/TESTING_GUIDE.md, "Architecture tests"). Several rules in
// test/.dependency-cruiser.cjs are keyed on one file or folder by name —
// src/lib/http.ts, src/auth/session-transitions.ts, src/pages/patient-api.ts.
// Rename that file and the rule matches nothing: dependency-cruiser reports no
// violation, which reads exactly like a rule that holds. This holds every
// literal path a rule names to a file that exists, so a rename fails here until
// the rule follows it.
const CONFIG = "test/.dependency-cruiser.cjs";

/**
 * The literal paths a rule names because it forbids them: they exist nowhere,
 * and that is what the rule holds. Each is still checked to be one the config
 * names, so an entry here cannot outlive its rule.
 */
const MEANT_TO_MATCH_NOTHING = [
  // mb-no-top-level-catchall-dirs
  "^src/types/",
  "^src/hooks/",
  "^src/utils/",
  // no-direct-http-in-components: third-party HTTP clients, which the app does
  // not depend on (a transitive install of one is no import of it)
  "^node_modules/axios/",
  "^node_modules/ky/",
  "^node_modules/got/",
  "^node_modules/node-fetch/",
  "^node_modules/cross-fetch/",
  "^node_modules/superagent/",
];

interface Matcher {
  path?: string | string[];
  pathNot?: string | string[];
}

interface Rule {
  name: string;
  from: Matcher;
  to: Matcher;
}

const asList = (value: string | string[] | undefined): string[] =>
  value === undefined ? [] : typeof value === "string" ? [value] : value;

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === "object" && value !== null;

const isPatterns = (value: unknown): value is string | string[] | undefined =>
  value === undefined ||
  typeof value === "string" ||
  (Array.isArray(value) && value.every((item) => typeof item === "string"));

const isMatcher = (value: unknown): value is Matcher =>
  isRecord(value) && isPatterns(value.path) && isPatterns(value.pathNot);

/** The forbidden rules of a dependency-cruiser configuration, or a thrown error. */
function rulesOf(config: unknown): Rule[] {
  if (!isRecord(config) || !Array.isArray(config.forbidden)) {
    throw new Error(`${CONFIG} exports no forbidden rules`);
  }
  return config.forbidden.map((rule: unknown) => {
    if (
      !isRecord(rule) ||
      typeof rule.name !== "string" ||
      !isMatcher(rule.from) ||
      !isMatcher(rule.to)
    ) {
      throw new Error(`${CONFIG} has a rule this test cannot read: ${JSON.stringify(rule)}`);
    }
    return { name: rule.name, from: rule.from, to: rule.to };
  });
}

/** `pattern` with each alternation group `(a|b)` spelled out, one pattern per alternative. */
function expansions(pattern: string): string[] {
  const group = /\(([^()]*)\)/.exec(pattern);
  if (group === null) return [pattern];
  const before = pattern.slice(0, group.index);
  const after = pattern.slice(group.index + group[0].length);

  return group[1].split("|").flatMap((alternative) => expansions(before + alternative + after));
}

// A pattern that names one path: anchored, with nothing but path characters
// and escaped dots, optionally ending at `$`. `[^/]+`, `.*` and a `$1`
// back-reference are not literal, so a pattern carrying one is skipped.
const LITERAL = /^\^(?:[\w/-]|\\\.)+\$?$/;

interface Named {
  rule: string;
  literal: string;
}

/** Every literal path each rule names, in any of its matchers. */
function literalsOf(rules: Rule[]): Named[] {
  return rules.flatMap(({ name, from, to }) =>
    [from.path, from.pathNot, to.path, to.pathNot]
      .flatMap(asList)
      .flatMap(expansions)
      .filter((pattern) => LITERAL.test(pattern))
      .map((literal) => ({ rule: name, literal })),
  );
}

/** Each named literal no path in `paths` matches, as `rule: literal`. */
function missingIn(named: Named[], paths: string[]): string[] {
  return named
    .filter(({ literal }) => !MEANT_TO_MATCH_NOTHING.includes(literal))
    .filter(({ literal }) => {
      const pattern = new RegExp(literal);
      return !paths.some((path) => pattern.test(path));
    })
    .map(({ rule, literal }) => `${rule}: ${literal}`);
}

// createRequire from a real path: under vitest `import.meta.url` is a served
// URL, not a `file:` one (sources.ts says why).
const require = createRequire(join(process.cwd(), CONFIG));
const rules = rulesOf(require(join(process.cwd(), CONFIG)));
const named = literalsOf(rules);

// What a rule's path can match: every file under src/, and every installed
// package as the folder dependency-cruiser resolves an import of it into.
const paths = [
  ...readSources("src", [""]).map(({ path }) => path),
  ...readdirSync(join(process.cwd(), "node_modules")).map((name) => `node_modules/${name}/`),
];

describe("paths the dependency-cruiser rules name", () => {
  it("finds the literal paths to check, alternatives spelled out", () => {
    // Proves the reading works: the transport rule names src/lib/http.ts and
    // decode.ts as one alternation, and both must come out of it.
    expect(named).toContainEqual({
      rule: "lb-transport-has-no-react",
      literal: "^src/lib/http\\.ts$",
    });
    expect(named).toContainEqual({
      rule: "lb-transport-has-no-react",
      literal: "^src/lib/decode\\.ts$",
    });
  });

  it("reports a renamed file, and not a pattern or a path that exists", () => {
    // Proves the rule fires: a planted config names one file that is not
    // there, beside a wildcard pattern and a file that is.
    const planted = literalsOf([
      {
        name: "planted-rule",
        from: { path: "^src/lib/(http|htp)\\.ts$", pathNot: "\\.(test|spec)\\.[tj]sx?$" },
        to: { path: "^src/pages/[^/]+/[^/]+-api\\.ts$" },
      },
    ]);

    expect(missingIn(planted, paths)).toEqual(["planted-rule: ^src/lib/htp\\.ts$"]);
  });

  it.each(MEANT_TO_MATCH_NOTHING)("keeps %s as a path the config forbids", (literal) => {
    expect(
      named.map((entry) => entry.literal),
      `${literal} is exempt as a path a rule forbids, so a rule must still name it.`,
    ).toContain(literal);
  });

  it("has no every_path_a_cruiser_rule_names_exists violation", () => {
    const missing = missingIn(named, paths);

    const why =
      `Each path below is named by a rule in ${CONFIG} and matches no file, so the rule ` +
      "holds nothing. Move the rule with the file it names.\n";

    expect(missing, why + missing.join("\n")).toEqual([]);
  });
});
