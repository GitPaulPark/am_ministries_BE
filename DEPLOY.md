# Deployment runbook — AM Ministries (AWS Seoul + Vercel)

End-to-end deploy for the production launch. Assumes you own the account for
`am-ministries` on both AWS (`ap-northeast-2`) and Vercel. Each step shows
exactly what to click or run, in order.

Rough target cost: ~$45/mo. See [README.md](README.md) for the breakdown.

---

## 0. One-time prerequisites

- Local Docker Desktop running (needed for the backend image build).
- AWS CLI configured (`aws configure` → region `ap-northeast-2`).
- A domain name you control. The examples below use `church.example.com`
  for the frontend and `api.church.example.com` for the backend — replace
  with yours.

---

## 1. Database — Amazon RDS MySQL

AWS console → RDS → **Create database**.

| Field | Value |
|---|---|
| Engine | MySQL 8.0.x |
| Templates | **Production** (Multi-AZ) or **Dev/Test** (single AZ, cheaper) |
| DB instance class | `db.t4g.micro` (burstable, ~$12/mo) |
| Storage | 20 GB gp3 |
| Master username | `church` |
| Master password | strong random — save to Secrets Manager next |
| VPC | same VPC the ECS service will run in |
| Public access | **No** (ECS reaches it via the VPC) |
| Initial database name | `church` |
| Backup retention | 7 days |
| Deletion protection | ✅ On |

Note the endpoint — `church-prod.xxx.ap-northeast-2.rds.amazonaws.com:3306`.

---

## 2. File storage — Amazon EFS

Why EFS: ECS Fargate's local volumes are wiped on redeploy. Bulletin PDFs
and sermon audio must survive container restarts.

AWS console → EFS → **Create file system**.

- Name: `church-uploads`
- VPC: same as RDS and ECS
- Throughput: Bursting
- Mount targets: one per AZ you plan to run ECS tasks in
- Access points — create two:
  - `/uploads` with POSIX user 10001 (matches the Dockerfile's `church` user)
  - `/private` with POSIX user 10001

---

## 3. Secrets — AWS Secrets Manager

AWS console → Secrets Manager → **Store a new secret**.

Secret name: `church/prod`

Secret value (JSON):
```json
{
  "DB_URL": "jdbc:mysql://church-prod.xxx.ap-northeast-2.rds.amazonaws.com:3306/church?useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci&serverTimezone=UTC&useSSL=true&allowPublicKeyRetrieval=true",
  "DB_USERNAME": "church",
  "DB_PASSWORD": "<the master password from step 1>",
  "JWT_SECRET": "<generate: openssl rand -base64 48>",
  "INITIAL_ADMIN_EMAIL": "pastor@yourchurch.org",
  "INITIAL_ADMIN_PASSWORD": "<temporary — will be forced to change on first login>",
  "SENTRY_DSN": "<optional — sentry.io project DSN, blank disables>"
}
```

The `INITIAL_ADMIN_*` entries are consumed by `ProdDataInitializer` on first
startup — the account is created with `password_change_required = true`, so
the temporary password is only valid long enough to reach `/me/password`.

---

## 4. Backend image — ECR

```bash
cd am-backend

# One-time: create the ECR repo
aws ecr create-repository --repository-name church-backend --region ap-northeast-2

# Set the account id into the shell
AWS_ACCT=$(aws sts get-caller-identity --query Account --output text)
REGISTRY=$AWS_ACCT.dkr.ecr.ap-northeast-2.amazonaws.com

# Build (ECS Fargate wants linux/amd64)
docker build --platform linux/amd64 -t church-backend:v1 .

# Push
aws ecr get-login-password --region ap-northeast-2 \
  | docker login --username AWS --password-stdin $REGISTRY
docker tag church-backend:v1 $REGISTRY/church-backend:v1
docker tag church-backend:v1 $REGISTRY/church-backend:latest
docker push $REGISTRY/church-backend:v1
docker push $REGISTRY/church-backend:latest
```

---

## 5. ECS cluster + service

AWS console → ECS → **Create cluster** → Fargate. Name it `church-prod`.

Task definition — **Fargate**, 0.25 vCPU, 512 MB. One container:

- Image: `${AWS_ACCT}.dkr.ecr.ap-northeast-2.amazonaws.com/church-backend:latest`
- Port: 8080
- Environment variables — all read from the Secrets Manager entry above
  (ECS supports `valueFrom`: `arn:aws:secretsmanager:...:secret:church/prod:DB_URL::`
  for each key)
- Also set plain env vars:
  - `SPRING_PROFILES_ACTIVE=prod`
  - `CORS_ALLOWED_ORIGINS=https://church.example.com` (your Vercel domain)
  - `UPLOADS_DIR=/data/uploads`
  - `PRIVATE_UPLOADS_DIR=/data/private`
- Volumes: mount the EFS access points at `/data/uploads` and `/data/private`
- Health check: container health is already defined in the Dockerfile;
  the ALB will probe `/actuator/health` separately.

**Create service** → Launch type Fargate, 1 task, networking in the same
VPC as RDS. Attach to a new Application Load Balancer with:

- Listener: 443 (HTTPS, ACM cert — see step 7)
- Target group: HTTP :8080, health check path `/actuator/health`
- Route: everything on `api.church.example.com` → target group

---

## 6. First boot verification

Watch the task's CloudWatch logs. You should see:

```
Successfully applied 9 migrations to schema `church`, now at version v9
Prod bootstrap: created initial ADMIN pastor@yourchurch.org with
  password_change_required=true. Sign in once and change the password immediately.
Started ChurchApplication in XX seconds
```

Smoke test — the script `scripts/smoke.sh` ships with the backend repo:

```bash
BASE_URL=https://api.church.example.com \
  ADMIN_EMAIL=pastor@yourchurch.org \
  ADMIN_PASSWORD=<temp password> \
  ./scripts/smoke.sh
```

Checks health, public landing, rate limiter, CORS preflight, login + /me.
Exits non-zero on any failure — safe to wire into a CD pipeline step.

---

## 7. HTTPS + domain

AWS console → Route 53 (or your DNS):

- `api.church.example.com` → ALB DNS name (ALIAS record)
- `church.example.com` → Vercel (follow Vercel's prompts)

AWS console → ACM (`ap-northeast-2`): request a certificate for
`api.church.example.com`, validate via DNS. Attach to the ALB listener on 443.

---

## 8. Frontend — Vercel

1. Vercel dashboard → **Add new project** → import `GitPaulPark/am_ministries_FE`.
2. Framework preset: **Vite**. Build command auto-detected.
3. Environment variables:
   - `VITE_API_BASE_URL = https://api.church.example.com/api/v1`
   - `VITE_SENTRY_DSN = <sentry.io project DSN>` (optional — leave empty to disable)
   - `VITE_SENTRY_ENV = production`
   - `VITE_SENTRY_RELEASE = $VERCEL_GIT_COMMIT_SHA` (lets Sentry attribute issues to a deploy)
4. Deploy. First build takes ~2 min.
5. Settings → Domains → add `church.example.com`. Vercel issues the HTTPS
   cert automatically.

---

## 9. First admin login

1. `https://church.example.com/login`
2. Email + the temp password from `INITIAL_ADMIN_PASSWORD`
3. The UI redirects to `/me/password` with a prominent "비밀번호 변경이
   필요합니다" banner. Enter the temp password + a real one.
4. On success, all refresh tokens are revoked. You're logged out and sent to
   `/login` — sign in with the new password.

---

## 10. Backups

RDS already does daily automated backups (7-day retention from step 1). What
you need to add:

- **EFS**: AWS Backup → plan → daily snapshot of the `church-uploads`
  filesystem. Retain 30 days.
- **Monthly restore drill**: once a month, restore the latest EFS snapshot
  to a temporary mount and verify a known file reads back cleanly. Do the
  same with RDS using a point-in-time restore to a throwaway instance.

Both drills take ~15 minutes; skipping them is how organisations discover
their backups were broken at the worst possible moment.

---

## 11. Monitoring

Minimum viable:

- **CloudWatch alarm**: ALB HTTP 5xx > 10 per 5 minutes → email you.
- **CloudWatch alarm**: ECS task CPU > 80% for 10 minutes → email you.
- **UptimeRobot** (free tier): HTTPS probe of
  `https://api.church.example.com/actuator/health` every 5 minutes.
- **Sentry** (free tier): add the Spring Boot + React SDKs, env-driven DSN.
  Covered in a future TODO.

---

## 12. Rolling a new backend version

```bash
cd am-backend
TAG=v2  # bump each release
docker build --platform linux/amd64 -t church-backend:$TAG .
docker tag church-backend:$TAG $REGISTRY/church-backend:$TAG
docker tag church-backend:$TAG $REGISTRY/church-backend:latest
docker push $REGISTRY/church-backend:$TAG
docker push $REGISTRY/church-backend:latest

# Trigger ECS to roll
aws ecs update-service --cluster church-prod --service church-backend \
  --force-new-deployment --region ap-northeast-2
```

ECS stops the old task after the new one passes its health check (zero
downtime). Watch task events in the console if a deploy stalls — the usual
cause is Flyway refusing a migration because the schema diverged from
`schema_history`.

---

## 13. Rollback

If a release goes bad:

```bash
aws ecs update-service --cluster church-prod --service church-backend \
  --task-definition church-backend:<previous-revision> \
  --region ap-northeast-2
```

If the issue is a bad migration, you may need to restore from RDS backup —
that's a 10-20 min outage. Factor into the "which migrations are safe to
push without a maintenance window" judgment.

---

## 14. Known pre-launch gaps

Tracked as separate follow-ups, not blocking the first deploy:

- Full test coverage for the attendance (dept + sunday) and bulletin PDF
  modules; migration still safe without them, but you're flying blind on
  future refactors.
- Real seed data (actual cells, pastors, service teams). Easiest to enter
  through the admin UI once prod is live.
- Monthly backup restore drill (both EFS + RDS). Schedule a calendar
  reminder — undrilled backups are approximately as useful as none.
