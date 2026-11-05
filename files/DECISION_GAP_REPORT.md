# Decision and Gap Report — Redis Engineering Lab

Companion to the five documents in `redis-engineering-lab-docs/` (`PROJECT.md`, `IMPLEMENTATION.md`, `FLOW.md`, `ROADMAP.md`, `API.md`). It lists what was contradictory, wrong, or missing in the source conversation and what the documents now say. IDs (R-xx, O-xx) match [`PROJECT.md` §17](redis-engineering-lab-docs/PROJECT.md#17-decision-register).

---

## 1. Conflicts identified and how they were resolved

| # | Conflict in the conversation | Resolution | Why |
|---|---|---|---|
| C1 | **Phase placement.** Early: Pub/Sub = Phase 2, multi-instance = Phase 3. Later "revised phases" table: rate limiting/Lua in Phase 2, Pub/Sub in Phase 3. The project brief: token bucket/Lua in Phase 1 (Stage 8), Pub/Sub in Phase 2, Streams/multi-instance in Phase 3. | Follow the brief and the confirmed "Phase 1 = token bucket + Lua" decisions. Multi-instance Pub/Sub testing in Phase 3. **R-01, R-30** | Rate limiting was explicitly designed, configured and confirmed for Phase 1 (algorithm, limits, headers, failure mode). |
| C2 | **Key versioning.** Assistant recommended versioned keys (`…:product:v1:101`), user confirmed option B (environment-aware, unversioned); a later question again offered "version or no version". | No versioning in Phase 1; versioning is a Phase 5 experiment. **R-18** | The confirmed choice was B; versioning only matters for schema migration/page invalidation experiments. |
| C3 | **Rate-limited endpoints.** Confirmed "all public read APIs" but enumerated only product-detail and inventory; the public list was added afterwards. | Public list is also rate limited. **R-02** | Intent was protecting every public read; leaving the list open would defeat the limiter. |
| C4 | **Product update body.** Early `PUT` example included `status`; later consolidated contract has `name, description, price, version` and separate activate/deactivate endpoints. | `PUT` has no `status`; unknown fields ignored. **R-04** | Avoids two ways to change status and keeps invalidation rules unambiguous. |
| C5 | **Early drafts vs final API.** `DELETE /api/products/{id}`, `PATCH /api/inventory/{id}/increment`, unversioned `/api/inventory` admin writes, Module 01–06 plan. | Final confirmed endpoints only; early drafts declared superseded. **R-19** | Later explicit decisions (soft delete, admin-prefixed `POST` increment/decrement). |
| C6 | **Inventory 404 code.** Error table has both `PRODUCT_NOT_FOUND` and `INVENTORY_NOT_FOUND`, but flows only say "404". | Product endpoints → `PRODUCT_NOT_FOUND`; inventory endpoints → `INVENTORY_NOT_FOUND`. **R-03** | Simple rule for clients; every product has an inventory row so the two cases coincide. |
| C7 | **Response shapes.** Confirmed examples for activate/deactivate (`{id,status}`) and increment/decrement (`{productId,stockQuantity}`) omit `version`, although `version` changes on those operations and later writes require it. | Add `version` to those responses. **R-05** | Otherwise admins need an extra GET to continue. Superset of the confirmed shape. |
| C8 | **Inventory version bump.** Question "should increment/decrement bump `version`?" was never answered (conversation was redirected), but the schema discussion says "we should also". | Yes, bump. **R-06** | Without it an old absolute-set request could overwrite a newer increment. |
| C9 | **Health.** Confirmed: app stays `UP` when Redis is down *and* statuses only; example shows Redis `DOWN`. Spring Boot's default aggregation makes the overall status `DOWN` if any component is `DOWN`. | Redis is non-critical: custom indicator reports `DEGRADED`, ignored by aggregation; MySQL critical; fallback group documented. **R-12** | The confirmed behavior ("UP while Redis is down") is the intent; the example was impossible with defaults. |
| C10 | **Package layout.** `config/RedisConfig` vs `cache/config`; `redis/` sandbox package vs `cache/`. | One reconciled layout. **R-21** | Single source of truth for the guide. |
| C11 | **Spring Cache annotations.** Original Module 03 included them; later plan never scheduled them. | Optional Stage 2.5. **R-22 / O-03** | Not part of confirmed Phase 1; kept so the original learning objective isn't lost. |
| C12 | **Flyway files.** Three named files in one place, one combined script in another. | V1 products, V2 inventory, V3 index. **R-23** | Matches the confirmed file list. |
| C13 | **Rate-limit key TTL.** Key inventory says "Dynamic", configuration says "60 s after full". | `EXPIRE = secondsToFull + 60`, computed in Lua. **R-11** | Both statements satisfied. |
| C14 | **Product-list invalidation mechanism.** "SCAN or a maintained set of page keys". | `SCAN` + `UNLINK`. **R-14** | Stateless; no second key to keep consistent. |
| C15 | **Order of work.** Roadmap said Basic APIs (stage 2) before Redis; later decision B: Redis first. | Brief's stage list: Stage 2 Redis, Stage 3 minimal model, Stage 4 APIs; the web infra the sandbox needs (error handler, admin key) is built in Stage 2. | Honors "Redis first" without leaving the sandbox unprotected. |

## 2. Technical mistakes corrected

| # | Mistake / risk in the discussed design | Correction | Where |
|---|---|---|---|
| T1 | "Invalidate after commit" is naturally written *inside* a `@Transactional` method, i.e. **before** the commit. | Services are non-transactional orchestrators using `TransactionTemplate`; invalidate after it returns. **R-07** | IMPL §9.2, FLOW §3 |
| T2 | Admin-key and rate-limit checks as servlet filters would bypass `@RestControllerAdvice`, so 401/429 would not use the standard JSON. | Use MVC interceptors that throw exceptions. **R-08** | IMPL §10, §20 |
| T3 | `RedisTemplate` + Jackson default typing (the usual "JSON serializer") writes `@class` into values and breaks on class renames; a JSON value serializer also corrupts Lua arguments (`"10"` with quotes). | `StringRedisTemplate` + explicit codec; Lua via `StringRedisTemplate`. **R-09** | IMPL §13 |
| T4 | Token bucket refill based on the application clock gives different answers on different instances. | Lua reads `redis.call('TIME')`. **R-11** | IMPL §20.3 |
| T5 | With Lettuce defaults, commands during an outage are queued until the 1 s timeout, adding ~1 s to every request. | `DisconnectedBehavior.REJECT_COMMANDS`. **R-10** | IMPL §12 |
| T6 | Dev sandbox accepted arbitrary keys, unauthenticated: could delete real cache keys or fill Redis. | Dev profile only, admin key, sandbox prefix, key/value limits, `503` not fail-open. **R-13** | API §6 |
| T7 | Unlimited page numbers/sizes create unlimited cache keys; with `noeviction` and 128 MB this can fill Redis and disable cache writes and the limiter. | Cache only non-empty pages (and page 0); size ≤ 50. **R-15** | FLOW §8 |
| T8 | `stock_quantity INT` can overflow on increment (MySQL error → 500); price `DECIMAL(10,2)` overflow; unbounded inputs. | Bounds + `STOCK_LIMIT_EXCEEDED`; price max. **R-16** | API §3–4 |
| T9 | Price "unchanged" check with `equals` fails on scale (`799.0` vs `799.00`); version vs no-op ordering undefined. | `compareTo`; version check first; no-op returns without version bump. **R-17** | API §3.2 |
| T10 | Trusting `X-Forwarded-For` lets clients spoof IPs and evade limits. | Remote address by default; forwarded header only if explicitly trusted. IPv6 colons sanitized in keys. **R-20** | IMPL §20.5 |
| T11 | Compose exposes Redis (no password) and MySQL on all interfaces. | Bind to `127.0.0.1`. **R-32** | IMPL §3 |
| T12 | Flyway 10+ needs `flyway-mysql`; native `CURRENT_TIMESTAMP` depends on server timezone; `open-in-view` default `true`. | Added module, UTC everywhere, `open-in-view=false`. **R-28** | IMPL §1, §3, §4 |
| T13 | Static admin key could be empty and compared with `equals`. | Fail-fast on empty; constant-time comparison. **R-26** | IMPL §10 |
| T14 | A corrupt/outdated cached value would throw on every read. | Treat as miss, delete, log. **R-27** | IMPL §15 |
| T15 | Cache-aside stale-write race and failed-invalidation staleness not acknowledged. | Documented as accepted Phase 1 limitation; reproduced and mitigated in Phase 5. **R-24** | PROJECT §11 |
| T16 | Prior statement that headers carry "reset time"/"retry" without units. | `Retry-After` integer seconds; `X-RateLimit-Reset` Unix epoch seconds; headers omitted if Redis failed. **R-29** | API §1.3 |
| T17 | Stampede "reproduce the problem" had no Phase 1 step. | Concurrency test in Stage 9. **R-31** | ROADMAP Stage 9 |

## 3. Missing requirements that were filled

| Area | Added |
|---|---|
| Error model | `MALFORMED_REQUEST`, `INVALID_PARAMETER`, `ENDPOINT_NOT_FOUND`, `METHOD_NOT_ALLOWED`, `UNSUPPORTED_MEDIA_TYPE`, `STOCK_LIMIT_EXCEEDED`; dev-only `REDIS_KEY_NOT_FOUND`, `REDIS_UNAVAILABLE` (**R-25**). |
| Validation | Price/stock/quantity bounds; trimming; pagination edge cases (page past end, non-numeric); `version` required messages. |
| Business semantics | Inactive products can be updated by admin; no-op rules for inventory set; 404 vs 409 disambiguation in decrement; `Location` header on create. |
| Redis failure handling | Corrupt cache entries; OOM behavior (writes fail, deletes work); limiter headers when Redis is down; dev endpoints behavior; health semantics. |
| Concurrency | Row-lock/read-after-write inside one transaction for atomic updates; Hibernate `clearAutomatically`; race windows listed. |
| Security | Fail-fast key, constant-time compare, loopback binding, sandbox hardening, forwarded-header policy, brute-force gap noted. |
| Configuration | Typed `app.*` properties; Redis/MySQL compose; `.env.example`; time zone; Lettuce options. |
| Testing | Full scenario matrix, Testcontainers wiring, failure injection, concurrency and time-based test guidance. |
| Roadmap | Per-stage dependencies, completion criteria, exit checklist, Phase 2–5 stage breakdown; admin-key/error infra moved to Stage 2. |
| API | Added `GET /api/admin/inventory/{productId}` (already agreed), `GET …/exists` dev endpoint (R-13), `Location` header. |

## 4. Open decisions (To Be Decided)

| ID | Decision | Why it can't be settled now |
|---|---|---|
| O-01 | Stream retention / trimming | Depends on event volume and Redis memory behavior measured in Phase 3/4. |
| O-02 | Idempotency key TTL and durability | Depends on Redis persistence choices and retry windows. |
| O-03 | Spring Cache annotation comparison timing | Original learning goal, never scheduled by the user. |
| O-04 | k6 vs JMeter | User said "k6 or JMeter". |
| O-05 | Page-key versioning vs `SCAN` | Planned experiment. |
| O-06 | Lock lease, wait, poll values | Need measurements of MySQL load time. |
| O-07 | Pub/Sub event timing relative to invalidation; schema evolution | Phase 2 design choice. |
| O-08 | Throttling failed admin-key attempts | Not discussed; may be out of lab scope. |
| O-09 | `X-Cache: HIT/MISS` debug header | Would change the API contract; user didn't ask. |
| O-10 | Exact Spring Boot / image patch versions | Pin when the project is created. |
| O-11 | Redis retry parameters; fail-closed limiter variant | Explicitly deferred. |
| O-12 | HA/failover scope (Sentinel/Cluster) | Mentioned ("high availability", "failover") without detail. |
| O-13 | Monitoring stack scope | Phase 4. |
| O-14 | API-key-based rate limiting | "Later" experiment. |

## 5. Significant changes to the original design

1. **Redis-down health stays `UP`** (custom non-critical indicator) instead of default aggregation.
2. **`TransactionTemplate`-based services** instead of `@Transactional` services, to get a real "after commit" hook.
3. **`StringRedisTemplate` + explicit JSON codec** instead of a JSON-serializer `RedisTemplate`; Lua uses plain strings.
4. **Interceptors, not filters,** for admin key and rate limit.
5. **Redis `TIME` in Lua** and `EXPIRE = secondsToFull + 60`.
6. **Lettuce rejects commands while disconnected.**
7. **Public list is rate limited.**
8. **Extra response fields** (`version`) and **extra error codes**; the `version` field on inventory write responses and status responses is the only visible contract change to confirmed examples.
9. **Dev sandbox hardened** and gains `EXISTS`.
10. **Input limits** (stock/price) and `STOCK_LIMIT_EXCEEDED` added.
11. **Loopback-only** container ports.

## 6. Final consistency review (performed on the five documents)

| Check | Result |
|---|---|
| Same architecture in all files (modular monolith, MySQL source of truth, Redis optional) | ✔ |
| Endpoints: 13 business + health + dev, same paths/methods/bodies in API, FLOW, IMPLEMENTATION, ROADMAP | ✔ (13 counted from API §1.1) |
| Redis keys/TTLs: 15 min / 30 s / 15 min / bucket seconds-to-full + 60 s | ✔ (PROJECT §10, IMPL §17, FLOW §12–13) |
| Invalidation matrix identical in PROJECT §11, FLOW §12, API per-endpoint notes, ROADMAP stages 5–7 | ✔ |
| Failure behavior: reads → MySQL; writes succeed; limiter fails open; dev sandbox 503 | ✔ |
| Phase separation: locks/idempotency/Pub/Sub only Phase 2; Streams/multi-instance Phase 3; persistence/eviction Phase 4 | ✔ |
| Transaction boundaries: invalidation only after commit and only on real change | ✔ |
| Mermaid diagrams: 33 blocks; checked for balanced `alt/else/end`, no stray `;`/`#`. **Not rendered by a Mermaid engine in this environment** — paste into the Mermaid Live Editor if a renderer complains. | ◐ |
| Spring Boot behaviors flagged for verification during implementation: Lettuce `REJECT_COMMANDS`, ignoring a custom health status in aggregation (fallback documented), `TimeoutOptions` API names | ◐ verify in Stages 2 and 9 |
