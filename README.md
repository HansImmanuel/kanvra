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
| Frontend       | Next.js + TypeScript (coming soon)                    |
| Backend        | Java 21 + Spring Boot (modular monolith)              |
| Persistence    | Spring Data JPA / Hibernate + PostgreSQL (Flyway)     |
| Messaging      | Apache Kafka (KRaft mode) via transactional outbox    |
| Realtime       | Spring WebSocket + STOMP (coming soon)                |
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
- **Apache Kafka 3.9 (KRaft)** on `localhost:9092`
- **Kafka UI** on `http://localhost:8081`

### 2. Run the backend

```bash
cd backend
./mvnw spring-boot:run     # (Windows: .\mvnw.cmd spring-boot:run)
```

The API is served at `http://localhost:8080/api/v1`. Actuator health is at
`http://localhost:8080/actuator/health`.

### 3. Tests

```bash
cd backend
./mvnw test
```

### Configuration via environment variables

Sensitive/non-default settings are supplied through environment variables (see `.env.example`):

| Variable                      | Default                                        | Purpose                       |
|-------------------------------|------------------------------------------------|-------------------------------|
| `KANVRA_DB_URL`               | `jdbc:postgresql://localhost:5432/kanvra`      | JDBC URL                      |
| `KANVRA_DB_USERNAME`          | `kanvra`                                       | DB user                       |
| `KANVRA_DB_PASSWORD`          | `kanvra_dev`                                   | DB password                   |
| `KANVRA_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092`                            | Kafka brokers                 |
| `KANVRA_JWT_SECRET`           | (dev default)                                  | HMAC secret for JWT signing   |
| `KANVRA_CORS_ORIGINS`         | `http://localhost:3000`                        | Allowed frontend origins      |

## Known gaps (documented decisions)

- **Dead-letter topic (DLT)** for failed consumer messages is deferred — see `TECH_DOC.md` §20. Consumer
  failures are logged and retried via Kafka's built-in retry; a DLT will be added once consumers are proven stable.
- No server-side token blacklist for auth; exposure is bounded by short access-token expiry — see `SPEC.md` §3.

## Repository layout

```text
backend/    Spring Boot modular monolith
frontend/   Next.js application (to be scaffolded)
infra/      Docker Compose + container tooling
docs/       PRD / SPEC / TECH_DOC / AGENT (source of truth)
```
