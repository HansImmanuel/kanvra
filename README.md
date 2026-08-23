# Kanvra

A lightweight Trello/Jira-style project and task management web application. Kanvra demonstrates a practical
event-driven architecture: **Java 21 + Spring Boot**, **PostgreSQL** as the source of truth, **Apache Kafka**
for asynchronous domain-event processing via a **transactional outbox**, and **WebSocket/STOMP** for real-time
board updates.

> Product scope and contracts are defined in `docs/` (`PRD.md`, `SPEC.md`, `TECH_DOC.md`, `AGENT.md`). These
> documents are the source of truth; keep them aligned with the implementation.

## Stack

| Layer          | Technology                                            |
|----------------|-------------------------------------------------------|
| Frontend       | Next.js + TypeScript (App Router, Tailwind CSS)        |
| Backend        | Java 21 + Spring Boot (modular monolith)              |
| Persistence    | Spring Data JPA / Hibernate + PostgreSQL (Flyway)     |
| Messaging      | Apache Kafka (KRaft mode) via transactional outbox    |
| Realtime       | Spring WebSocket + STOMP (@stomp/stompjs client)      |
| Security       | Spring Security, cookie-based JWT + CSRF double-submit|
| Build / CI     | Maven, GitHub Actions                                  |

## Local development

### 1. Start infrastructure

```bash
# from repo root
docker compose up -d
```

This starts:

- **PostgreSQL 17** on `localhost:5432` (db/user/pass = `kanvra` / `kanvra` / `kanvra_dev`)
- **Apache Kafka 3.9 (KRaft)** — dual listeners: `localhost:29092` (EXTERNAL, for host tools/apps) and `kafka:9092` (INTERNAL, for in-network containers)
- **Kafka UI** on `http://localhost:8081`

### 2. Run the backend

```bash
cd backend
./mvnw spring-boot:run     # (Windows: .\mvnw.cmd spring-boot:run)
```

The API is served at `http://localhost:8080/api/v1`. Actuator health is at
`http://localhost:8080/actuator/health`.

### 3. Run the frontend

```bash
cd frontend
npm install
npm run dev        # http://localhost:3000
```

`/api/*` requests are proxied to the backend (`KANVRA_BACKEND_URL`, default
`http://localhost:8080`). The board's WebSocket connects directly to the backend
`/ws` endpoint (same-site cross-origin); override with `NEXT_PUBLIC_WS_URL`.

### 4. Tests

```bash
# backend (unit + Testcontainers integration tests; the latter skip locally if Docker is unavailable)
cd backend
./mvnw test

# frontend
cd frontend
npm run typecheck
npm run test
npm run build
```

### Configuration via environment variables

Sensitive/non-default settings are supplied through environment variables (see `.env.example`):

| Variable                      | Default                                        | Purpose                       |
|-------------------------------|------------------------------------------------|-------------------------------|
| `KANVRA_DB_URL`               | `jdbc:postgresql://localhost:5432/kanvra`      | JDBC URL                      |
| `KANVRA_DB_USERNAME`          | `kanvra`                                       | DB user                       |
| `KANVRA_DB_PASSWORD`          | `kanvra_dev`                                   | DB password                   |
| `KANVRA_KAFKA_BOOTSTRAP_SERVERS` | `localhost:29092`                            | Kafka brokers (EXTERNAL listener; `9092` is the in-network listener) |
| `KANVRA_JWT_SECRET`           | (dev default)                                  | HMAC secret for JWT signing   |
| `KANVRA_CORS_ORIGINS`         | `http://localhost:3000`                        | Allowed frontend origins      |

## Known gaps (documented decisions)

- **Dead-letter handling** is table-based since Sprint 4: poison messages (malformed JSON, missing `eventId`)
  are parked in the `dead_letter_events` table and acked so they cannot block a Kafka partition; transient
  failures still rethrow for Kafka redelivery. Recovery is a documented manual/ops step (`GET
  /api/v1/dead-letters` lists recent rows; a scheduled job purges after 30 days). See `TECH_DOC.md` §20.
- **Access tokens are not revocable** — exposure is bounded by their short (~30 min) expiry. Refresh tokens *are*
  revocable server-side since Sprint 2: rotation revokes the old token, reuse detection revokes the family, and
  logout revokes all active refresh tokens (see `SPEC.md` §3.4/§3.5).
- **Rate limiter is per-instance** (in-memory bucket, not distributed) — fine for the single-instance MVP, but the
  effective limit multiplies by replica count if the backend is ever scaled out.
- **Outbox publisher retries are throttled** with exponential backoff + jitter (bounded at 30s) so a down broker
  is not hammered while the backlog is retained safely in PostgreSQL.
- **Real-time fallback**: if the WebSocket endpoint is unreachable for a sustained window, the frontend falls back
  to polling the board REST endpoint (authoritative resync) and periodically re-checks the socket.
- **Frontend Dockerfile** is not yet provided (applications run locally in dev); the backend image builds in CI
  so it does not bitrot. Containerizing the Next.js app is deferred per `TECH_DOC.md` §21.

## Repository layout

```text
backend/    Spring Boot modular monolith
frontend/   Next.js application (App Router, Tailwind)
infra/      Docker Compose + container tooling
docs/       PRD / SPEC / TECH_DOC / AGENT (source of truth)
```
