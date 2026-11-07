# Redis Engineering Lab

A Spring Boot backend used to **learn Redis by solving realistic backend problems** (caching, invalidation, rate limiting, and more). The domain is a small Product & Inventory API; MySQL is the source of truth and Redis is an optional performance layer.

Design documentation lives under [`files/`](files/) — start with [`files/PROJECT.md`](files/PROJECT.md) and the stage plan in [`files/ROADMAP.md`](files/ROADMAP.md).

**Phase 1 learner docs** (as-built narrative): [`docs/architecture.md`](docs/architecture.md), [`docs/redis-fundamentals.md`](docs/redis-fundamentals.md), [`docs/experiments.md`](docs/experiments.md).

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

All **13** business endpoints from `files/API.md` are available. Services use **interfaces** (`ProductService`, `InventoryService`) with `*Impl` classes; writes go through `TransactionTemplate`. Public product detail, paginated lists, and public inventory use Redis cache-aside (Stages 5–7).

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

## Stage 5 — Product detail cache-aside

`GET /api/products/{id}` uses cache-aside. Values are plain JSON at `redis-lab:dev:product:{id}` with TTL **15 minutes** (`app.redis.ttl.product`). Misses load from MySQL and `SET` the key. Missing or inactive products are **not** cached. Admin updates and status changes delete that key **after** the MySQL commit, and only when the row actually changed. Redis errors are logged and the request still uses MySQL (fail-open). A value that is not valid JSON is deleted and treated as a miss.

Watch the app log for `CACHE_MISS` then `CACHE_HIT` on two GETs of the same id. Inspect Redis:

```bash
docker exec redislab-redis redis-cli GET redis-lab:dev:product:1
docker exec redislab-redis redis-cli TTL redis-lab:dev:product:1
```

`TTL` should be at most about **900** seconds. Stop Redis and repeat the public GET: the response stays `200` from MySQL.

## Stage 6 — Product-list caching and invalidation

`GET /api/products?page=&size=` uses cache-aside. Each page is stored at `redis-lab:dev:products:page:{page}:size:{size}` with TTL **15 minutes** (`app.redis.ttl.product-list`). Creating a product or any **changed** update/activate/deactivate clears **all** list keys after MySQL commit using `SCAN` + `UNLINK` (never `KEYS`). Admin product list never touches Redis.

Out-of-range empty pages (`page > 0`, `content: []`) are **not** cached ([R-15]) so random page numbers cannot fill Redis.

```bash
curl -s "http://localhost:8080/api/products?page=0&size=20"
docker exec redislab-redis redis-cli SCAN 0 MATCH redis-lab:dev:products:page:* COUNT 100
```

After a create or catalog change, repeat `SCAN` — list keys should be gone until the next public list GET refills them. Stop Redis: public list still returns `200` from MySQL.

## Stage 7 — Inventory caching and invalidation

`GET /api/inventory/{productId}` uses cache-aside. Values are plain JSON at `redis-lab:dev:inventory:{productId}` with TTL **30 seconds** (`app.redis.ttl.inventory`). Only active products are cached. Missing or inactive products return `404` and are not stored. Admin inventory reads never touch Redis.

A changed set, increment, or decrement deletes the key **after** the MySQL commit. Activating or deactivating a product does the same. An unchanged quantity leaves the key in place. If a delete is missed, the value can be stale for at most 30 seconds.

```bash
curl -s http://localhost:8080/api/inventory/1
docker exec redislab-redis redis-cli GET redis-lab:dev:inventory:1
docker exec redislab-redis redis-cli TTL redis-lab:dev:inventory:1
```

Two GETs log `CACHE_MISS` then `CACHE_HIT`. `TTL` is at most **30**. Stop Redis and the public GET still returns `200` from MySQL.

## Stage 8 — Per-IP token bucket rate limiting

Public **GET** endpoints share one token bucket per client IP: `GET /api/products/{id}`, `GET /api/products`, and `GET /api/inventory/{productId}`. Defaults: capacity **10**, refill **1 token/s**, bucket state in Redis Hash `redis-lab:dev:rate-limit:{ip}` via Lua (`redis/token_bucket.lua`). Admin, actuator, and Swagger are not limited.

Successful responses include `X-RateLimit-Limit`, `X-RateLimit-Remaining`, and `X-RateLimit-Reset` when Redis is up (including `404`). The 11th rapid request returns **429** with `Retry-After`, the same headers, and JSON `RATE_LIMIT_EXCEEDED`. If Redis is unavailable, requests proceed with **no** rate-limit headers (`RATE_LIMIT_REDIS_FAILURE` in logs). Unit tests disable limiting via `app.rate-limit.enabled: false` in the test profile.

```bash
for i in $(seq 1 11); do curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/products/1; done
docker exec redislab-redis redis-cli HGETALL redis-lab:dev:rate-limit:127.0.0.1
```

Expect `200` for requests 1–10 (`Remaining` counts down), then `429`; after ~1s refill, one more `200`. With `trust-forwarded-headers: false`, spoofed `X-Forwarded-For` does not change the bucket key.

## Stage 9 — Testing and failure verification

Automated integration tests use the Spring profile **`it`**: real MySQL and Redis **7.2** (128 MB, `noeviction`) via Testcontainers. Keys use the `test` prefix (`redis-lab:test:*`). Fast unit tests keep profile **`test`** with a mock Redis.

```bash
mvn verify   # Docker required
```

Coverage includes cache-aside, invalidation, optimistic locking, inventory concurrency, token-bucket limits (HTTP + Lua), stampede reproduction (`StampedeReproductionIT` — documents Phase 2 need), Redis-unreachable fail-open, admin/error format, and actuator health. Manual compose drills (stop Redis, restart, OOM) are recorded in [`docs/experiments.md`](docs/experiments.md) with the §21.2 checklist.

## Stage 10 — Phase 1 review and documentation

Phase 1 is **complete** as a documented baseline for Phase 2. Read [`docs/architecture.md`](docs/architecture.md) for keys, flows, and failure modes; [`docs/redis-fundamentals.md`](docs/redis-fundamentals.md) for CLI and structure choices; [`docs/experiments.md`](docs/experiments.md) for drill results, latency samples, and the §21.2 map.

### Onboarding (fresh clone)

1. Java 21, Maven 3.9+, Docker Compose v2  
2. `cp .env.example .env` — set secrets locally (never commit `.env`)  
3. `docker compose -f ops/compose.dev.yml --env-file .env up -d`  
4. `export SPRING_PROFILES_ACTIVE=dev` and source `.env`; `mvn spring-boot:run`  
5. Import `postman/redis-engineering-lab.postman_collection.json`  
6. `mvn verify` (Docker) for the full test suite  

### Phase 1 exit checklist

| Criterion | Evidence |
|-----------|----------|
| 13 endpoints per [`files/API.md`](files/API.md) | Controllers + Postman collection |
| 3 cache families + invalidation matrix | `CacheInvalidationIT`, [`docs/architecture.md`](docs/architecture.md) |
| Token bucket under concurrency | `RateLimitIntegrationIT`, `RateLimitLuaIT` |
| Redis stop/restart drills | [`docs/experiments.md`](docs/experiments.md) |
| Stampede reproduced | `StampedeReproductionIT` (entity loads > 1) |
| No `KEYS` in application code | `SCAN` list eviction; audit in experiments |
| Secrets not in Git | `.gitignore` + `.env.example` |
| Docs match code | `docs/*` + this README |

**Next:** Phase 2 Stage 2.1 — distributed lock on cache miss; re-run `StampedeReproductionIT` targeting ~1 MySQL load.

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
| `docs/` | Phase 1 learner docs (architecture, Redis primer, experiments) |
| `files/` | Design archive and roadmap |
