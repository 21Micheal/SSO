# IDM Platform — Identity Provider (IdP)

Keycloak 26 running in Docker, shared by all systems in the platform (DMS,
financial-system, and any future additions). Each system is registered as a
separate OIDC client in the `idp-dev` realm.

---

## Contents

1. [Prerequisites](#1-prerequisites)
2. [First-time setup](#2-first-time-setup)
3. [Starting and stopping](#3-starting-and-stopping)
4. [Admin console](#4-admin-console)
5. [Browser URL vs internal URL — the split-horizon explanation](#5-browser-url-vs-internal-url)
6. [Financial-backed users](#6-financial-backed-users)
7. [Registered OIDC clients](#7-registered-oidc-clients)
8. [Adding a new client for a future system](#8-adding-a-new-client)
9. [How to configure a client application](#9-how-to-configure-a-client-application)
10. [User federation](#10-user-federation)
11. [Backup and restore](#11-backup-and-restore)
12. [Day-2 operations](#12-day-2-operations)
13. [Production checklist](#13-production-checklist)

---

## 1. Prerequisites

- Docker Engine 24+ and Docker Compose v2
- Port **8080** free on the host (Keycloak admin + OIDC endpoints)
- No non-containerised Keycloak or Postgres installed locally

---

## 2. First-time setup

```bash
cd ~/Projects/SSO
cp .env.example .env
nano .env          # set KC_BOOTSTRAP_ADMIN_PASSWORD and KC_DB_PASSWORD
```

`.env` is gitignored. Never commit it.

For financial federation, set the same `FINANCIAL_INTERNAL_IDP_API_KEY` in:

- `~/Projects/SSO/.env`
- `~/Projects/financial-system/.env`

Use a **separate** `DMS_INTERNAL_IDP_API_KEY` in SSO, IDM, and financial (launcher probe).

- Get it with:
```bash
openssl rand -hex 32
```
- or
```py
python3 -c "import secrets; print(secrets.token_hex(32))"
```
---

## 3. Starting and stopping

```bash
# Start (detached). The first run builds both Keycloak provider jars.
docker compose up -d --build

# First startup takes ~60 s while Keycloak initialises the database
# and imports the realm. Watch progress:
docker compose logs -f keycloak

# You'll see: "Keycloak 26.0 on JVM (powered by Quarkus) started"
# then add financial-user-storage in admin (or reset the volume) and sign in
# with financial-system users.

# Stop (keeps data in the keycloak_db_data volume)
docker compose down

# Full teardown including data volume (resets all realm/user data)
docker compose down -v
```

> ⚠️ `docker compose down -v` deletes all realm configuration, users, and
> sessions. Admin console changes made after the initial import are lost.
> After this, the next `docker compose up` re-imports from `realm-config/`.

---

## 4. Admin console

| URL | http://localhost:8080/admin |
|---|---|
| Username | value of `KC_BOOTSTRAP_ADMIN_USERNAME` in `.env` |
| Password | value of `KC_BOOTSTRAP_ADMIN_PASSWORD` in `.env` |

After login, switch to the **`idp-dev`** realm (top-left dropdown — default
is the built-in `master` realm).

**OIDC discovery endpoint** (useful for debugging):
```
http://localhost:8080/realms/idp-dev/.well-known/openid-configuration
```

---

## 5. Browser URL vs internal URL

This is the most important concept when wiring client applications to this IdP.

### The problem

Keycloak embeds a URL into every JWT it issues as the `iss` (issuer) claim.
Applications verify this claim when validating tokens. In a Docker Compose
environment, two different audiences need to reach Keycloak:

| Who | Route to Keycloak | Hostname |
|---|---|---|
| **Browser** (dev machine) | Host port 8080 → container | `http://localhost:8080` |
| **Backend app** (inside Docker) | Docker service DNS | `http://keycloak:8080` |

If the JWT `iss` = `http://keycloak:8080/realms/idp-dev`, the browser cannot
follow login redirects (Docker service names don't resolve on the host).

If the JWT `iss` = `http://localhost:8080/realms/idp-dev`, a backend container
trying to reach `localhost:8080` talks to itself, not Keycloak.

### The solution

`docker-compose.yml` configures Keycloak with:

```
KC_HOSTNAME=http://localhost:8080   # fixes the JWT issuer to localhost
KC_HOSTNAME_STRICT=false            # still accepts requests at keycloak:8080
```

Result:
- **JWT `iss`** = `http://localhost:8080/realms/idp-dev` — browser login works ✓
- Keycloak accepts connections from `http://keycloak:8080` — backend JWKS
  calls work without routing through the host ✓

### How to configure each client application

Every app that validates tokens must configure **two separate values**:

| Config key | Value | Purpose |
|---|---|---|
| OIDC discovery / JWKS URL | `http://keycloak:8080/realms/idp-dev` | Docker-internal network access |
| Expected issuer (for JWT validation) | `http://localhost:8080/realms/idp-dev` | Must match the `iss` claim |

Example (mozilla-django-oidc, Django):
```python
# settings.py
OIDC_OP_JWKS_ENDPOINT = "http://keycloak:8080/realms/idp-dev/protocol/openid-connect/certs"
OIDC_OP_AUTHORIZATION_ENDPOINT = "http://localhost:8080/realms/idp-dev/protocol/openid-connect/auth"
OIDC_OP_TOKEN_ENDPOINT = "http://keycloak:8080/realms/idp-dev/protocol/openid-connect/token"
OIDC_OP_USER_ENDPOINT = "http://keycloak:8080/realms/idp-dev/protocol/openid-connect/userinfo"
OIDC_OP_ISSUER = "http://localhost:8080/realms/idp-dev"
```

The pattern: use `keycloak:8080` for machine-to-machine calls (token exchange,
JWKS fetch, userinfo), and `localhost:8080` wherever the **browser** must be
redirected (authorization endpoint) or wherever an issuer **string** is
compared (not a network call).

> **Why `localhost` for the authorization endpoint?** The authorization endpoint
> URL is sent to the browser as a redirect. The browser must be able to open it.
> All other endpoints are called server-to-server and should use the internal
> Docker hostname for efficiency and reliability.

---

## 6. Financial-backed users

This realm does not seed application users. Keycloak reads identity from the
**financial system** through the `financial-user-storage` federation provider.
Passwords, profile write-through, and `financial_role` claims come from
`http://financial-backend:8001/api/v1/internal/idp`.

DMS is a relying party. The `dms-live-authorization-mapper` only loads
`dms_role` / permissions (by email, because federated users have no `dms_user_id`).
Do **not** enable `dms-user-storage` as the identity backend.

Example decoded token claims for `financial-client`:
```json
{
  "iss": "http://localhost:8080/realms/idp-dev",
  "preferred_username": "alice@example.com",
  "financial_user_id": "...",
  "financial_role": "client_user",
  "financial_permissions": ["finance.read"],
  "organization_id": "...",
  "is_staff": false
}
```

---

## 7. Registered OIDC clients

| Client ID | App | Redirect URIs | Type |
|---|---|---|---|
| `dms-client` | Document Management System | `http://localhost:3000/*`, `http://localhost:8000/*` | Public (PKCE) |
| `financial-client` | Financial System | `http://localhost:3001/*`, `http://localhost:8001/*` | Public (PKCE) |

Both clients use **Authorization Code + PKCE** (`pkce.code.challenge.method=S256`).
No client secret is needed or expected.

---

## 8. Adding a new client

To register a third system (e.g., an HR portal), add a new entry to the
`clients` array in `realm-config/idp-dev.json`:

```json
{
  "clientId": "hr-client",
  "name": "HR Portal",
  "enabled": true,
  "publicClient": true,
  "protocol": "openid-connect",
  "standardFlowEnabled": true,
  "implicitFlowEnabled": false,
  "directAccessGrantsEnabled": false,
  "redirectUris": ["http://localhost:3200/*"],
  "webOrigins": ["http://localhost:3200"],
  "attributes": {
    "pkce.code.challenge.method": "S256",
    "post.logout.redirect.uris": "http://localhost:3200/*"
  }
}
```

Then either:

**Option A — modify via Admin Console** (persists in DB, no restart needed):
Realm → Clients → Create client → fill in the form.

**Option B — add to `realm-config/idp-dev.json` and reset volume** (reproducible):
```bash
# Edit realm-config/idp-dev.json, then:
docker compose down -v   # wipes DB — re-imports fresh on next up
docker compose up -d
```

For a team environment, prefer Option B so the realm config stays in Git.

---

## 9. How to configure a client application

When wiring any system to this IdP, provide these values (replace
`<client-id>` with `dms-client` or `financial-client`):

| Setting | Value |
|---|---|
| Realm issuer | `http://localhost:8080/realms/idp-dev` |
| Client ID | `dms-client` / `financial-client` |
| Client secret | *(none — public client)* |
| Authorization endpoint | `http://localhost:8080/realms/idp-dev/protocol/openid-connect/auth` |
| Token endpoint | `http://keycloak:8080/realms/idp-dev/protocol/openid-connect/token` |
| JWKS endpoint | `http://keycloak:8080/realms/idp-dev/protocol/openid-connect/certs` |
| Userinfo endpoint | `http://keycloak:8080/realms/idp-dev/protocol/openid-connect/userinfo` |
| End session endpoint | `http://localhost:8080/realms/idp-dev/protocol/openid-connect/logout` |

Also join `idp-network` as an external network so the `keycloak` hostname resolves:

```yaml
# In the app's docker-compose.yml:
networks:
  idp-network:
    external: true
  # ... your app's own networks

services:
  backend:
    networks:
      - idp-network
      - default
```

---

## 10. User federation

Identity backend: `providers/financial-keycloak-provider` (`financial-user-storage`)
calls `http://financial-backend:8001/api/v1/internal/idp` with
`Authorization: Bearer <FINANCIAL_INTERNAL_IDP_API_KEY>`.

DMS mapper: `providers/dms-keycloak-provider` is **claims-only**. It must not
validate passwords or create users.

### Build and deploy

The Dockerfile builds **both** Maven jars and runs `kc.sh build`.

```bash
cd ~/Projects/SSO
docker compose up -d --build keycloak
```

After any SPI change, rebuild the image. Stale Keycloak-local users can shadow
federation — disable the old DMS user-storage component, add `financial-user-storage`,
and remove stale local users if lookup looks wrong.

### Enable financial-user-storage

Realm import may not attach the SPI until you add it in admin (or reset the volume):

1. Realm `idp-dev` → User federation → Add provider → `financial-user-storage`.
2. Base URL `http://financial-backend:8001/api/v1/internal/idp`.
3. API key = `FINANCIAL_INTERNAL_IDP_API_KEY`.
4. Disable any existing `dms-user-storage` component.

Creating a user from the Keycloak admin console write-throughs to the financial system.

### Token mappers

- `financial-client`: `financial-live-authorization-mapper` → `financial_role`, `financial_permissions`, `organization_id`.
- `dms-client`: `dms-live-authorization-mapper` → `dms_role`, `dms_permissions`, `dms_groups` (lookup by email).

## 11. Backup and restore

### Backup Keycloak's database

```bash
# Dump from the running container
docker compose exec -T keycloak-db \
  pg_dump -U keycloak keycloak > keycloak_$(date +%F).sql

# Restore
docker compose exec -T keycloak-db \
  psql -U keycloak keycloak < keycloak_2026-09-21.sql
```

### Export realm config from the running admin console

For a point-in-time export that captures admin console changes:

```bash
docker compose exec keycloak \
  /opt/keycloak/bin/kc.sh export \
  --dir /opt/keycloak/data/import \
  --realm idp-dev \
  --users realm_file
```

This writes back to the mounted `realm-config/` directory, updating
`idp-dev.json` with all current clients, roles, and users.

---

## 12. Day-2 operations

```bash
# Tail Keycloak logs
docker compose logs -f keycloak

# Restart Keycloak only (e.g. after config change)
docker compose restart keycloak

# Update Keycloak image
# Edit docker-compose.yml: change quay.io/keycloak/keycloak:26.0 → :26.x
docker compose build keycloak
docker compose up -d keycloak

# Check health
docker compose ps
```

---

## 13. Production checklist

When promoting this IdP to staging or production:

- [ ] Replace `start-dev` with `start` in `docker-compose.yml`
- [ ] Set `KC_HOSTNAME=https://auth.yourdomain.com` (real HTTPS URL)
- [ ] Set `KC_HOSTNAME_STRICT=true`
- [ ] Add a TLS-terminating reverse proxy (nginx/traefik) in front of Keycloak
- [ ] Set `sslRequired: "external"` in the realm JSON
- [ ] Remove any old local realm users that duplicate DMS users
- [ ] Use a secrets manager (Docker secrets, Vault) instead of `.env`
- [ ] Restrict `/api/v1/internal/idp/*` so only the Keycloak network can reach it
- [ ] Enable brute force protection in the realm settings
- [ ] Set up Postgres backups on a schedule
