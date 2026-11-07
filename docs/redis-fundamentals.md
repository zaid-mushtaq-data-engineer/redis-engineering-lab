# Redis fundamentals (this lab)

Short primer aligned with **choices in this repository**, not a full Redis tutorial.

## Why Redis here

MySQL stays authoritative. Redis reduces read latency and holds **coordination state** (rate-limit buckets). Phase 1 deliberately shows failure and stampede behavior before Phase 2 fixes.

## Data structures used

| Structure | Where | Why |
|-----------|-------|-----|
| **String** | Product, list, inventory cache values | Plain JSON text; no Spring `@class` metadata ([R-27]) |
| **Hash** | Rate-limit bucket | Two fields updated atomically in Lua |
| **Lua script** | Token bucket | Single round-trip; Redis `TIME` for consistent clocks |

We do **not** use Redis as primary storage for catalog data in Phase 1 (no persistence; restart clears Redis).

## Serialization

All cache values are written with [`StringRedisTemplate`](https://docs.spring.io/spring-data/redis/reference/redis/template.html) and [`CacheJsonCodec`](../src/main/java/com/redislab/cache/support/CacheJsonCodec.java) — human-readable JSON. Compare with the dev **JDK serialization demo** (`/api/dev/redis/serialization`) to see why binary payloads are avoided for cache.

## TTL

| Cache | TTL | Rationale |
|-------|-----|-----------|
| Product | 15 min | Rare catalog changes |
| Product list | 15 min | Invalidated on any catalog change |
| Inventory | 30 s | Stock changes more often |

Negative results (404, inactive) are **not** cached. Empty list pages with `page > 0` are not stored ([R-15]).

## SCAN vs KEYS

Evicting all list keys uses **`SCAN` with a pattern** and **`UNLINK`** in batches. `KEYS` blocks Redis and is forbidden in application code. In the CLI, prefer `SCAN` for inspection too:

```bash
docker exec redislab-redis redis-cli SCAN 0 MATCH 'redis-lab:dev:products:page:*' COUNT 100
```

## Token bucket (Hash + Lua)

Key: `redis-lab:dev:rate-limit:127.0.0.1` (colons in IPv6 addresses become `_` in the key).

```bash
docker exec redislab-redis redis-cli HGETALL redis-lab:dev:rate-limit:127.0.0.1
docker exec redislab-redis redis-cli TTL redis-lab:dev:rate-limit:127.0.0.1
```

Script: [`src/main/resources/redis/token_bucket.lua`](../src/main/resources/redis/token_bucket.lua). One bucket per client IP for all public GETs.

## `noeviction` and fail-open

Compose sets **128 MB** and **`noeviction`**. When memory is full, **writes** (cache `SET`, limiter `HSET`) fail; **deletes** (`DEL`/`UNLINK`) still work. Application code catches Redis errors on business paths and **allows the request** (fail-open), logging `CACHE_*_FAILED` or `RATE_LIMIT_REDIS_FAILURE`.

Dev sandbox is the exception: Redis errors return **503** so you can see failures explicitly.

## CLI cheat sheet (dev keys)

```bash
# Product cache
docker exec redislab-redis redis-cli GET redis-lab:dev:product:1
docker exec redislab-redis redis-cli TTL redis-lab:dev:product:1

# Inventory
docker exec redislab-redis redis-cli GET redis-lab:dev:inventory:1

# Memory
docker exec redislab-redis redis-cli INFO memory
docker exec redislab-redis redis-cli CONFIG GET maxmemory-policy
```

## When Redis is down (Phase 1)

1. Public **GET** → MySQL, no `X-RateLimit-*`.
2. Admin **writes** → MySQL commit succeeds; evict may be skipped (stale cache until TTL).
3. **`/actuator/health`** → overall `UP`, `redis` shows `DEGRADED`.
4. App **does not** require restart when Redis comes back.

See [`experiments.md`](experiments.md) for recorded drill results.

## What Phase 2 adds (preview)

Distributed lock on cache miss (stampede), idempotency keys, Pub/Sub and Streams — see open questions in [`experiments.md`](experiments.md).
