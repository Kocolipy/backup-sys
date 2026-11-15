# shellcheck shell=bash
# Shared by deploy.sh, db-bootstrap.sh and smoke.sh. Sourced, never run.
#
# Secret discipline (ADR 0013, "The bootstrap does not echo"; IM8 as-8). Every
# script in this directory:
#   - runs without command tracing: `set -x` prints every expanded secret;
#   - keeps a secret value in a shell variable only. It reaches a process on a
#     pipe, through a process substitution (a /dev/fd path) or in the
#     environment of that one process, never as a command-line argument (which
#     `ps` and /proc show every local user) and never in a file;
#   - names a secret in its messages, never prints its value.
# `printf` is a bash builtin, so `printf '%s' "$secret" | cmd` puts the value on
# cmd's stdin without it appearing in any argv.

set +x

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

print_info() { printf '%b[INFO]%b %s\n' "$GREEN" "$NC" "$*"; }
print_warn() { printf '%b[WARN]%b %s\n' "$YELLOW" "$NC" "$*"; }
print_error() { printf '%b[ERROR]%b %s\n' "$RED" "$NC" "$*" >&2; }
die() {
  print_error "$*"
  exit 1
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "$1 is required${2:+: $2}"
}

# --- Stack outputs -----------------------------------------------------------

STACK_OUTPUTS=""

# load_stack_outputs <stack> <region>
load_stack_outputs() {
  STACK_OUTPUTS=$(aws cloudformation describe-stacks --stack-name "$1" --region "$2" \
    --query 'Stacks[0].Outputs' --output json) || die "cannot read the outputs of stack $1"
}

# stack_output <key>: one output of the stack load_stack_outputs read.
stack_output() {
  printf '%s' "$STACK_OUTPUTS" |
    jq -er --arg k "$1" '.[] | select(.OutputKey == $k) | .OutputValue' ||
    die "stack output $1 is missing; was the stack created from this template?"
}

# --- Secrets -----------------------------------------------------------------

# secret_string <secret-arn> <region> [version-stage]: the SecretString, on stdout.
secret_string() {
  aws secretsmanager get-secret-value --secret-id "$1" --region "$2" \
    ${3:+--version-stage "$3"} --query SecretString --output text
}

# json_field <json> <field>: a string field of a secret's JSON, failing when absent.
json_field() {
  printf '%s' "$1" | jq -er --arg f "$2" '.[$f] | strings'
}

# --- The instance --------------------------------------------------------------
#
# The instance has no key pair and no inbound port 22. Everything here reaches
# it through Systems Manager Run Command, which its role allows
# (AmazonSSMManagedInstanceCore) and the operator's credentials invoke
# (ssm:SendCommand, ssm:GetCommandInvocation, ssm:DescribeInstanceInformation).
#
# The database is in private subnets and is reached from the instance only:
# the database bootstrap and the TLS check run there, as root, with the
# instance role's access to the secrets they need, so no database credential
# is ever sent to the instance from here.
#
# Run Command keeps each command's text, and the start of its output, in the
# account's command history. So a script sent to the instance holds no secret
# value, prints none, and prints little: a check that compares secret values
# with the instance's files runs on the instance and prints its verdict alone.
INFRA_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# How long remote waits for one script to finish on the instance, in seconds.
REMOTE_TIMEOUT=${REMOTE_TIMEOUT:-1800}

# remote [script]: runs a bash script on the instance as root, from the outputs
# load_stack_outputs read. The script is the argument, or stdin when there is
# none. Prints the script's stdout on stdout and its stderr on stderr, and
# returns its exit status. Run Command returns the first 24,000 characters of
# stdout and 8,000 of stderr.
remote() {
  local script instance region params id invocation status code out err waited=0
  if (($# > 0)); then script=$1; else script=$(cat); fi
  instance=$(stack_output EC2InstanceId)
  region=$(stack_output Region)
  # The script travels base64-encoded, so no quoting of its own is disturbed.
  params=$(jq -nc --arg s "$(printf '%s' "$script" | base64 | tr -d '\n')" \
    '{commands: ["printf %s \($s) | base64 -d | bash -s"]}')
  if ! id=$(aws ssm send-command --instance-ids "$instance" --document-name AWS-RunShellScript \
    --comment "infra scripts" --parameters "$params" --region "$region" \
    --query Command.CommandId --output text); then
    print_error "Systems Manager did not accept a command for instance $instance"
    return 255
  fi
  status=Pending
  while [[ $status == Pending || $status == InProgress || $status == Delayed || $status == Cancelling ]]; do
    if ((waited >= REMOTE_TIMEOUT)); then
      print_error "command $id did not finish on instance $instance within $REMOTE_TIMEOUT s"
      return 255
    fi
    sleep 3
    waited=$((waited + 3))
    # The invocation is not there for a moment after send-command.
    if invocation=$(aws ssm get-command-invocation --command-id "$id" --instance-id "$instance" \
      --region "$region" --output json 2>/dev/null); then
      status=$(jq -r .Status <<<"$invocation")
    fi
  done
  out=$(jq -r '.StandardOutputContent // empty' <<<"$invocation")
  err=$(jq -r '.StandardErrorContent // empty' <<<"$invocation")
  [[ -z $out ]] || printf '%s\n' "$out"
  [[ -z $err ]] || printf '%s\n' "$err" >&2
  [[ $status != Success ]] || return 0
  code=$(jq -r .ResponseCode <<<"$invocation")
  ((code > 0 && code < 256)) || code=1
  return "$code"
}

# wait_for_boot: waits until the instance is managed by Systems Manager and its
# UserData has finished, and fails when it failed (the database bootstrap
# included), showing the end of its log. It polls with short commands: UserData
# updates the instance's packages, which can restart the agent under a long one.
wait_for_boot() {
  local instance region waited=0 state
  instance=$(stack_output EC2InstanceId)
  region=$(stack_output Region)
  until [[ $(aws ssm describe-instance-information --region "$region" \
    --filters "Key=InstanceIds,Values=$instance" \
    --query 'InstanceInformationList[0].PingStatus' --output text 2>/dev/null) == Online ]]; do
    ((waited < 600)) ||
      die "instance $instance did not register with Systems Manager within 600 s (it needs its role's AmazonSSMManagedInstanceCore and a route to the Systems Manager endpoints)"
    sleep 10
    waited=$((waited + 10))
  done
  waited=0
  while :; do
    state=$(remote 2>/dev/null <<'SCRIPT' || true
if [[ -f /var/log/user-data-complete.log ]]; then
  echo complete
elif cloud-init status 2>/dev/null | grep -Eq '^status: (done|error|degraded)'; then
  echo failed
else
  echo running
fi
SCRIPT
    )
    case $state in
    complete) break ;;
    failed)
      remote 'tail -n 40 /var/log/cloud-init-output.log' || true
      die "the instance's boot (UserData) failed; the end of its log is above"
      ;;
    esac
    ((waited < 1800)) || die "the instance's boot (UserData) did not finish within 1800 s"
    sleep 15
    waited=$((waited + 15))
  done
  print_info "the instance has booted, and its database bootstrap has run"
}

# rerun_db_bootstrap: runs the database bootstrap again on the instance (its
# unit, backend-db-bootstrap.service) and prints its output, which holds no
# secret. Sets DB_BOOTSTRAP_CHANGES to the number of changes the run made: 0
# is a no-op.
DB_BOOTSTRAP_CHANGES=""
rerun_db_bootstrap() {
  local out
  if ! out=$(remote <<'SCRIPT'
since=$(date +%s)
systemctl start backend-db-bootstrap.service
status=$?
journalctl -u backend-db-bootstrap.service --no-pager -o cat --since "@$since"
exit "$status"
SCRIPT
  ); then
    printf '%s\n' "$out"
    die "the database bootstrap failed; its output is above"
  fi
  printf '%s\n' "$out"
  # shellcheck disable=SC2034 # read by db-bootstrap.sh, which sources this file
  DB_BOOTSTRAP_CHANGES=$(grep -c 'db-init: change:' <<<"$out" || true)
}

# ship_jar <stack> <region> <jar>: puts <jar> in the stack's artifact bucket, under the
# prefix the instance's role may read, and has the instance fetch it, check its
# SHA-256, install it root-owned, run the migrate step and start the service.
#
# The migrate step runs first — after the database bootstrap, which its unit
# requires — as the owner login, against the new JAR; the service is stopped
# while it runs and is started only once it succeeded. A failed migration
# returns non-zero with the service still stopped, so a schema the new JAR does
# not match is never served (infra/docs/database-boot.md, "Migrate step").
ship_jar() {
  local stack=$1 region=$2 jar=$3 bucket key digest
  bucket=$(stack_output ArtifactBucket)
  key="$stack/jar/backend-$(date +%Y%m%d%H%M%S).jar"
  digest=$(openssl dgst -sha256 -r "$jar" | cut -d' ' -f1)
  aws s3 cp "$jar" "s3://$bucket/$key" --region "$region" --only-show-errors ||
    die "cannot upload $jar to s3://$bucket/$key"
  print_info "JAR staged at s3://$bucket/$key; the instance fetches it from there"
  remote <<SCRIPT
set -euo pipefail
new=\$(mktemp /opt/backend/backend.jar.XXXXXX)
trap 'rm -f "\$new"' EXIT
aws s3 cp 's3://$bucket/$key' "\$new" --region '$region' --only-show-errors
echo '$digest  '"\$new" | sha256sum -c --quiet
chown root:root "\$new"
chmod 0644 "\$new"
systemctl stop backend
mv -f "\$new" /opt/backend/backend.jar
if ! systemctl restart backend-migrate; then
  journalctl -u backend-db-bootstrap -u backend-migrate -n 50 --no-pager || true
  echo "the migrate step failed; the service was not started" >&2
  exit 1
fi
systemctl start backend
systemctl enable backend
sleep 5
systemctl status backend --no-pager
SCRIPT
}

# db_tls_check_script: the sqlcmd half of the TLS check, which
# check_tls_enforced runs on the instance as root. As the RDS master user, read
# from Secrets Manager there by root with the instance's own helper
# (/opt/backend/secret.sh), as the database bootstrap reads it (only a login
# with VIEW SERVER STATE may read its own session's encrypt_option):
#   1. a client that asks for no encryption (sqlcmd -No) still gets an
#      encrypted session: the server forces encryption (rds.force_ssl);
#   2. a client that cannot validate the server's certificate is refused:
#      with the RDS CA bundle withheld, sqlcmd -N fails verification;
#   3. sqlcmd -N with the bundle connects, encrypted.
# Never -C. The password is in sqlcmd's environment only. The other half, a
# client that cannot do TLS at all, is tds-plaintext-probe.py.
db_tls_check_script() {
  cat <<'SCRIPT'
set -euo pipefail
set -a
# shellcheck source=/dev/null # the instance's files
. /opt/backend/db-bootstrap.env
set +a
# shellcheck source=/dev/null
. /opt/backend/secret.sh
login=$(secret "$DB_MASTER_SECRET_ARN" username)
password=$(secret "$DB_MASTER_SECRET_ARN" password)
encrypted='SET NOCOUNT ON; SELECT encrypt_option FROM sys.dm_exec_connections WHERE session_id = @@SPID'
as_master() {
  SQLCMDPASSWORD="$password" /opt/mssql-tools18/bin/sqlcmd -S "tcp:$DB_HOST,$DB_PORT" -U "$login" \
    -b -h -1 -W -l 15 "$@"
}
out=$(SSL_CERT_FILE="$DB_CA_FILE" as_master -No -Q "$encrypted")
[[ $out == TRUE ]] || { echo "a session that asked for no encryption is not encrypted ($out)"; exit 1; }
echo "a session that asked for no encryption was encrypted by the server"
if out=$(SSL_CERT_FILE=/dev/null as_master -N -Q 'SELECT 1' 2>&1); then
  echo "a session that could not validate the server's certificate was ACCEPTED"
  exit 1
fi
[[ $out == *"certificate verify failed"* ]] || { echo "that session failed, but not on its certificate: $out"; exit 1; }
echo "a session that cannot validate the server's certificate is refused"
out=$(SSL_CERT_FILE="$DB_CA_FILE" as_master -N -Q "$encrypted")
[[ $out == TRUE ]] || { echo "the validated session is not encrypted ($out)"; exit 1; }
echo "a session validating the certificate against the RDS CA bundle is encrypted"
SCRIPT
}

# check_tls_enforced <region>: the instance's parameter group forces
# encryption and turns TLS 1.0 and 1.1 off, and is applied; then, on the
# instance, a client that cannot do TLS is refused (tds-plaintext-probe.py,
# which sends no credential), and the sqlcmd checks above pass.
check_tls_enforced() {
  local region=$1 group params status out line
  group=$(stack_output DBParameterGroupName)
  # shellcheck disable=SC2016 # the backticks are JMESPath literals
  params=$(aws rds describe-db-parameters --db-parameter-group-name "$group" --region "$region" \
    --query 'Parameters[?ParameterName==`rds.force_ssl` || ParameterName==`rds.tls10` || ParameterName==`rds.tls11`].[ParameterName,ParameterValue]' \
    --output text | sort | awk '{ printf "%s%s=%s", (NR > 1 ? " " : ""), $1, $2 }')
  [[ $params == "rds.force_ssl=1 rds.tls10=disabled rds.tls11=disabled" ]] ||
    die "TLS: parameter group $group has '$params', expected rds.force_ssl=1 rds.tls10=disabled rds.tls11=disabled"
  status=$(aws rds describe-db-instances --db-instance-identifier "$(stack_output DBInstanceIdentifier)" \
    --region "$region" --query 'DBInstances[0].DBParameterGroups[0].ParameterApplyStatus' --output text)
  [[ $status == in-sync ]] || die "TLS: the instance's parameter group is '$status', not in-sync (reboot it)"
  print_info "TLS: $group forces encryption with TLS 1.0 and 1.1 off, applied to the instance"
  if ! out=$({
    printf "python3 -I - '%s' '%s' <<'PROBE'\n" "$(stack_output DBEndpoint)" "$(stack_output DBPort)"
    cat "$INFRA_DIR/tds-plaintext-probe.py"
    printf '\nPROBE\n'
  } | remote 2>&1); then
    die "TLS: $out"
  fi
  print_info "TLS: $out"
  if ! out=$(db_tls_check_script | remote 2>&1); then
    die "TLS: $out"
  fi
  while IFS= read -r line; do print_info "TLS: $line"; done <<<"$out"
}

# --- Post-deploy secret-leak check -------------------------------------------

# scan_for_secrets <source> <name>=<value>...: reads text on stdin and fails if
# any value occurs in it, naming the secret and never printing the value. The
# value reaches grep as a pattern file through a process substitution. Empty
# values are skipped (an empty pattern matches everything).
scan_for_secrets() {
  local source=$1 text pair name value leaked=0
  shift
  text=$(cat)
  [[ -n $text ]] || die "secret-leak check: $source is empty or could not be read"
  for pair in "$@"; do
    name=${pair%%=*}
    value=${pair#*=}
    [[ -n $value ]] || continue
    if printf '%s' "$text" | grep -qF -f <(printf '%s\n' "$value"); then
      print_error "secret-leak check: the value of $name appears in $source"
      leaked=1
    fi
  done
  ((leaked == 0)) || return 1
  print_info "secret-leak check: no secret value in $source"
}

# --- SCIM smoke ----------------------------------------------------------------

# The service's development fixtures, read from the file that defines them so
# that a fixture added there is checked too.
AUTHORIZATION_YAML="$INFRA_DIR/../backend/src/main/resources/authorization.yaml"

# dev_fixture_usernames: the userName of every development fixture User, one
# per line: each baseline-member, dormant-member and Group member value under
# app.dev-fixtures. Fails when it finds none, so a reshaped file
# cannot turn the check into a pass over nothing.
dev_fixture_usernames() {
  local names
  names=$(awk '
    /^  dev-fixtures:/ { inside = 1; next }
    inside && /^ {0,2}[^ #]/ { inside = 0 }
    inside && match($0, /^ +(- )?(baseline-member|dormant-member|member): */) {
      value = substr($0, RLENGTH + 1)
      sub(/ *(#.*)?$/, "", value)
      print value
    }
  ' "$AUTHORIZATION_YAML")
  [[ -n $names ]] || die "no development fixture userName found in $AUTHORIZATION_YAML"
  printf '%s\n' "$names"
}
#
# scim_smoke <base-url>: signs in as the Bootstrap Admin (ADMIN_USERNAME,
# ADMIN_PASSWORD), mints a one-day token on a throwaway connector, and over
# SCIM creates a User, finds it in the list, finds no development fixture
# account, deletes the User and confirms it is gone; then deletes the
# connector, which revokes the token, and signs out.
#
# The first sign-in on a fresh stack must replace the seeded password. The new
# one comes from new_admin_password, and is handed to stage_admin_password
# BEFORE the change and to commit_admin_password after it, so a failure between
# the two leaves the new value recoverable. The caller defines all three; they
# run inside the smoke's subshell, whose own variables are all named smoke_*.
#
# Runs in a subshell, so its cleanup trap and working directory end with it.
# Request bodies holding a credential travel on curl's stdin, and the CSRF and
# bearer headers through process substitutions; responses are read into shell
# variables. The cookie jar is the one file, in a private temporary directory
# removed on exit, and its session is signed out at the end.
scim_smoke() (
  set -euo pipefail
  smoke_base=$1
  smoke_dir=$(mktemp -d)
  chmod 0700 "$smoke_dir"
  smoke_jar="$smoke_dir/cookies"
  smoke_connector=""
  smoke_user=""
  smoke_token=""
  smoke_csrf_header=""
  smoke_csrf_token=""

  # request <method> <path> [json-body]: sets STATUS and BODY. CSRF header from
  # the last csrf call; bearer token when one is held and the path is SCIM.
  request() {
    local method=$1 path=$2 out
    local -a args=(-sS -X "$method" -b "$smoke_jar" -c "$smoke_jar" -H 'Accept: application/json'
      -w '\n%{http_code}' --max-time 30)
    # The credential headers, read by curl from a /dev/fd path.
    credential_headers() {
      if [[ $path == /scim/* ]]; then
        printf 'Authorization: Bearer %s\n' "$smoke_token"
      elif [[ -n $smoke_csrf_token ]]; then
        printf '%s: %s\n' "$smoke_csrf_header" "$smoke_csrf_token"
      fi
    }
    if (($# > 2)); then
      args+=(-H 'Content-Type: application/json' --data-binary @-)
      out=$(printf '%s' "$3" | curl "${args[@]}" -H @<(credential_headers) "$smoke_base$path")
    else
      out=$(curl "${args[@]}" -H @<(credential_headers) "$smoke_base$path" </dev/null)
    fi
    STATUS=${out##*$'\n'}
    BODY=${out%$'\n'*}
    [[ $BODY != "$STATUS" ]] || BODY=""
  }
  expect() { # <status> <what>
    [[ $STATUS == "$1" ]] || die "SCIM smoke: $2 answered $STATUS, expected $1"
  }
  csrf() {
    request GET /api/auth/csrf
    expect 200 "GET /api/auth/csrf"
    smoke_csrf_header=$(printf '%s' "$BODY" | jq -er .headerName)
    smoke_csrf_token=$(printf '%s' "$BODY" | jq -er .token)
  }
  login() {
    local smoke_body
    smoke_body=$(printf '%s' "$ADMIN_PASSWORD" | jq -Rsc --arg u "$ADMIN_USERNAME" '{username: $u, password: .}')
    csrf
    request POST /api/auth/login "$smoke_body"
    expect 200 "Login as the Bootstrap Admin"
  }
  # shellcheck disable=SC2329 # invoked by the EXIT trap below
  cleanup() {
    local status=$?
    trap - EXIT
    if [[ -n $smoke_user && -n $smoke_token ]]; then request DELETE "/scim/v2/Users/$smoke_user" || true; fi
    if [[ -n $smoke_connector ]]; then
      { csrf && request DELETE "/api/admin/connectors/$smoke_connector"; } || true
    fi
    rm -rf "$smoke_dir"
    exit "$status"
  }
  trap cleanup EXIT

  login
  if [[ $(printf '%s' "$BODY" | jq -r .passwordChangeRequired) == true ]]; then
    print_info "SCIM smoke: replacing the Bootstrap Admin's seeded password"
    smoke_new=$(new_admin_password)
    stage_admin_password "$smoke_new"
    smoke_body=$(jq -nc --rawfile c <(printf '%s' "$ADMIN_PASSWORD") --rawfile n <(printf '%s' "$smoke_new") \
      '{currentPassword: $c, newPassword: $n}')
    csrf
    request POST /api/auth/change-password "$smoke_body"
    expect 204 "the Bootstrap Admin's password change"
    commit_admin_password "$smoke_new"
    ADMIN_PASSWORD=$smoke_new
    login
  fi

  smoke_stamp="deploy-smoke-$(date +%Y%m%d%H%M%S)-$RANDOM"
  csrf
  request POST /api/admin/connectors "$(jq -nc --arg n "$smoke_stamp" '{displayName: $n}')"
  expect 201 "creating the smoke connector"
  smoke_connector=$(printf '%s' "$BODY" | jq -er .id)
  csrf
  request POST "/api/admin/connectors/$smoke_connector/tokens" \
    '{"permissions":["user:read","user:write"],"lifetimeDays":1}'
  expect 201 "issuing the smoke connector's token"
  smoke_token=$(printf '%s' "$BODY" | jq -er .presentedValue)

  request POST /scim/v2/Users \
    "$(jq -nc --arg u "$smoke_stamp" '{schemas: ["urn:ietf:params:scim:schemas:core:2.0:User"], userName: $u}')"
  expect 201 "SCIM create"
  smoke_user=$(printf '%s' "$BODY" | jq -er .id)
  print_info "SCIM smoke: created User $smoke_user"

  smoke_filter=$(jq -rn --arg u "$smoke_stamp" '"userName eq \"\($u)\"" | @uri')
  request GET "/scim/v2/Users?filter=$smoke_filter"
  expect 200 "SCIM list"
  [[ $(printf '%s' "$BODY" | jq -r --arg id "$smoke_user" '[.Resources[]? | select(.id == $id)] | length') == 1 ]] ||
    die "SCIM smoke: the created User is missing from the list"
  print_info "SCIM smoke: listed it"

  # No development fixture account exists (APP_DEV_FIXTURES_ENABLED=false): none
  # of the userNames authorization.yaml seeds them as (dev_fixture_usernames).
  smoke_fixtures=$(dev_fixture_usernames)
  for smoke_fixture in $smoke_fixtures; do
    smoke_filter=$(jq -rn --arg u "$smoke_fixture" '"userName eq \"\($u)\"" | @uri')
    request GET "/scim/v2/Users?filter=$smoke_filter"
    expect 200 "SCIM list"
    [[ $(printf '%s' "$BODY" | jq -r '.totalResults') == 0 ]] ||
      die "SCIM smoke: the development fixture account $smoke_fixture exists on this stack"
  done
  print_info "SCIM smoke: no development fixture account exists"

  request DELETE "/scim/v2/Users/$smoke_user"
  expect 204 "SCIM delete"
  request GET "/scim/v2/Users/$smoke_user"
  expect 404 "reading the deleted User"
  smoke_user=""
  print_info "SCIM smoke: deleted it"

  csrf
  request DELETE "/api/admin/connectors/$smoke_connector"
  expect 204 "deleting the smoke connector"
  smoke_connector=""
  csrf
  request DELETE /api/auth/logout
  expect 204 "logout"
  print_info "SCIM smoke: create, list and delete passed"
)
