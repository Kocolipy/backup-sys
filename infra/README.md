# AWS CloudFormation Deployment Guide

Deploy the Spring Boot backend to AWS ap-southeast-1 with an ALB, EC2, **RDS for
SQL Server 2022**, and Redis.

Run every command below from `infra/`: `deploy.sh` finds the template and the
repo from its own location, but writes `<stack>-outputs.txt` to the current
directory, which is where `cleanup.sh` deletes it from. The local artefacts —
`parameters.json`, `*-outputs.txt` — are gitignored.

The instance has **no SSH**: no key pair and no inbound port 22. Operators reach
it through AWS Systems Manager — Session Manager for a shell, Run Command for
the scripts here ([Instance access](#instance-access)).

## Quick Start

```bash
cd infra

# 1. Get your VPC details
./get-vpc-info.sh vpc-YOUR_VPC_ID

# 2. Deploy everything
./deploy.sh
```

The script prompts for VPC IDs, the SQL Server edition and sizing — **never for
a password**: every
credential is generated into Secrets Manager by the stack
([Database credentials](docs/database-credentials.md)). "Deployment" below has
what it does, step by step.

> **Before taking traffic:** attach an AWS WAF web ACL to the ALB. The stack
> does not create one, and the application has no rate limiter of its own. See
> [Edge throttling](docs/edge-throttling.md).

> **Cost:** the default is Express Edition on a small class, for a trial stack.
> SQL Server Standard Edition, licence-included — production's edition — is
> expensive. Read [Engine, edition and cost](docs/database-engine.md)
> before deploying.

---

## Architecture

```
Internet → ALB (HTTP:80) → EC2 (8080) → RDS for SQL Server (1433) + Redis
           (public)         (public)     (private subnets)
```

**Components:**

- Application Load Balancer (internet-facing)
- EC2 instance (Amazon Linux 2023 + Java 25, and `sqlcmd` for the database
  bootstrap), managed through Systems Manager
- RDS for SQL Server 2022, licence-included — Express Edition by default, for
  a trial stack; Standard Edition in production (`DBEngine`) — (encrypted under a
  customer-managed KMS key, TLS 1.2+ forced, Enhanced Monitoring and Performance
  Insights, automated backups)
- Secrets Manager secrets for every database credential, the driver's trust
  store and the Bootstrap Admin's first password
- ElastiCache Redis (session storage)
- An SNS topic as the stack's alarm target, and two database alarms
- Security groups with proper isolation

The rest of the stack's documentation is in `docs/`, one file per concern;
[`docs/index.md`](docs/index.md) lists them. Read the one your change or your
incident touches.

---

## Prerequisites

1. **Existing VPC in ap-southeast-1** with:
   - 2 public subnets (different AZs) for ALB; the instance's (public subnet 1)
     reaches the internet through the Internet Gateway, to install the SQL
     Server tools and fetch the RDS CA bundle at boot
   - 2 private subnets (different AZs) for RDS/Redis
   - Internet Gateway attached

2. **AWS CLI** configured, as a principal that may create the stack's
   resources (IAM, KMS, RDS, Secrets Manager, CloudWatch, SNS included), read RDS
   engine versions and orderable options, create and write the S3 bucket the
   template and the JAR are staged in, and run commands on the instance through
   Systems Manager (`ssm:SendCommand`, `ssm:GetCommandInvocation`,
   `ssm:DescribeInstanceInformation`; `ssm:StartSession` for a shell) — this
   principal becomes the database key's
   administrator (`KeyAdminArn`):

   ```bash
   aws configure
   # Set region: ap-southeast-1
   ```

   Plus `jq`, `curl`, `openssl` and `base64` on the machine you deploy from,
   and the AWS CLI's Session Manager plugin if you want a shell on the instance
   (the scripts do not need it). No database client: everything that talks to
   the database runs on the instance.

3. **Application built** — the _integrated_ JAR, `make package` from the repo
   root (a plain `./mvnw clean package` serves no SPA). `./deploy.sh` runs it
   for you when you answer yes to the JAR prompt.

---

## Deployment

### Option 1: Automated (Recommended)

```bash
./deploy.sh
```

It prompts for no password, and asks for the SQL Server edition — `express`
(the default, for a trial stack) or `standard` (production's) — then for an instance class,
defaulting to the smallest that edition runs on. In order, it:

1. checks that RDS resolves SQL Server `16.00` in the edition you chose and that
   the instance class is orderable for it, licence-included, with storage
   encryption, Enhanced Monitoring and Performance Insights;
2. stages the template in S3 and creates the stack from it, and waits for it;
3. writes `<stack>-outputs.txt` (no secret value in it);
4. waits for the instance to register with Systems Manager, then for its boot
   — which installed `sqlcmd` and ran the database bootstrap — to finish,
   failing on a failed boot, then re-runs the bootstrap and fails unless the
   re-run changed nothing: `./db-bootstrap.sh --expect-no-op <stack>`;
5. on a yes to the JAR prompt, builds the integrated JAR, stages it in S3 for
   the instance to fetch, runs the migrate step, starts the service, and runs
   `./smoke.sh <stack>`, failing the
   deploy on any failed check; on a no, runs `./smoke.sh --leaks-only <stack>`,
   failing the deploy on a leaked secret.

The only User a deployment is seeded with is the Bootstrap Admin
(`AppBootstrapUsername`), whose first password is generated into
`AppBootstrapSecretArn`; existing accounts are never overwritten on restart.
After the first smoke that secret holds its current password:

```bash
aws secretsmanager get-secret-value --secret-id <AppBootstrapSecretArn> \
  --query SecretString --output text | jq -r .password
```

### Option 2: Manual

```bash
# 1. Get VPC info
./get-vpc-info.sh vpc-xxxxx

# 2. Configure parameters (no secret goes in it)
cp parameters.template.json parameters.json
# Edit parameters.json: VPC and subnet IDs, ArtifactBucket — the bucket of step
# 3, which the instance fetches the JAR from — DBEngine and DBInstanceClass
# (docs/database-engine.md) and KeyAdminArn — your deploying role's or user's
# ARN (aws sts get-caller-identity; for an assumed role, the role's ARN from
# aws iam get-role, path included)

# 3. Stage the template in S3: it is larger than --template-body accepts
#    (create the bucket first if it is not there, with public access blocked)
BUCKET=cfn-templates-$(aws sts get-caller-identity --query Account --output text)-ap-southeast-1
aws s3 cp infrastructure.yaml s3://$BUCKET/spring-backend/infrastructure.yaml

# 4. Deploy stack
aws cloudformation create-stack \
  --stack-name spring-backend \
  --template-url https://$BUCKET.s3.ap-southeast-1.amazonaws.com/spring-backend/infrastructure.yaml \
  --parameters file://parameters.json \
  --capabilities CAPABILITY_NAMED_IAM \
  --region ap-southeast-1

# 5. Wait for completion (20-40 minutes: RDS for SQL Server is slow to create)
aws cloudformation wait stack-create-complete \
  --stack-name spring-backend \
  --region ap-southeast-1

# 6. Confirm the boot's database bootstrap and that a re-run is a no-op
./db-bootstrap.sh --expect-no-op spring-backend

# 7. Deploy the application (below), then the post-deploy smoke
./smoke.sh spring-backend
```

---

## Instance access

The instance has no key pair, and its security group opens no port 22: the only
inbound rule is the ALB's to port 8080. Its role carries
`AmazonSSMManagedInstanceCore`, and the Systems Manager agent on Amazon Linux
2023 registers it at boot, over its outbound internet route.

**A shell** — Session Manager, which needs the
[Session Manager plugin](https://docs.aws.amazon.com/systems-manager/latest/userguide/session-manager-working-with-install-plugin.html)
for the AWS CLI on your machine (or use the console: EC2 → the instance →
Connect → Session Manager):

```bash
INSTANCE_ID=$(aws cloudformation describe-stacks \
  --stack-name spring-backend \
  --region ap-southeast-1 \
  --query 'Stacks[0].Outputs[?OutputKey==`EC2InstanceId`].OutputValue' \
  --output text)

aws ssm start-session --target $INSTANCE_ID --region ap-southeast-1
```

The session is `ssm-user`, which may `sudo`. The stack's
`SessionManagerCommand` output is the same command, filled in.

**The scripts** — `deploy.sh`, `deploy-jar.sh`, `db-bootstrap.sh` and
`smoke.sh` — run their steps on the instance as root through Run Command
(`AWS-RunShellScript`), with your AWS credentials and no plugin. Run Command
keeps each command's text and the start of its output in the account's command
history, so the scripts send no secret value to the instance and bring none
back: the instance reads its own secrets, and a check that compares them with
its files returns its verdict alone.

---

## Application Deployment

The instance bootstraps the database at boot, so the JAR can be deployed as soon
as the stack is up and the boot has finished (`./db-bootstrap.sh <stack>` waits
for it). Starting the migrate step re-runs the bootstrap first.

```bash
# Build the integrated JAR (SPA + backend), from the repo root
(cd .. && make package)

# Ship it, migrate (as the owner login), and start the application only if
# that succeeded
./deploy-jar.sh spring-backend

# Then check it: health, TLS, SCIM and fixtures, alarms and secret leaks
./smoke.sh spring-backend
```

`deploy-jar.sh` uploads the JAR to `s3://<ArtifactBucket>/<stack>/jar/`, the one
prefix the instance's role may read. The instance fetches it, checks its
SHA-256 against the one computed on your machine, installs it as
`/opt/backend/backend.jar` (`root:root 0644`), runs the migrate step with the
service stopped, and starts the service only if the migration succeeded. The
uploaded JARs stay in the bucket until you delete them.

---

## Test Application

`./smoke.sh` covers this end to end. By hand: the Bootstrap Admin's password is
in `AppBootstrapSecretArn` (see [Deployment](#deployment)). If you replace a
seeded password by hand, write the new one back to that secret, or the next
`smoke.sh` cannot sign in.

```bash
# Get ALB URL
ALB_URL=$(aws cloudformation describe-stacks \
  --stack-name spring-backend \
  --region ap-southeast-1 \
  --query 'Stacks[0].Outputs[?OutputKey==`LoadBalancerURL`].OutputValue' \
  --output text)

# Test health
curl $ALB_URL/actuator/health

# Every unsafe request, Login included, sends the session's CSRF token in the
# header GET /api/auth/csrf names. Login replaces the token, so fetch a fresh
# one before each unsafe request.
post() {
  local csrf; csrf=$(curl -s -b cookies.txt -c cookies.txt $ALB_URL/api/auth/csrf)
  curl -X POST "$ALB_URL$1" -b cookies.txt -c cookies.txt \
    -H "Content-Type: application/json" \
    -H "$(echo "$csrf" | jq -r .headerName): $(echo "$csrf" | jq -r .token)" \
    -d "$2"
}

# Login as the Bootstrap Admin. Its seeded password must be replaced first:
# until then the session reaches only the password change, which ends every
# session, so log in again with the new password.
post /api/auth/login '{"username":"admin","password":"YOUR_BOOTSTRAP_PASSWORD"}'
post /api/auth/change-password \
  '{"currentPassword":"YOUR_BOOTSTRAP_PASSWORD","newPassword":"A_NEW_PASSWORD"}'
post /api/auth/login '{"username":"admin","password":"A_NEW_PASSWORD"}'

# Test authenticated endpoint
curl $ALB_URL/api/auth/me -b cookies.txt
```

---

## Common Tasks

### View Logs

The service writes ECS JSON to stdout (journald) **and** to a rolling file,
`/var/log/backend/backend.json` (`LOG_FILE` in `/opt/backend/.env`; directory
owned by `springboot`, mode `0750`). The file rolls daily or at 50 MB, keeps 14
days and never exceeds 1 GB on disk. The CloudWatch agent on the instance ships
that file into the log group `/<stack name>/backend`, one stream per instance,
kept for `LogRetentionDays`. The agent's configuration is
`/opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json`. The service
itself never sends logs over the network.

Every record carries `service.name` (`backend`), `service.version` (the built
version), `service.environment` (`AppEnvironment`), a `trace.id` / `span.id` for
the request or scheduled-job run that emitted it, and an `@timestamp` in
Singapore time (`+08:00`). Search CloudWatch Logs Insights by `trace.id` to read
one request end to end.

```bash
aws ssm start-session --target $INSTANCE_ID --region ap-southeast-1
sudo journalctl -u backend -f              # stdout
sudo journalctl -u backend-db-bootstrap    # the database bootstrap's runs
sudo tail -f /var/log/backend/backend.json # the file the agent ships
```

### Restart Application

```bash
aws ssm send-command --instance-ids $INSTANCE_ID --region ap-southeast-1 \
  --document-name AWS-RunShellScript \
  --parameters 'commands=["systemctl restart backend"]'
```

### Update Application

```bash
# Build the integrated JAR (SPA + backend), from the repo root
(cd .. && make package)

# Confirm the SPA is in the artefact (package.sh already asserts this)
unzip -Z1 ../backend/target/backend-0.0.1-SNAPSHOT.jar BOOT-INF/classes/static/index.html

# Ship and restart (from infra/). The migrate step (after the database
# bootstrap, a no-op) runs against the new JAR first; the service starts only
# if it succeeded (see `docs/database-boot.md`, "Migrate step").
./deploy-jar.sh spring-backend
```

### Check ALB Target Health

```bash
TG_ARN=$(aws elbv2 describe-target-groups \
  --names spring-backend-TG \
  --region ap-southeast-1 \
  --query 'TargetGroups[0].TargetGroupArn' \
  --output text)

aws elbv2 describe-target-health \
  --target-group-arn $TG_ARN \
  --region ap-southeast-1
```

### Delete Stack

```bash
./cleanup.sh
```

### Inspect the Stack

```bash
aws cloudformation describe-stack-events --stack-name spring-backend \
  --region ap-southeast-1 --max-items 20

aws cloudformation describe-stacks --stack-name spring-backend \
  --region ap-southeast-1 --query 'Stacks[0].Outputs' --output table
```

---

## Troubleshooting

### Health Check Failing

Check application status:

```bash
aws ssm start-session --target $INSTANCE_ID --region ap-southeast-1
sudo systemctl status backend
sudo journalctl -u backend -n 100
```

Check target health:

```bash
aws elbv2 describe-target-health --target-group-arn $TG_ARN
```

**Common causes:**

- Application not running: `sudo systemctl start backend`
- Wrong health check path (should be `/actuator/health`)
- Security group blocking ALB → EC2:8080

### Can't Access via ALB

1. Check ALB DNS is correct
2. Verify security groups:
   - ALB SG allows 80 from internet
   - EC2 SG allows 8080 from ALB SG
3. Check target group shows "healthy"

### Database Connection Error

```bash
aws ssm start-session --target $INSTANCE_ID --region ap-southeast-1

# The URL and the secret ARNs the start script reads (no password is in here)
sudo grep -E '^(DATABASE_|AWS_REGION)' /opt/backend/.env

# Did start.sh read its secrets? An AccessDenied or a missing field shows here
sudo journalctl -u backend -n 50 --no-pager
```

From your own machine, `./db-bootstrap.sh <stack>` doubles as the connection
test: the bootstrap signs in as each login over validated TLS, and resets the
password of one whose secret changed (a `db-init: change: reset the password`
line). Its journal on the instance is `sudo journalctl -u backend-db-bootstrap`.

- `certificate verify failed` (from `sqlcmd`) or `PKIX path building failed` /
  `trustAnchors parameter must be non-empty` (from the service's driver): the RDS
  CA bundle or the trust store is missing or stale, or the trust-store password
  did not reach the driver. Check `/opt/backend/rds-ca-bundle.pem` and
  `/opt/backend/rds-truststore.p12` exist (`sudo keytool -list -storetype PKCS12
-keystore /opt/backend/rds-truststore.p12 -storepass:env P` with `P` exported
  from `DBTrustStoreSecretArn`, in a shell you then close).
- `keystore password was incorrect`: the store was built under another password
  than `DBTrustStoreSecretArn` holds now; rebuild it as UserData does.
- `Login failed for user`: the login's password differs from its secret — re-run
  `./db-bootstrap.sh <stack>`.

### Can't Reach the Instance through Systems Manager

`deploy.sh` stops with "did not register with Systems Manager", or
`start-session` answers `TargetNotConnected`:

- The instance needs an outbound route to the Systems Manager endpoints: its
  subnet (public subnet 1) must route `0.0.0.0/0` to the Internet Gateway.
- Its role must carry `AmazonSSMManagedInstanceCore` (the stack attaches it).
- `aws ssm describe-instance-information --region ap-southeast-1` lists the
  instance with `PingStatus: Online` once the agent has registered, which takes
  a minute or two after launch.
- `start-session` failing with `SessionManagerPlugin is not found`: install the
  Session Manager plugin, or connect from the EC2 console.
