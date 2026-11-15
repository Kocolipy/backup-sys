---
type: Deployment Reference
title: Database engine and cost
description: RDS for SQL Server 2022 Standard Edition — edition, instance classes, cost, collation and time zone.
tags: [rds, sql-server, cost]
---

# Database engine and cost

## Engine, edition and cost

- **Engine:** `sqlserver-se`, version `16.00` — SQL Server 2022 — which RDS
  resolves to its current default build. `deploy.sh` asks RDS for that build
  before creating anything (`describe-db-engine-versions --default-only`), and
  `AutoMinorVersionUpgrade` applies newer builds (cumulative updates) in the
  maintenance window, Sunday 04:00–05:00 UTC (ADR 0013, "Database patching").
- **Edition: Standard Edition, licence-included.** The SQL Server licence is part
  of the hourly instance price; there is nothing to bring or to track. It is the
  edition ADR 0013 chose ("Scope") — Express caps a database at 10 GB, and
  Developer Edition may not serve production — and choosing it is a
  stakeholder sign-off.
- **Instance classes:** Standard Edition does not run on the small classes —
  no `db.t3.micro` to `db.t3.large` and no Graviton (`db.t4g.*`) — so
  `DBInstanceClass` allows only classes RDS offers it on: `db.t3.xlarge`
  (default, 4 vCPU / 16 GiB) and `db.t3.2xlarge`, `db.m5`/`db.m6i` `large` to
  `2xlarge`, and `db.r5`/`db.r6i` `large` and `xlarge`. `deploy.sh` checks the
  one you choose is orderable for Standard Edition, licence-included, in the
  region (`describe-orderable-db-instance-options`) before creating anything.

**It is expensive.** The smallest Standard Edition instance carries the instance
price **and** the per-vCPU SQL Server licence, for four vCPUs at least — before
storage, backups, Performance Insights beyond its free tier, and Multi-AZ (which
doubles the instance price). Take the current price for the class you choose
before sign-off:

```bash
aws pricing get-products --region us-east-1 --service-code AmazonRDS \
  --filters Type=TERM_MATCH,Field=regionCode,Value=ap-southeast-1 \
    Type=TERM_MATCH,Field=databaseEngine,Value="SQL Server" \
    Type=TERM_MATCH,Field=databaseEdition,Value=Standard \
    Type=TERM_MATCH,Field=licenseModel,Value="License included" \
    Type=TERM_MATCH,Field=deploymentOption,Value=Single-AZ \
    Type=TERM_MATCH,Field=instanceType,Value=db.t3.xlarge \
  --query 'PriceList[0]' --output text |
  jq -r '.terms.OnDemand[].priceDimensions[] | "\(.pricePerUnit.USD) USD \(.unit): \(.description)"'
```

An instance that is stopped still bills its storage, and RDS restarts a stopped
instance after seven days; `cleanup.sh` deletes the stack, after a final
snapshot that is itself billed until you delete it.

## Collation and time zone

- **The database is collated `Latin1_General_100_BIN2_UTF8`** — UTF-8 storage in
  code-point order, which the schema and the SCIM sort depend on (ADR 0013, "Runtime model"). It is the **database's** collation, set by
  the bootstrap's `CREATE DATABASE … COLLATE`, which every column takes, and the
  bootstrap refuses a database it finds with any other collation rather than
  altering it (an altered collation would not convert existing columns).
- **The server is collated `Latin1_General_100_BIN2_UTF8` too**
  (`CharacterSetName`), so `master` and `tempdb`
  compare names as the database does. It is fixed at creation; changing it
  replaces the instance.
- **The server collation is the one setting the suite does not reproduce.**
  On the Linux image the local and test servers run, a server collated
  `BIN2_UTF8` cannot take the application lock Flyway guards every migration
  with, so those servers keep their default collation and only their databases
  are `Latin1_General_100_BIN2_UTF8`. RDS runs the Windows build, on which the
  defect is untested (ADR 0013, "Collation and the seed lock", has the
  measurement and why the service's own seed lock is unaffected).
- **What a failed first migrate looks like.** If the Windows build has the
  same defect, the failure is loud, not silent:
  - `backend-migrate.service` fails, so `backend.service`, which `Requires=`
    it, never starts.
  - The deploy's health check and smoke fail.
  - `sudo journalctl -u backend-migrate` shows `FlywaySqlException: Unable to
    acquire SQL Server application lock` with `Error Code : 17750`.
  - On a later deploy, `deploy.sh` prints that journal and stops with the
    service still stopped.
  - The way out is a server collation that takes the lock (ADR 0013 names
    them). Changing `CharacterSetName` replaces the instance, which costs
    nothing on a stack that never migrated.
- **The server's time zone is UTC**, stated (`Timezone: UTC`) rather than
  defaulted: storage is UTC only (ADR 0013, "Storage is UTC only").

Both are fixed when the instance is created; changing either replaces it.
