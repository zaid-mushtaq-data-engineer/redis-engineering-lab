# Redis Engineering Lab

A Spring Boot backend used to **learn Redis by solving realistic backend problems** (caching, invalidation, rate limiting, and more). The domain is a small Product & Inventory API; MySQL is the source of truth and Redis is an optional performance layer.

Design documentation lives under [`files/`](files/) — start with [`files/PROJECT.md`](files/PROJECT.md) and the stage plan in [`files/ROADMAP.md`](files/ROADMAP.md).

## Prerequisites

- Java 21
- Maven 3.9+
- Docker Compose v2

## Infrastructure (MySQL + Redis)

1. Copy environment template and edit secrets (do not commit `.env`):

   ```bash
   cp .env.example .env
   ```

2. Start containers:

   ```bash
   docker compose -f ops/compose.dev.yml --env-file .env up -d
   ```

3. Wait until both services are healthy:

   ```bash
   docker compose -f ops/compose.dev.yml ps
   ```

MySQL listens on `127.0.0.1:3306` (data persisted in a Docker volume). Redis listens on `127.0.0.1:6379` with **128 MB** `maxmemory`, **noeviction**, and **no persistence** (a restart empties Redis).

## Run the application

Spring Boot does not read `.env` automatically. Export the same variables (or configure them in your IDE run configuration):

```bash
set -a && source .env && set +a
export SPRING_PROFILES_ACTIVE=dev
mvn spring-boot:run
```

The `dev` profile is required: the datasource is defined only in `application-dev.yml`.

The app listens on [http://localhost:8080](http://localhost:8080). Actuator health is always available; dev Redis sandbox endpoints require the `dev` profile and `app.dev-endpoints.enabled=true` (set in `application-dev.yml`).

## Stage 2 — Redis sandbox (dev only)

All `/api/dev/**` routes require header `X-Admin-Key` (same value as `ADMIN_API_KEY`).

Create a key with TTL and compare with Redis CLI:

```bash
curl -s -X POST http://localhost:8080/api/dev/redis/keys \
  -H "X-Admin-Key: $ADMIN_API_KEY" -H "Content-Type: application/json" \
  -d '{"key":"demo","value":"hello","ttlSeconds":60}'

docker exec redislab-redis redis-cli GET redis-lab:dev:sandbox:demo
docker exec redislab-redis redis-cli TTL redis-lab:dev:sandbox:demo
```

JDK vs String serialization (learning demo):

```bash
curl -s -X POST http://localhost:8080/api/dev/redis/serialization/jdk-demo \
  -H "X-Admin-Key: $ADMIN_API_KEY"

docker exec redislab-redis redis-cli GET redis-lab:dev:sandbox:jdk-demo
docker exec redislab-redis redis-cli GET redis-lab:dev:sandbox:string-demo
```

The `jdk-demo` value is **JDK-serialized binary** (`\xac\xed…` in CLI). The `string-demo` value is **plain text** — this is why the project uses `StringRedisTemplate` for cache and Lua ([R-09]).

Stage 2 checks:

| Check | Expected |
|-------|----------|
| Missing `X-Admin-Key` on `/api/dev/redis/keys` | `401` `INVALID_ADMIN_KEY` |
| GET missing sandbox key | `404` `REDIS_KEY_NOT_FOUND` |
| `docker stop redislab-redis` → sandbox call | `503` `REDIS_UNAVAILABLE` |
| Health while Redis stopped | Overall `UP`, `redis` status `DEGRADED` (HTTP 200) |
| `docker start redislab-redis` (no app restart) | Sandbox works again |
| Sandbox call while Redis down | Fails quickly (not ~1s), thanks to `REJECT_COMMANDS` |

## Stage 3 — Product and inventory data layer

Flyway applies `V1`–`V3` on startup (`products`, `inventory`, list index). No new HTTP APIs until Stage 4.

Run repository integration tests (Docker required):

```bash
mvn test
```

Optional **10,000 product catalog** for later performance work — off by default. In `application-dev.yml` set `app.dev.seed-catalog: true`, restart once, then set back to `false`:

```bash
# verify count
docker exec redislab-mysql mysql -uredislab -p"$MYSQL_PASSWORD" redis_engineering_lab \
  -e "SELECT COUNT(*) FROM products; SELECT version FROM flyway_schema_history;"
```

Expect **3** Flyway migration rows after a successful upgrade from Stage 1/2.

## Stage 4 — Basic APIs (MySQL only)

All **13** business endpoints from `files/API.md` are available. Services use **interfaces** (`ProductService`, `InventoryService`) with `*Impl` classes; writes go through `TransactionTemplate`. **No Redis** in product/inventory services yet (Stage 5+).

| Area | Notes |
|------|--------|
| Swagger UI | Dev profile only: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) |
| Admin auth | Header `X-Admin-Key` (same as Stage 2) |
| Postman | Import `postman/redis-engineering-lab.postman_collection.json`; set `baseUrl` and `adminKey` |
| Baseline latency | Optional: note response times on public GETs before enabling cache in Stage 5 |

Quick smoke:

```bash
curl -s http://localhost:8080/api/products?page=0&size=20 | head
curl -s -H "X-Admin-Key: $ADMIN_API_KEY" http://localhost:8080/api/admin/products/1
```

Run tests:

```bash
mvn test
```

## Stage 1 verification

Redis limits:

```bash
docker exec redislab-redis redis-cli CONFIG GET maxmemory
docker exec redislab-redis redis-cli CONFIG GET maxmemory-policy
docker exec redislab-redis redis-cli CONFIG GET save
```

Expected: `maxmemory` = `134217728`, `maxmemory-policy` = `noeviction`, `save` empty.

Health (with infra up and app running):

```bash
curl -s http://localhost:8080/actuator/health
```

Expected: overall `UP`, with `db` and `redis` components `UP`.

Fail-fast admin key: start the app **without** `ADMIN_API_KEY` set — startup should fail (missing placeholder or validation on empty key).

Optional: `docker restart redislab-redis` — Redis data is cleared (no persistence).

## Versions (pinned)

| Component | Version |
|-----------|---------|
| Spring Boot | 3.5.16 |
| MySQL (Compose) | 8.0 |
| Redis (Compose) | 7.2 |

## Project layout

| Path | Purpose |
|------|---------|
| `src/main/java/com/redislab/` | Application code |
| `src/main/resources/db/migration/` | Flyway scripts (empty until Stage 3) |
| `ops/compose.dev.yml` | Local MySQL and Redis |
| `files/` | Architecture and roadmap docs |
