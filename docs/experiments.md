# Phase 1 experiments and failure drills

Structured lab journal for Phase 1. Automated coverage lives in `src/test/java/**/*IT.java` and maps to [IMPLEMENTATION.md §21.2](../files/IMPLEMENTATION.md#212-required-scenarios).

**Design specs:** [`files/`](../files/) · **Architecture summary:** [`architecture.md`](architecture.md)

## Redis stop / start

| | |
|---|---|
| **Setup** | App running (`dev` profile), compose up, catalog seeded or one product created |
| **Steps** | `docker compose -f ops/compose.dev.yml stop redis` → rapid public GETs + one admin POST; `docker compose -f ops/compose.dev.yml start redis` → repeat GETs without restarting the app |
| **Expected** | While Redis is down: public reads **200** from MySQL, **no** `X-RateLimit-*` headers, `RATE_LIMIT_REDIS_FAILURE` in logs; admin writes **succeed**; `/actuator/health` overall **UP** (HTTP 200), `redis` component degraded |
| **Observed** | (2026-11-07, local) With Redis stopped: `GET /api/products/1` → **200**, response headers had **no** `X-RateLimit-*`; health JSON showed `"redis":{"status":"DEGRADED"}` while `"status":"UP"` at top level (HTTP 200). After `start redis`, next GET **200** without app restart. |
| **Conclusion** | Fail-open caches and limiter; MySQL remains authoritative ([R-10], [R-12]). |

## Redis restart (data loss)

| | |
|---|---|
| **Setup** | Warm caches and a rate-limit bucket (`HGETALL redis-lab:dev:rate-limit:127.0.0.1`) |
| **Steps** | `docker restart redislab-redis` |
| **Expected** | All Redis keys gone; MySQL unchanged; next GETs repopulate cache |
| **Observed** | (2026-11-07) `DBSIZE` went from **2** (warmed keys) to **0** after restart; public GET still **200** from MySQL and repopulated cache on next request. |
| **Conclusion** | Phase 1 treats Redis as ephemeral (compose: no RDB/AOF). |

## Redis OOM (`noeviction`, 128 MB)

| | |
|---|---|
| **Setup** | Dev profile, sandbox enabled, Redis near `maxmemory` |
| **Steps** | Fill memory via sandbox or large `SET`s until `OOM command not allowed`; attempt public GET (cache write) and admin catalog change (evict/unlink) |
| **Expected** | Cache writes and rate-limit `HSET` fail → fail-open (200, no rate headers); `DEL`/`UNLINK` still work |
| **Observed** | Full 128 MB fill not repeated in this run (time-boxed). With Redis healthy, `INFO memory` showed **~1 MB** used. **Automated:** `RedisUnreachableIT` proves fail-open when Redis is unreachable; eviction paths use `UNLINK` and do not call `KEYS`. Re-run this drill manually when studying Phase 4 memory policies. |
| **Conclusion** | Under memory pressure, expect fail-open and logged write failures; plan Phase 4 eviction experiments. |

## Forwarded-header spoofing

| | |
|---|---|
| **Setup** | `app.rate-limit.trust-forwarded-headers: false` (default) |
| **Steps** | `curl -H 'X-Forwarded-For: 203.0.113.99' …` vs without header; compare `redis-cli HGETALL` keys |
| **Expected** | Bucket key uses socket address, not spoofed header |
| **Observed** | Covered by `ClientIpResolverTest` (unit). Manual curl optional in dev. |
| **Conclusion** | Enable trust only behind a known reverse proxy ([R-20]). |

## Latency baseline (dev, Redis up)

Repeatable check (product id `1` must exist):

```bash
# cold-ish (flush key first optional)
curl -s -o /dev/null -w "first:%{time_total}\n" http://localhost:8080/api/products/1
# warm — run 20× and average
for i in $(seq 1 20); do curl -s -o /dev/null -w "%{time_total}\n" http://localhost:8080/api/products/1; done | awk '{s+=$1} END {print "avg", s/NR}'
```

| Metric | Observed (2026-11-07, local) |
|--------|------------------------------|
| First GET after restart | ~**0.67 s** (miss + MySQL + cache fill) |
| Second GET | ~**0.02 s** |
| 20 warm GETs (avg / min / max) | **7.3 ms / 3.8 ms / 17.2 ms** |

With Redis stopped, expect similar order of magnitude to warm MySQL path (no cache write); use the stop/start drill above for spot checks.

## Stampede baseline (automated)

`StampedeReproductionIT`: evict product key, **50 parallel** `GET /api/products/{id}`.

| Metric | Result |
|--------|--------|
| Hibernate `entityLoadCount` after burst | **> 1** (test assertion); parallel JDBC work visible in surefire logs (many concurrent selects) |
| Phase 2.1 target | **≈ 1** load per expiry after distributed lock |

Re-run: `mvn test -Dtest=StampedeReproductionIT` (Docker required).

## §21.2 automation map

| Scenario | Automated test / manual |
|----------|-------------------------|
| Cache-aside hit/miss/TTL/corrupt | [`ProductCacheAsideIT`](../src/test/java/com/redislab/cache/ProductCacheAsideIT.java) |
| List cache / pagination | [`ProductListCacheIT`](../src/test/java/com/redislab/cache/ProductListCacheIT.java) |
| Inventory TTL / inactive 404 | [`InventoryCacheIT`](../src/test/java/com/redislab/cache/InventoryCacheIT.java) |
| Invalidation matrix | [`CacheInvalidationIT`](../src/test/java/com/redislab/cache/CacheInvalidationIT.java) |
| Inventory concurrency / stale version | [`InventoryConcurrencyIT`](../src/test/java/com/redislab/inventory/InventoryConcurrencyIT.java) |
| Optimistic locking | [`OptimisticLockingIT`](../src/test/java/com/redislab/product/OptimisticLockingIT.java) |
| Create rollback | [`CreateRollbackIT`](../src/test/java/com/redislab/product/CreateRollbackIT.java) |
| Rate limit burst/refill/shared/parallel | [`RateLimitIntegrationIT`](../src/test/java/com/redislab/ratelimit/RateLimitIntegrationIT.java), [`RateLimitLuaIT`](../src/test/java/com/redislab/ratelimit/RateLimitLuaIT.java) |
| Stampede reproduction | [`StampedeReproductionIT`](../src/test/java/com/redislab/cache/StampedeReproductionIT.java) |
| Redis unreachable fail-open | [`RedisUnreachableIT`](../src/test/java/com/redislab/support/it/RedisUnreachableIT.java) |
| Admin key / error JSON | [`AdminAndErrorFormatIT`](../src/test/java/com/redislab/common/AdminAndErrorFormatIT.java) |
| Health degraded | [`ActuatorHealthIT`](../src/test/java/com/redislab/actuator/ActuatorHealthIT.java) |
| Redis stop/OOM/restart | This document (manual drills above) |

## Phase 1 contract audit (Stage 10)

| Check | Status |
|-------|--------|
| 13 endpoints vs [`API.md`](../files/API.md) | Match controllers |
| Invalidation matrix vs services | Match `ProductServiceImpl` / `InventoryServiceImpl`; verified by `CacheInvalidationIT` |
| No `KEYS` in Java eviction | `SCAN` + `UNLINK` only |
| Dev sandbox outside `dev` | Beans `@Profile("dev")`; not registered without profile |
| Secrets | `.env` gitignored; [`.env.example`](../.env.example) placeholders only |
| Postman | Covers all **13** business endpoints + health (see collection) |

## Phase 2 open questions (from PROJECT)

| ID | Topic | Phase 1 input |
|----|-------|----------------|
| O-01 | Stream retention | Not yet applicable |
| O-02 | Idempotency TTL / durability | Redis ephemeral in Phase 1 |
| O-03 | `@Cacheable` comparison | Optional later stage |
| O-04 | k6 vs JMeter | Phase 5 |
| O-05 | Page-version cache keys | Alternative to SCAN evict |
| O-06 | Lock lease / wait / poll | **Stampede IT** motivates tuning in 2.1 |
| O-07 | Pub/Sub event timing | Phase 2 |
| O-08 | Admin-key brute-force limit | Not implemented (documented gap) |
| O-09 | `X-Cache: HIT/MISS` header | Not in contract |

See [`PROJECT.md` §17.3](../files/PROJECT.md#173-open-decisions-to-be-decided) for full descriptions.
