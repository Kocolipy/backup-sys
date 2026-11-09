// The backend's port as the root scripts read it:
// SERVER_PORT, which application.yaml's `server.port` reads too, and 8080 when
// it is unset or blank. `make integration-test` runs this file first; nothing
// here starts a server or binds a port.
//
//   node --test scripts/test/*.test.mjs
import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { describe, it } from "node:test";
import { fileURLToPath } from "node:url";

const LIB = fileURLToPath(new URL("../lib.sh", import.meta.url));

/** An environment holding PATH, HOME and `extra` alone, so the caller's SERVER_PORT never leaks in. */
function isolatedEnv(extra) {
  return { PATH: process.env.PATH, HOME: process.env.HOME, ...extra };
}

/** What lib.sh's backend_port prints under `extra`. */
function backendPort(extra) {
  return execFileSync("bash", ["-c", 'source "$0" && backend_port', LIB], {
    env: isolatedEnv(extra),
    encoding: "utf8",
  }).trim();
}

describe("the scripts' backend port", () => {
  it("is 8080 when SERVER_PORT is absent", () => {
    assert.equal(backendPort({}), "8080");
  });

  it("is 8080 when SERVER_PORT is blank", () => {
    assert.equal(backendPort({ SERVER_PORT: "" }), "8080");
  });

  it("is the port SERVER_PORT names", () => {
    assert.equal(backendPort({ SERVER_PORT: "8091" }), "8091");
  });
});
