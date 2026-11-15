#!/bin/bash

# AWS CloudFormation Deployment Script for Spring Boot Backend
#
# Prompts for no password: every credential is generated into Secrets Manager
# by the stack (infra/docs/database-credentials.md, "Database credentials"). The instance has no
# key pair and no inbound port 22: this script reaches it through Systems
# Manager Run Command (lib.sh, "The instance"). The stack's instance
# bootstraps the database itself at boot; this script waits for that boot,
# re-runs the bootstrap to prove it a no-op (db-bootstrap.sh --expect-no-op),
# and runs the post-deploy smoke (smoke.sh), which fails the deploy on a leaked
# secret: the full smoke once the JAR is deployed, and its leak check over the
# boot alone when it is not. The template exceeds CloudFormation's inline
# template-body limit, so it is staged in S3 first (stage_template).
# Never run with command tracing (bash -x): see lib.sh.

set -euo pipefail

# Resolve paths so the script works from any working directory.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
BACKEND_DIR="${REPO_ROOT}/backend"

# shellcheck source=lib.sh source-path=SCRIPTDIR
source "${SCRIPT_DIR}/lib.sh"

# Check prerequisites
check_prerequisites() {
    print_info "Checking prerequisites..."

    local cmd
    for cmd in aws jq curl openssl base64; do
        require_cmd "$cmd"
    done

    if ! aws sts get-caller-identity &> /dev/null; then
        print_error "AWS CLI is not configured. Run 'aws configure' first."
        exit 1
    fi

    # The stack's bootstrap SQL must be the local init's (infra/docs/database-boot.md,
    # "Database bootstrap"): a template whose copy has drifted is not deployed.
    "${SCRIPT_DIR}/check-bootstrap-parity.sh" ||
        die "the template's database bootstrap SQL differs from backend/scripts/local-db-init.sql"

    print_info "Prerequisites check passed"
}

# The IAM principal deploying the stack administers its database key (and only
# administers it). An assumed role's session ARN is turned into the role's ARN,
# path included, which is what a key policy names.
default_key_admin_arn() {
    local caller role
    caller=$(aws sts get-caller-identity --query Arn --output text)
    case $caller in
        arn:*:sts::*:assumed-role/*)
            role=${caller#*:assumed-role/}
            aws iam get-role --role-name "${role%%/*}" --query Role.Arn --output text
            ;;
        *) printf '%s\n' "$caller" ;;
    esac
}

# Get user inputs
get_inputs() {
    print_info "CloudFormation Stack Configuration"
    echo

    read -r -p "Enter stack name (default: spring-backend): " STACK_NAME
    STACK_NAME=${STACK_NAME:-spring-backend}

    AWS_REGION="ap-southeast-1"
    print_info "Region set to: ap-southeast-1 (Singapore)"

    # VPC Configuration (Required)
    echo
    print_info "VPC Configuration (Existing VPC Required)"

    read -r -p "Enter existing VPC ID (e.g., vpc-12345678): " VPC_ID
    [ -n "$VPC_ID" ] || die "VPC ID is required"

    read -r -p "Enter Public Subnet 1 ID (e.g., subnet-12345678): " PUBLIC_SUBNET_1_ID
    [ -n "$PUBLIC_SUBNET_1_ID" ] || die "Public Subnet 1 ID is required"

    read -r -p "Enter Public Subnet 2 ID (e.g., subnet-87654321): " PUBLIC_SUBNET_2_ID
    [ -n "$PUBLIC_SUBNET_2_ID" ] || die "Public Subnet 2 ID is required"

    read -r -p "Enter Private Subnet 1 ID (e.g., subnet-11111111): " PRIVATE_SUBNET_1_ID
    [ -n "$PRIVATE_SUBNET_1_ID" ] || die "Private Subnet 1 ID is required"

    read -r -p "Enter Private Subnet 2 ID (e.g., subnet-22222222): " PRIVATE_SUBNET_2_ID
    [ -n "$PRIVATE_SUBNET_2_ID" ] || die "Private Subnet 2 ID is required"

    echo
    # The instance runs the organisation's RHEL 9 image in private subnet 1,
    # with no route to the internet: its packages come from the repositories
    # the image is configured with (README.md, "Prerequisites").
    print_info "EC2 Configuration (RHEL 9, private subnet, no internet access)"
    read -r -p "Enter the organisation's RHEL 9 image ID (e.g., ami-0123456789abcdef0): " IMAGE_ID
    [[ $IMAGE_ID =~ ^ami-[0-9a-f]+$ ]] || die "the image ID is required, and looks like ami-0123456789abcdef0"

    read -r -p "Enter EC2 instance type (default: t3.small): " INSTANCE_TYPE
    INSTANCE_TYPE=${INSTANCE_TYPE:-t3.small}

    read -r -p "Enter the Java 25 JDK package name in the image's repositories (default: java-25-openjdk-devel): " JAVA_PACKAGE
    JAVA_PACKAGE=${JAVA_PACKAGE:-java-25-openjdk-devel}

    # Microsoft's packages (msodbcsql18, mssql-tools18): empty when the image's
    # repositories already offer them.
    read -r -p "Internal mirror URL of Microsoft's RHEL 9 repository (empty if the image's repositories already carry msodbcsql18): " MSSQL_REPO_BASE_URL
    MSSQL_REPO_GPG_KEY_URL=""
    if [ -n "$MSSQL_REPO_BASE_URL" ]; then
        read -r -p "Internal mirror URL of Microsoft's package signing key: " MSSQL_REPO_GPG_KEY_URL
        [ -n "$MSSQL_REPO_GPG_KEY_URL" ] || die "the signing key's URL is required with the repository's"
    fi

    # Database configuration. No password: the master user's is RDS-managed and
    # the three logins' are generated, all in Secrets Manager.
    print_info "Database Configuration"
    read -r -p "Enter database name (default: backend): " DB_NAME
    DB_NAME=${DB_NAME:-backend}

    # The edition sets the price and the classes on offer: Express, the
    # default, is the small edition for a trial stack; Standard Edition is
    # production's (ADR 0013, "Scope") and bills its licence by the hour
    # (docs/database-engine.md, "Engine, edition and cost").
    local edition default_class
    read -r -p "Enter SQL Server edition: express or standard (default: express): " edition
    case ${edition:-express} in
        express)
            DB_ENGINE=sqlserver-ex
            DB_EDITION_NAME="Express Edition"
            default_class=db.t3.micro
            print_warn "SQL Server Express Edition caps a database at 10 GB: a trial stack's edition, not production's"
            ;;
        standard)
            DB_ENGINE=sqlserver-se
            DB_EDITION_NAME="Standard Edition"
            default_class=db.t3.xlarge
            print_warn "SQL Server Standard Edition (licence-included) bills its licence by the hour: see docs/database-engine.md, \"Engine, edition and cost\""
            ;;
        *) die "the edition must be express or standard" ;;
    esac
    read -r -p "Enter RDS instance class (default: ${default_class}, the smallest ${DB_EDITION_NAME} runs on): " DB_INSTANCE_CLASS
    DB_INSTANCE_CLASS=${DB_INSTANCE_CLASS:-$default_class}

    local default_admin
    default_admin=$(default_key_admin_arn)
    read -r -p "IAM principal administering the database KMS key (default: ${default_admin}): " KEY_ADMIN_ARN
    KEY_ADMIN_ARN=${KEY_ADMIN_ARN:-$default_admin}

    read -r -p "Email for database alarms (optional, confirm the email AWS sends): " ALARM_EMAIL

    # Redis configuration
    print_info "Redis Configuration"
    read -r -p "Enter Redis node type (default: cache.t3.micro): " REDIS_NODE_TYPE
    REDIS_NODE_TYPE=${REDIS_NODE_TYPE:-cache.t3.micro}

    # Application configuration. The Bootstrap Admin's password is generated into
    # its secret; smoke.sh replaces it on the first deploy.
    print_info "Application Configuration"
    read -r -p "Enter application ADMIN username (default: admin): " APP_BOOTSTRAP_USERNAME
    APP_BOOTSTRAP_USERNAME=${APP_BOOTSTRAP_USERNAME:-admin}
}

# The engine and class RDS will actually be asked for: the template names the
# major version 16.00 (SQL Server 2022), which RDS resolves to its default
# build, and the instance class must be orderable for the chosen edition,
# licence-included, in this region, with what the template turns on: storage
# encryption, Enhanced Monitoring and Performance Insights. Checked before
# anything is created.
check_engine_and_class() {
    local version option feature
    version=$(aws rds describe-db-engine-versions --engine "${DB_ENGINE}" --engine-version 16.00 \
        --default-only --region "${AWS_REGION}" --query 'DBEngineVersions[0].EngineVersion' --output text)
    [[ -n $version && $version != None ]] ||
        die "RDS offers no default SQL Server 2022 (16.00) ${DB_EDITION_NAME} build in ${AWS_REGION}"
    print_info "RDS resolves engine version 16.00 to ${version}"
    option=$(aws rds describe-orderable-db-instance-options --engine "${DB_ENGINE}" --engine-version "$version" \
        --license-model license-included --db-instance-class "${DB_INSTANCE_CLASS}" --region "${AWS_REGION}" \
        --query 'OrderableDBInstanceOptions[0]' --output json)
    [[ -n $option && $option != null ]] ||
        die "${DB_INSTANCE_CLASS} is not orderable for SQL Server ${DB_EDITION_NAME}, licence-included, in ${AWS_REGION}"
    for feature in SupportsStorageEncryption SupportsEnhancedMonitoring SupportsPerformanceInsights; do
        [[ $(jq -r --arg f "$feature" '.[$f]' <<<"$option") == true ]] ||
            die "${DB_INSTANCE_CLASS} on SQL Server ${DB_EDITION_NAME} reports ${feature}=false, and the template turns that feature on"
    done
    print_info "${DB_INSTANCE_CLASS} is orderable for SQL Server ${DB_EDITION_NAME}, licence-included, with storage encryption, Enhanced Monitoring and Performance Insights"
}

# The template is larger than create-stack's 51,200-byte --template-body
# limit, so it goes to S3 and the stack is created from its URL. The bucket is
# CFN_TEMPLATE_BUCKET, or one this script creates once per account and region
# with every public access blocked. The template holds no secret. The same
# bucket holds each deploy's JAR, under <stack>/jar/ (lib.sh, ship_jar).
resolve_artifact_bucket() {
    local account
    account=$(aws sts get-caller-identity --query Account --output text)
    ARTIFACT_BUCKET=${CFN_TEMPLATE_BUCKET:-cfn-templates-${account}-${AWS_REGION}}
}

stage_template() {
    local bucket=$ARTIFACT_BUCKET key
    if ! aws s3api head-bucket --bucket "$bucket" --region "${AWS_REGION}" 2>/dev/null; then
        print_info "Creating the template bucket ${bucket}"
        aws s3api create-bucket --bucket "$bucket" --region "${AWS_REGION}" \
            --create-bucket-configuration "LocationConstraint=${AWS_REGION}" >/dev/null
        aws s3api put-public-access-block --bucket "$bucket" --region "${AWS_REGION}" \
            --public-access-block-configuration \
            BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
    fi
    key="${STACK_NAME}/infrastructure-$(date +%Y%m%d%H%M%S).yaml"
    aws s3 cp "${SCRIPT_DIR}/infrastructure.yaml" "s3://${bucket}/${key}" --region "${AWS_REGION}" >/dev/null
    TEMPLATE_URL="https://${bucket}.s3.${AWS_REGION}.amazonaws.com/${key}"
    print_info "Template staged at ${TEMPLATE_URL}"
}

# The RDS CA bundle for the region, which every database connection validates
# the server against: AWS's public file, fetched here and staged under
# <stack>/boot/ in the artifact bucket, because the instance has no route to
# the internet to fetch it itself. RDS_CA_BUNDLE names a local copy instead.
stage_boot_files() {
    local bundle
    bundle=$(mktemp)
    if [ -n "${RDS_CA_BUNDLE:-}" ]; then
        cp "$RDS_CA_BUNDLE" "$bundle"
    else
        curl -fsS --proto '=https' -o "$bundle" \
            "https://truststore.pki.rds.amazonaws.com/${AWS_REGION}/${AWS_REGION}-bundle.pem" ||
            die "cannot fetch the RDS CA bundle; download it elsewhere and name the file in RDS_CA_BUNDLE"
    fi
    grep -q 'BEGIN CERTIFICATE' "$bundle" || die "the RDS CA bundle holds no certificate"
    aws s3 cp "$bundle" "s3://${ARTIFACT_BUCKET}/${STACK_NAME}/boot/rds-ca-bundle.pem" \
        --region "${AWS_REGION}" --only-show-errors
    rm -f "$bundle"
    print_info "RDS CA bundle staged at s3://${ARTIFACT_BUCKET}/${STACK_NAME}/boot/rds-ca-bundle.pem"
}

# Create parameters file. It holds no secret.
create_parameters_file() {
    print_info "Creating parameters file..."

    PARAMETERS_FILE="/tmp/cfn-parameters-${STACK_NAME}.json"
    jq -n \
        --arg VpcId "$VPC_ID" \
        --arg PublicSubnet1Id "$PUBLIC_SUBNET_1_ID" \
        --arg PublicSubnet2Id "$PUBLIC_SUBNET_2_ID" \
        --arg PrivateSubnet1Id "$PRIVATE_SUBNET_1_ID" \
        --arg PrivateSubnet2Id "$PRIVATE_SUBNET_2_ID" \
        --arg ImageId "$IMAGE_ID" \
        --arg InstanceType "$INSTANCE_TYPE" \
        --arg JavaPackage "$JAVA_PACKAGE" \
        --arg MssqlRepoBaseUrl "$MSSQL_REPO_BASE_URL" \
        --arg MssqlRepoGpgKeyUrl "$MSSQL_REPO_GPG_KEY_URL" \
        --arg ArtifactBucket "$ARTIFACT_BUCKET" \
        --arg DBEngine "$DB_ENGINE" \
        --arg DBName "$DB_NAME" \
        --arg DBInstanceClass "$DB_INSTANCE_CLASS" \
        --arg KeyAdminArn "$KEY_ADMIN_ARN" \
        --arg AlarmEmail "$ALARM_EMAIL" \
        --arg RedisNodeType "$REDIS_NODE_TYPE" \
        --arg AppBootstrapUsername "$APP_BOOTSTRAP_USERNAME" \
        '$ARGS.named | to_entries | map({ParameterKey: .key, ParameterValue: .value})' \
        > "$PARAMETERS_FILE"

    print_info "Parameters file created at ${PARAMETERS_FILE}"
}

# Deploy stack
deploy_stack() {
    print_info "Deploying CloudFormation stack: ${STACK_NAME}"

    aws cloudformation create-stack \
        --stack-name "${STACK_NAME}" \
        --template-url "${TEMPLATE_URL}" \
        --parameters "file://${PARAMETERS_FILE}" \
        --capabilities CAPABILITY_NAMED_IAM \
        --region "${AWS_REGION}"

    print_info "Stack creation initiated. Waiting for completion..."
    print_warn "This may take 20-40 minutes: RDS for SQL Server is slow to create..."

    aws cloudformation wait stack-create-complete \
        --stack-name "${STACK_NAME}" \
        --region "${AWS_REGION}"

    print_info "Stack created successfully!"
}

# Display outputs
display_outputs() {
    print_info "Retrieving stack outputs..."

    load_stack_outputs "${STACK_NAME}" "${AWS_REGION}"
    EC2_ID=$(stack_output EC2InstanceId)
    EC2_IP=$(stack_output EC2PrivateIP)
    ALB_DNS=$(stack_output LoadBalancerDNS)
    ALB_URL=$(stack_output LoadBalancerURL)
    DB_ENDPOINT=$(stack_output DBEndpoint)
    REDIS_ENDPOINT=$(stack_output RedisEndpoint)
    SESSION_COMMAND=$(stack_output SessionManagerCommand)
    ADMIN_SECRET_ARN=$(stack_output AppBootstrapSecretArn)
    ALARM_TOPIC_ARN=$(stack_output AlarmTopicArn)

    echo
    print_info "=== Deployment Complete ==="
    echo
    echo "Load Balancer DNS: ${ALB_DNS}"
    echo "Application URL: ${ALB_URL}"
    echo "EC2 Instance: ${EC2_ID} (${EC2_IP})"
    echo "Database Endpoint: ${DB_ENDPOINT}"
    echo "Redis Endpoint: ${REDIS_ENDPOINT}"
    echo "Bootstrap Admin secret: ${ADMIN_SECRET_ARN}"
    echo "Alarm topic: ${ALARM_TOPIC_ARN}"
    echo

    echo "Shell on the instance (Session Manager):"
    echo "  ${SESSION_COMMAND}"
    echo

    # Save to file
    cat > "${STACK_NAME}-outputs.txt" << EOF
Stack Name: ${STACK_NAME}
Region: ${AWS_REGION}
Deployed: $(date)

Load Balancer DNS: ${ALB_DNS}
Application URL: ${ALB_URL}
EC2 Instance: ${EC2_ID} (${EC2_IP})
Database Endpoint: ${DB_ENDPOINT}
Redis Endpoint: ${REDIS_ENDPOINT}
Bootstrap Admin secret: ${ADMIN_SECRET_ARN}
Alarm topic: ${ALARM_TOPIC_ARN}

Shell on the instance (Session Manager; needs the AWS CLI's Session Manager plugin):
${SESSION_COMMAND}

Next Steps (deploy.sh does 1-3 when you answer yes to the JAR prompt):
1. The database and its logins were bootstrapped by the instance at boot; to
   re-run that (a no-op on a healthy stack): ./db-bootstrap.sh ${STACK_NAME} ${AWS_REGION}
2. Build the integrated JAR (SPA + backend), from the repo root: make package
   (equivalently: scripts/package.sh — 'cd backend && ./mvnw clean package' is
   backend-only and produces a JAR with no SPA)
3. Ship the JAR to the instance, migrate and start: see README.md, "Application Deployment"
   (the migrate step re-runs the database bootstrap, then the migrations as the owner login; if it fails, the service is not started)
4. Post-deploy smoke (health, TLS, SCIM, alarms, secret leaks): ./smoke.sh ${STACK_NAME} ${AWS_REGION}
5. The Bootstrap Admin's password: aws secretsmanager get-secret-value --secret-id ${ADMIN_SECRET_ARN} --query SecretString --output text
EOF

    print_info "Outputs saved to ${STACK_NAME}-outputs.txt"
}

# The instance bootstraps the database at boot (UserData). Wait for that
# boot, then run the bootstrap again: on a fresh stack the re-run must change
# nothing, which proves it idempotent before anything depends on it.
bootstrap_database() {
    "${SCRIPT_DIR}/db-bootstrap.sh" --expect-no-op "${STACK_NAME}" "${AWS_REGION}"
}

# Prompt for JAR deployment
prompt_jar_deployment() {
    echo
    read -r -p "Do you want to deploy the application JAR now? (y/n): " DEPLOY_JAR

    if [[ $DEPLOY_JAR =~ ^[Yy]$ ]]; then
        deploy_jar
        "${SCRIPT_DIR}/smoke.sh" "${STACK_NAME}" "${AWS_REGION}"
    else
        # The boot's leak check needs no JAR: UserData, cloud-init logs, .env.
        print_warn "Skipping JAR deployment; checking the boot for leaked secrets only"
        "${SCRIPT_DIR}/smoke.sh" --leaks-only "${STACK_NAME}" "${AWS_REGION}"
        print_warn "Deploy the JAR, then run ./smoke.sh ${STACK_NAME} ${AWS_REGION} for the full smoke; see ${STACK_NAME}-outputs.txt"
    fi
}

# Deploy JAR
deploy_jar() {
    print_info "Building application (SPA + backend, integrated JAR)..."

    if [ ! -f "${BACKEND_DIR}/pom.xml" ]; then
        print_error "pom.xml not found at ${BACKEND_DIR}. Is infra/ still a sibling of backend/?"
        exit 1
    fi

    if [ ! -x "${REPO_ROOT}/scripts/package.sh" ]; then
        print_error "scripts/package.sh not found or not executable at ${REPO_ROOT}/scripts/package.sh"
        exit 1
    fi

    # The integrated package path, not a bare './mvnw clean package': the
    # backend's with-frontend profile is off by default, so a pure backend build
    # ships a JAR with no SPA. package.sh builds frontend/dist and activates the
    # profile with an explicit -Dfrontend.dist.dir.
    "${REPO_ROOT}/scripts/package.sh"

    JAR_FILE=$(find "${BACKEND_DIR}/target" -maxdepth 1 -name 'backend-*.jar' ! -name '*-plain.jar' -print -quit)

    if [ -z "$JAR_FILE" ]; then
        print_error "JAR file not found in ${BACKEND_DIR}/target"
        exit 1
    fi

    # package.sh already asserts this; re-check the artefact we are about to ship
    # so a stale or hand-built JAR in target/ cannot be deployed SPA-less.
    if command -v unzip >/dev/null 2>&1; then
        if unzip -Z1 "${JAR_FILE}" 'BOOT-INF/classes/static/index.html' >/dev/null 2>&1; then
            print_info "verified BOOT-INF/classes/static/index.html inside ${JAR_FILE}"
        else
            print_error "${JAR_FILE} contains no BOOT-INF/classes/static/index.html — the SPA is missing from the JAR"
            exit 1
        fi
    else
        print_warn "unzip not installed — cannot verify the SPA is inside ${JAR_FILE}"
    fi

    print_info "Shipping the JAR to the instance, migrating the schema (migrate step), then starting the application..."

    ship_jar "${STACK_NAME}" "${AWS_REGION}" "${JAR_FILE}" ||
        die "the JAR was not deployed: the instance's output is above"

    print_info "Application deployed and started!"
}

# Main execution
main() {
    echo "=========================================="
    echo "AWS CloudFormation Deployment Script"
    echo "Spring Boot Backend Infrastructure"
    echo "=========================================="
    echo

    check_prerequisites
    get_inputs
    check_engine_and_class
    resolve_artifact_bucket
    create_parameters_file
    stage_template
    stage_boot_files
    deploy_stack
    display_outputs
    bootstrap_database
    prompt_jar_deployment

    echo
    print_info "Deployment completed successfully!"
    print_info "Check ${STACK_NAME}-outputs.txt for connection details"
}

# Run main function
main
