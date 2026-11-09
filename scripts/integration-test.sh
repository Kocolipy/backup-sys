#!/usr/bin/env bash
# The whole Playwright suite, end to end, in one run: every project in
# frontend/playwright.config.ts, the packaged SPA's (`packaged`) among them.
# frontend/AGENTS.md carries its trigger and when it passes.
#
# Runs the scripts' own tests first (scripts/test), then packages the JAR with
# the SPA (scripts/package.sh), brings up SQL Server, Redis and the in-house
# patient source's mock (ADR 0014), which the JAR reads at backend/.env's
# APP_PATIENT_SOURCE_BASE_URL. It runs the JAR once, in the `local` profile, on
# SERVER_PORT (backend/.env's over the shell's; 8080 when neither sets it).
# Playwright starts the Vite dev server itself, which proxies /api to that JAR:
# every project but `packaged` runs through it, and `packaged` loads the JAR's
# own origin, where the built bundle runs under the backend's CSP, as the
# specs' direct SCIM and actuator calls reach it too. Everything this script
# started is torn down again unless KEEP_UP=1 (stack_up and stack_backend in
# lib.sh).
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"

require_node

# First and cheapest: how the scripts read the backend's port, which every
# request below depends on.
log "testing the scripts' backend port"
node --test "$ROOT_DIR"/scripts/test/*.test.mjs

"$ROOT_DIR/scripts/package.sh"
# -print -quit rather than `find ... | head -1`: see package.sh.
jar="$(find "$BACKEND_DIR/target" -maxdepth 1 -name 'backend-*.jar' ! -name '*-plain.jar' -print -quit)"
[[ -n $jar ]] || die "no backend-*.jar in backend/target after packaging"

stack_up "SQL Server, Redis and the patient source's mock"

# The `local` profile keeps the local run's in-process migrations, which every
# other profile turns off, and the development fixtures the specs sign in as.
export SPRING_PROFILES_ACTIVE=local

# The JAR's own origin, which declares the `packaged` project.
export E2E_PACKAGED_BASE_URL="http://localhost:$BACKEND_PORT"

stack_backend backend "$BACKEND_PORT" java -jar "$jar"

stack_e2e test:e2e
