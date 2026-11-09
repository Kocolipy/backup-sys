---
type: Deployment Reference
title: Parameters reference
description: Every CloudFormation parameter, what it sets and its default.
tags: [cloudformation, parameters]
---

# Parameters Reference

| Parameter            | Description                                     | Required | Default                 |
| -------------------- | ----------------------------------------------- | -------- | ----------------------- |
| VpcId                | Existing VPC ID                                 | Yes      | -                       |
| PublicSubnet1Id      | Public subnet 1                                 | Yes      | -                       |
| PublicSubnet2Id      | Public subnet 2                                 | Yes      | -                       |
| PrivateSubnet1Id     | Private subnet 1                                | Yes      | -                       |
| PrivateSubnet2Id     | Private subnet 2                                | Yes      | -                       |
| CreateKeyPair        | Create key pair                                 | No       | true                    |
| KeyName              | Key pair name                                   | No       | spring-backend-key      |
| InstanceType         | EC2 type                                        | No       | t3.small                |
| SSHLocation          | CIDR allowed to SSH to the instance             | Yes      | - (your IP as a /32)    |
| DBInstanceClass      | SQL Server SE class (see "Engine, edition…")    | No       | db.t3.xlarge            |
| DBAllocatedStorage   | RDS storage, GB (20–100)                        | No       | 20                      |
| DBName               | Database the bootstrap creates                  | No       | backend                 |
| DBMasterUsername     | RDS master username (password RDS-managed)      | No       | dbmaster                |
| DBOwnerLogin         | Owner login (`db_owner`, migrations)            | No       | backend_owner           |
| DBAppLogin           | App login (member of `backend_app`)             | No       | backend_app_login       |
| DBRetentionLogin     | Retention login (`backend_audit_retention`)     | No       | backend_retention_login |
| KeyAdminArn          | Deploying role/user; administers the DB key     | Yes      | - (deploy.sh fills it)  |
| AlarmEmail           | Email subscribed to the alarm topic             | No       | (empty)                 |
| RedisNodeType        | ElastiCache node type                           | No       | cache.t3.micro          |
| AppBootstrapUsername | Bootstrap Admin's username (password generated) | No       | admin                   |
| AppEnvironment       | `service.environment` on every log record       | No       | production              |
| LogRetentionDays     | Retention of the `/<stack>/backend` log group   | No       | 90                      |

Never commit a filled-in parameters file.
