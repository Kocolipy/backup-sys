#!/bin/bash
# Ships an already-built integrated JAR to a stack's instance, runs the migrate
# step and starts the service (lib.sh, ship_jar): the JAR goes to the stack's
# artifact bucket, and the instance fetches it from there. Build the JAR first,
# from the repo root: make package.
#
# Usage: ./deploy-jar.sh <stack-name> [region] [jar]
#   jar  default: the backend-*.jar in ../backend/target
#
# Never run with command tracing (bash -x): see lib.sh.
set -euo pipefail

# shellcheck source=lib.sh source-path=SCRIPTDIR
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"

main() {
  local stack=${1:-} region=${2:-ap-southeast-1} jar=${3:-} cmd
  [[ -n $stack ]] || die "usage: $0 <stack-name> [region] [jar]"
  for cmd in aws jq openssl base64; do require_cmd "$cmd"; done
  if [[ -z $jar ]]; then
    jar=$(find "$INFRA_DIR/../backend/target" -maxdepth 1 -name 'backend-*.jar' ! -name '*-plain.jar' -print -quit 2>/dev/null)
    [[ -n $jar ]] || die "no backend-*.jar in backend/target: build it from the repo root with 'make package'"
  fi
  [[ -r $jar ]] || die "$jar is not readable"
  load_stack_outputs "$stack" "$region"
  ship_jar "$stack" "$region" "$jar" || die "the JAR was not deployed: the instance's output is above"
  print_info "$jar is deployed and the service started; check it with ./smoke.sh $stack $region"
}

main "$@"
