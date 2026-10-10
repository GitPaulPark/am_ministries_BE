# Single-server deploy runbook — AM Ministries

End-to-end deploy on one VPS (NCP Compact Server, AWS Lightsail, Hetzner, or a
home server behind Cloudflare Tunnel). Everything — backend, MySQL, static
frontend, HTTPS reverse proxy — runs in three containers managed by one
`docker-compose.prod.yml` file.

Target cost: **$5–15/mo** depending on host. See the comparison in
[DEPLOY.md](DEPLOY.md) if you want to see the AWS Fargate alternative.

---

## 0. One-time prerequisites

- A domain you control, with DNS you can edit. The examples use
  `church.example.com` — replace with yours.
- A VPS with Docker + Docker Compose plugin installed. Minimum 2 vCPU / 2 GB
  RAM / 20 GB SSD. Recommended host for KR users: **NCP Compact Server
  (Seoul, 2 vCPU / 4 GB, ~$12/mo)**.
- SSH access to the VPS.

### Server bootstrap (one-time, as root)

```bash
# Install Docker
curl -fsSL https://get.docker.com | sh
systemctl enable --now docker

# A non-root user for everything that follows
adduser deploy
usermod -aG docker deploy

# UFW firewall — only HTTPS + SSH open
ufw allow 22/tcp
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable

# Point the domain's A record at this server's public IP. Caddy requires
# port 80 + 443 reachable for Let's Encrypt to issue the TLS cert.
```

---

## 1. Clone the repos (as the `deploy` user)

```bash
su - deploy
mkdir -p ~/am_ministries && cd ~/am_ministries
git clone git@github.com:GitPaulPark/am_ministries_BE.git am-backend
git clone git@github.com:GitPaulPark/am_ministries_FE.git am-frontend
```

Layout on disk:
```
~/am_ministries/
├── am-backend/              Dockerfile, docker-compose.prod.yml, Caddyfile
├── am-frontend/             Vite source; `npm run build` produces dist/
└── .env.prod                You create this next — the one secrets file
```

---

## 2. Secrets file

```bash
cd ~/am_ministries
cp am-backend/.env.prod.example .env.prod
# Edit — fill in DOMAIN, TLS_EMAIL, DB passwords, JWT_SECRET, admin creds
nano .env.prod
```

Quick secret generation:
```bash
# Passwords
openssl rand -base64 32
# JWT secret (needs ≥256 bits — Spring refuses shorter)
openssl rand -base64 48
```

Lock it down so only `deploy` can read:
```bash
chmod 600 .env.prod
```

---

## 3. Install Node (frontend build)

The frontend `dist/` has to exist before Caddy starts. Do the build on the
server; keeps the compose stack self-contained.

```bash
# Node 20 LTS via nodesource — pick whatever matches your package manager
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt-get install -y nodejs
node -v   # should show v20.x
```

---

## 4. First build + first boot

```bash
cd ~/am_ministries/am-frontend
npm ci
npm run build      # outputs dist/, which Caddy will serve

cd ~/am_ministries/am-backend
docker compose -f docker-compose.prod.yml --env-file ../.env.prod up -d --build

# Watch the logs — the first boot runs Flyway V1-V9 and seeds the admin
docker compose -f docker-compose.prod.yml logs -f backend
```

You should see:
```
Successfully applied 9 migrations to schema `church`, now at version v9
Prod bootstrap: created initial ADMIN pastor@yourchurch.org with
  password_change_required=true. Sign in once and change the password immediately.
Started ChurchApplication in XX seconds
```

Caddy will reach out to Let's Encrypt and provision an HTTPS cert on the
first browser hit — may take 20–40 seconds. Watch Caddy logs:
```bash
docker compose -f docker-compose.prod.yml logs -f caddy
```

---

## 5. Verify

```bash
# From your laptop:
BASE_URL=https://church.example.com \
  ADMIN_EMAIL=pastor@yourchurch.org \
  ADMIN_PASSWORD=<your temp password> \
  ~/am_ministries/am-backend/scripts/smoke.sh
```

Should print 7 PASS lines (health, UP, public landing, rate limiter, CORS,
login, /auth/me).

Then open `https://church.example.com/login` in a browser, sign in with the
temp admin creds, and the UI will force you to `/me/password` to pick a real
password.

---

## 6. Backups

The two volumes you care about:
- `church_church-mysql-data` — all data
- `church_church-uploads` — bulletin PDFs, sermon audio

Nightly cron (as `deploy`):
```bash
mkdir -p ~/backups

cat > ~/backup.sh <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
STAMP=$(date +%Y%m%d-%H%M)
cd /home/deploy/am_ministries/am-backend

# MySQL — logical dump, UTF-8-safe
docker compose -f docker-compose.prod.yml exec -T mysql \
  mysqldump -uroot -p"$(grep MYSQL_ROOT_PASSWORD ../.env.prod | cut -d= -f2)" \
  --single-transaction --routines --triggers church \
  | gzip > ~/backups/church-db-$STAMP.sql.gz

# Uploads — tarball from the volume
docker run --rm -v church_church-uploads:/data -v $HOME/backups:/out alpine \
  tar czf /out/church-uploads-$STAMP.tgz -C /data .

# Keep 30 days, drop older
find ~/backups -name 'church-*' -mtime +30 -delete
EOF
chmod +x ~/backup.sh

# Run nightly at 3am KST
( crontab -l 2>/dev/null; echo "0 3 * * * /home/deploy/backup.sh" ) | crontab -
```

**Monthly restore drill** — once a month, actually test a restore:
```bash
# Pick the latest DB dump
LATEST=$(ls -t ~/backups/church-db-*.sql.gz | head -1)
# Spin up a throwaway MySQL, restore into it, verify a known row reads back
docker run --rm -d --name restore-test -e MYSQL_ROOT_PASSWORD=t -p 3307:3306 mysql:8.4
sleep 20
zcat "$LATEST" | docker exec -i restore-test mysql -uroot -pt
docker exec restore-test mysql -uroot -pt -e "SELECT email FROM church.users LIMIT 1"
docker rm -f restore-test
```

Push the backups off-box (S3-compatible bucket, Backblaze B2, etc.) at least
weekly — a single-server disaster takes out local backups too.

---

## 7. Rolling a new version

```bash
cd ~/am_ministries/am-frontend
git pull && npm ci && npm run build

cd ~/am_ministries/am-backend
git pull
docker compose -f docker-compose.prod.yml --env-file ../.env.prod up -d --build

# Verify
~/am_ministries/am-backend/scripts/smoke.sh
```

Downtime: ~2–3 seconds for the backend container to restart. Caddy keeps
serving the previous frontend bundle until the new build finishes, so the
public site stays up during the rebuild.

---

## 8. Monitoring

Minimum viable:
- **UptimeRobot** (free): HTTPS probe of `https://church.example.com/actuator/health`
  every 5 minutes.
- **systemd + Docker**: everything is `restart: unless-stopped` so a reboot or
  container crash recovers automatically.
- **Sentry** (optional): set `SENTRY_DSN` in `.env.prod` and restart the
  backend. Frontend DSN goes in Vercel-style env — for single-server, bake
  it into the frontend build with `VITE_SENTRY_DSN=xxx npm run build`.

---

## 9. SSH + patching

```bash
# Monthly OS patches
sudo apt update && sudo apt upgrade -y

# Docker image updates (MySQL, Caddy — base images)
cd ~/am_ministries/am-backend
docker compose -f docker-compose.prod.yml pull mysql caddy
docker compose -f docker-compose.prod.yml --env-file ../.env.prod up -d
```

Plan 15 min a month for this. Set a calendar reminder — unpatched boxes end
badly.

---

## 10. Rollback

Backend got worse? Roll the backend repo and rebuild:
```bash
cd ~/am_ministries/am-backend
git log --oneline -10
git checkout <previous-good-sha>
docker compose -f docker-compose.prod.yml --env-file ../.env.prod up -d --build backend
```

Frontend got worse?
```bash
cd ~/am_ministries/am-frontend
git checkout <previous-good-sha>
npm ci && npm run build
# Caddy automatically picks up the new dist/ — no restart needed.
```

If a Flyway migration is the culprit, you'll need to restore the MySQL
backup — 5–10 min outage. Factor that in when deciding "is this migration
safe to push without a maintenance window."

---

## 11. What this setup does NOT give you

- **High availability** — if the server reboots, the site is down 1–2 min.
  Fine for a church site; terrible for a bank. If you need HA, go back to
  the AWS Fargate path in [DEPLOY.md](DEPLOY.md).
- **Auto-scaling** — one box handles your load. If the choir goes viral on
  TikTok and 50k people visit on Sunday morning, the rate limiter will keep
  the DB up but response times degrade.
- **Managed backups** — the cron above is on you. A missed drill means your
  "backups" might be corrupt. Set calendar reminders.
