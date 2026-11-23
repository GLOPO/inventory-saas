# Chapter 4: CI/CD, Deployment & Git Runbook

[← Chapter 3: QA Verification Guide](03-qa-playbook.md) | [Back to README](../README.md)

---

## 4.1 Backing Infrastructure Requirements

Before deploying the Spring Boot container in staging or production, provision the following external services:

1. **PostgreSQL (`15+`):** Dedicated database instance with persistent volume storage.
2. **Redis (`7+`):** In-memory cache instance accessible over private VPC/network.
3. **SMTP Relay:**
    * **Staging / QA:** Mailtrap Email Sandbox (`sandbox.smtp.mailtrap.io`).
    * **Production:** AWS SES, SendGrid, Postmark, or Mailgun.

---

## 4.2 Environment Variable Contract

Inject all configuration via environment variables (never hardcode secrets in `application.yml` or commit `.env` files to Git):

```env
# ==========================================
# SERVER & PROFILE
# ==========================================
SERVER_PORT=8080
SPRING_PROFILES_ACTIVE=prod

# ==========================================
# POSTGRESQL DATABASE
# ==========================================
SPRING_DATASOURCE_URL=jdbc:postgresql://<DB_HOST>:5432/<DB_NAME>
SPRING_DATASOURCE_USERNAME=<DB_USER>
SPRING_DATASOURCE_PASSWORD=<DB_PASSWORD>

# ==========================================
# FLYWAY SCHEMA MIGRATIONS
# ==========================================
SPRING_FLYWAY_ENABLED=true
SPRING_FLYWAY_BASELINE_ON_MIGRATE=true

# ==========================================
# REDIS CACHE
# ==========================================
SPRING_DATA_REDIS_HOST=<REDIS_HOST>
SPRING_DATA_REDIS_PORT=6379
SPRING_DATA_REDIS_PASSWORD=<REDIS_PASSWORD>

# ==========================================
# JWT AUTHENTICATION SECURITY
# ==========================================
# Must be a Base64-encoded 256-bit (32+ byte) secret key
JWT_SECRET=<BASE64_256_BIT_SECRET>
JWT_EXPIRATION_MS=86400000

# ==========================================
# TRANSACTIONAL EMAIL (SMTP)
# ==========================================
SPRING_MAIL_HOST=<SMTP_HOST>
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=<SMTP_USERNAME>
SPRING_MAIL_PASSWORD=<SMTP_PASSWORD>
SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true
```

---

## 4.3 Database Migrations (Flyway Guardrails)

* **Location:** `src/main/resources/db/migration/`
* **Naming Convention:** `V<version>__<snake_case_description>.sql` (e.g., `V1__init_schema.sql`, `V2__add_warehouse_coordinates.sql`).
* **Startup Behavior:** Flyway runs automatically during Spring Boot initialization prior to opening HTTP ports.
* **Strict Immutability Rule:** Never edit or delete a migration file that has already been applied to staging or production. Flyway validates SHA-256 checksums on startup and will deliberately fail container boot if a historical migration checksum mismatches. Always create a new incremental `V<next>__*.sql` file.

---

## 4.4 Container Build & Orchestration

### Building the Production Image
The repository uses a multi-stage `Dockerfile` (compiling the executable fat JAR with Maven/JDK 21+ and copying only the artifact into a minimal JRE runtime image):
```bash
docker build -t inventorysaas:${GIT_SHA:-latest} .
```

### Container Startup Ordering & Health Checks
When orchestrating via Docker Compose, ECS, or Kubernetes:
1. Enforce dependency health checks so the API container waits until PostgreSQL (`pg_isready -U $POSTGRES_USER`) and Redis (`redis-cli ping`) are healthy.
2. Configure container **Liveness & Readiness Probes**:
    * **Endpoint:** `GET /v3/api-docs` (or `GET /actuator/health` if Spring Boot Actuator is enabled).
    * **Initial Delay:** `20s` (allows Flyway migrations and Hibernate schema validation to complete).
    * **Interval:** `15s` | **Timeout:** `5s` | **Failure Threshold:** `3`.

---

## 4.5 Recommended CI/CD Pipeline Stages

```text
[Push / PR to dev or main]
       │
       ▼
┌────────────────────────────────────────────────────────┐
│ Stage 1: Build & Unit Tests                            │
│  $ ./mvnw clean test                                   │
└────────────────────────────────────────────────────────┘
       │
       ▼
┌────────────────────────────────────────────────────────┐
│ Stage 2: Package Executable JAR                        │
│  $ ./mvnw package -DskipTests                          │
└────────────────────────────────────────────────────────┘
       │
       ▼
┌────────────────────────────────────────────────────────┐
│ Stage 3: Docker Build & Automated E2E Smoke Test       │
│  $ docker-compose -f docker-compose.yml up -d --build  │
│  $ python test_order_flow.py   # Runs all 41 checks    │
│  $ docker-compose down -v                              │
└────────────────────────────────────────────────────────┘
       │ (Only on merge to main)
       ▼
┌────────────────────────────────────────────────────────┐
│ Stage 4: Tag, Push & Rolling Deployment                │
│  $ docker tag inventorysaas:latest $REGISTRY:$GIT_SHA  │
│  $ docker push $REGISTRY:$GIT_SHA                      │
│  $ Trigger zero-downtime rolling deploy + health check │
└────────────────────────────────────────────────────────┘
```

---

## 4.6 Git Branching & Release Protocol

1. **`main` (Protected):** Always reflects production-ready, verified code. Direct commits are prohibited; merges occur exclusively via reviewed Pull Requests from `dev` or critical `hotfix/*` branches.
2. **`dev` (Integration):** Primary development integration branch. All completed features merge here first and undergo full E2E verification (`test_order_flow.py`).
3. **`feature/<scope>`:** Every new feature or bug fix branches off `dev` (e.g., `feature/order-rerouting`, `docs/engineering-storybook`). Keep commits atomic and scoped to a single concern.

---

[← Chapter 3: QA Verification Guide](03-qa-playbook.md) | [Back to README](../README.md)