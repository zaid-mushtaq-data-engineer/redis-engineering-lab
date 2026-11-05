# FLOW.md — API and System Design Flows

> Part of the documentation set: [`PROJECT.md`](PROJECT.md) · [`IMPLEMENTATION.md`](IMPLEMENTATION.md) · **`FLOW.md`** · [`ROADMAP.md`](ROADMAP.md) · [`API.md`](API.md)
> This file explains **how requests travel through the system**. Field-level contracts live in [API.md](API.md); class-level detail in [IMPLEMENTATION.md](IMPLEMENTATION.md). Decision tags: **[Confirmed]**, **[Resolved R-xx]**, **[Open O-xx]** ([register](PROJECT.md#17-decision-register)).

## Contents

1. Overall architecture · 2. Public vs admin pipeline · 3. Product creation · 4. Product update · 4b. Activate / deactivate · 5–7. Product detail (hit, miss, Redis down) · 8. Product listing · 9. Inventory read · 10. Inventory writes · 11. Admin API-key validation · 12. Cache invalidation · 13–16. Token bucket, Lua, rejection, Redis failure · 17. Global exception handling · 18. Redis and MySQL interaction overview · 19. Phase 2 flows · 20. Phase 3 flows

---

## 1. Overall application architecture

```mermaid
flowchart TB
    C["Client: Postman / Swagger UI"] --> SRV["Embedded Tomcat"]
    SRV --> INT{"MVC interceptors"}
    INT -->|"/api/admin/** and /api/dev/**"| AK["AdminKeyInterceptor"]
    INT -->|"public GET endpoints"| RLI["RateLimitInterceptor"]
    AK --> CTRL
    RLI --> CTRL
    CTRL["Controllers: validate input, map DTOs"] --> SVC["Services: business rules, transactions"]
    SVC --> CS["Cache services: ProductCache, InventoryCache, ProductListCache"]
    SVC --> REPO["JPA repositories"]
    RLI --> TB["TokenBucketRateLimiter"]
    CS --> RT["StringRedisTemplate"]
    TB --> RT
    RT --> RDS[("Redis")]
    REPO --> DB[("MySQL")]
    AK -. "401" .-> GEH["GlobalExceptionHandler"]
    RLI -. "429" .-> GEH
    CTRL -. "exceptions" .-> GEH
    SVC -. "exceptions" .-> GEH
```

Fixed rules shown here: business services never call Redis commands directly; the dotted lines are exception paths ending in one JSON error format ([§17](#17-global-exception-handling)).

## 2. Public vs admin request flow comparison

| Aspect | Public read (`GET /api/products/**`, `/api/inventory/**`) | Admin (`/api/admin/**`) |
|---|---|---|
| Auth | none | `X-Admin-Key` → `401` |
| Rate limit | yes, token bucket per IP | no |
| First check | rate limit | admin key |
| Redis role | cache-aside read | **only invalidation** (admin reads bypass Redis) |
| MySQL | on miss / fallback | always |
| Inactive products | `404` | visible and manageable |
| Redis down | fall back to MySQL; limiter fails open | writes succeed; invalidation skipped and logged |

```mermaid
flowchart LR
    subgraph Public["Public read"]
        P1["Request"] --> P2["RateLimit interceptor"] --> P3["Controller"] --> P4["Service"] --> P5{"Redis hit?"}
        P5 -->|yes| P6["Return cached DTO"]
        P5 -->|no| P7["MySQL, then cache fill"] --> P6
    end
    subgraph Admin["Admin request"]
        A1["Request"] --> A2["AdminKey interceptor"] --> A3["Controller: validate body"] --> A4["Service: MySQL transaction"] --> A5["After commit: Redis invalidation"] --> A6["Return DTO"]
    end
```

---

## 3. Product creation

`POST /api/admin/products` — **[Confirmed]** flow.

| Item | Detail |
|---|---|
| Starting request | Admin sends name, description, price with `X-Admin-Key` |
| Components | AdminKeyInterceptor → AdminProductController → ProductService (`TransactionTemplate`) → ProductRepository / InventoryRepository → ProductListCacheService |
| Order | key check → bean validation → **begin tx** → insert product → insert inventory(0) → **commit** → invalidate list pages → `201` |
| MySQL | 2 inserts in one transaction |
| Redis | `SCAN` + `UNLINK` of `products:page:*` **after** commit; nothing for product/inventory (not cached yet) |
| Success | `201 Created` + product DTO (`version` 0) |
| Failures | `401` bad key · `400` validation · MySQL failure → rollback, **no** Redis action, `500` · Redis failure after commit → logged, still `201` |

```mermaid
sequenceDiagram
    actor Admin
    participant AK as AdminKeyInterceptor
    participant C as AdminProductController
    participant S as ProductService
    participant DB as MySQL
    participant LC as ProductListCacheService
    participant R as Redis

    Admin->>AK: POST /api/admin/products
    AK->>AK: compare X-Admin-Key
    AK->>C: allowed
    C->>C: validate body
    C->>S: createProduct(request)
    S->>DB: BEGIN
    S->>DB: INSERT product (ACTIVE, version 0)
    S->>DB: INSERT inventory (stock 0)
    S->>DB: COMMIT
    S->>LC: evictAll()
    LC->>R: SCAN MATCH products:page:* then UNLINK
    alt Redis error
        LC->>LC: log warning and continue
    end
    S-->>C: ProductAdminResponse
    C-->>Admin: 201 Created
```

Why invalidate after commit: if the transaction rolled back after we had deleted cache entries, we would have thrown away valid cache data for nothing; worse, a concurrent reader could re-cache the pre-commit state. Implementation note: [IMPLEMENTATION.md §9.2](IMPLEMENTATION.md#92-transaction-and-invalidation-pattern).

## 4. Product update

`PUT /api/admin/products/{id}` — **[Confirmed]** + checks ordered by [R-17].

| Item | Detail |
|---|---|
| Components | AdminKeyInterceptor → controller → ProductService → ProductRepository → ProductCacheService + ProductListCacheService |
| MySQL | read product (and compare), then `UPDATE … WHERE id=? AND version=?` |
| Redis | delete `product:{id}` + all list pages, after commit, only if changed |
| Success | `200` + updated DTO, `version` +1; or `200` unchanged (no-op) |
| Failures | `404` missing · `409 CONCURRENT_MODIFICATION` stale version (either from the explicit compare or Hibernate's optimistic-lock exception) · `400` validation · Redis failure → logged |

```mermaid
sequenceDiagram
    actor Admin
    participant C as AdminProductController
    participant S as ProductService
    participant DB as MySQL
    participant PC as ProductCacheService
    participant LC as ProductListCacheService

    Admin->>C: PUT /api/admin/products/101 with version 0
    C->>S: updateProduct(101, request)
    S->>DB: BEGIN, SELECT product 101
    alt not found
        S-->>Admin: 404 PRODUCT_NOT_FOUND
    else request.version differs from stored version
        S-->>Admin: 409 CONCURRENT_MODIFICATION
    else values unchanged
        S->>DB: ROLLBACK (nothing to write)
        S-->>Admin: 200 current product, no invalidation
    else changed
        S->>DB: UPDATE products SET ... version = version + 1 WHERE id = 101 AND version = 0
        S->>DB: COMMIT
        S->>PC: evict(101)
        S->>LC: evictAll()
        S-->>Admin: 200 updated product
    end
```

The classic lost-update scenario: two admins both read version 1; the first `PUT` succeeds (→ 2); the second still sends 1 → `409`, forcing a re-read.

## 4b. Product activation and deactivation

`PATCH /api/admin/products/{id}/activate|deactivate`

| Item | Detail |
|---|---|
| Redis | On a **real** status change: delete `product:{id}`, `inventory:{id}`, all list pages (after commit). Repeats do nothing. |
| MySQL | `UPDATE products SET status=?, version=version+1` only when status differs |
| Success | `200` `{id, status, version}` |
| Failures | `404`; Redis failure logged |

```mermaid
flowchart TD
    A["PATCH /api/admin/products/101/deactivate"] --> B["Admin key valid?"]
    B -->|no| X["401 INVALID_ADMIN_KEY"]
    B -->|yes| C["Load product in MySQL"]
    C --> D{"Exists?"}
    D -->|no| Y["404 PRODUCT_NOT_FOUND"]
    D -->|yes| E{"Already INACTIVE?"}
    E -->|yes| Z["200 OK, no write, no invalidation"]
    E -->|no| F["Update status in MySQL transaction, version + 1"]
    F --> G["Commit"]
    G --> H["Delete product:101"]
    H --> I["Delete inventory:101"]
    I --> J["SCAN and delete products:page:*"]
    J --> K["200 OK"]
```

---

## 5. Product detail read — cache hit

`GET /api/products/101`

| Item | Detail |
|---|---|
| Order | rate limit → controller → ProductService → `GET redis-lab:dev:product:101` |
| Redis | 1 × `GET` |
| MySQL | **none** |
| Success | `200` cached DTO (+ rate-limit headers) |

```mermaid
sequenceDiagram
    actor U as Client
    participant RL as RateLimitInterceptor
    participant S as ProductService
    participant PC as ProductCacheService
    participant R as Redis

    U->>RL: GET /api/products/101
    RL->>R: EVALSHA token bucket script
    R-->>RL: allowed, remaining 9
    RL->>S: continue with rate-limit headers set
    S->>PC: get(101)
    PC->>R: GET redis-lab:dev:product:101
    R-->>PC: JSON string
    PC-->>S: ProductResponse
    S-->>U: 200 OK (no MySQL call)
```

## 6. Product detail read — cache miss

| Item | Detail |
|---|---|
| Redis | `GET` (nil) → `SET … EX 900` |
| MySQL | `SELECT … WHERE id=101 AND status='ACTIVE'` |
| Success | `200` DTO |
| Failures | missing/inactive → `404` **without caching** · cache write fails → log, still `200` |

```mermaid
sequenceDiagram
    actor U as Client
    participant S as ProductService
    participant PC as ProductCacheService
    participant R as Redis
    participant DB as MySQL

    U->>S: GET /api/products/101 (after rate limit)
    S->>PC: get(101)
    PC->>R: GET redis-lab:dev:product:101
    R-->>PC: nil
    PC-->>S: empty
    S->>DB: SELECT product 101 where ACTIVE
    alt found
        DB-->>S: product row
        S->>PC: put(product)
        PC->>R: SET key json EX 900
        S-->>U: 200 OK
    else missing or inactive
        DB-->>S: no row
        S-->>U: 404 PRODUCT_NOT_FOUND (nothing cached)
    end
```

Phase 1 intentionally has **no stampede protection**: if 100 requests miss together, 100 MySQL queries run ([§19.1](#191-stampede-protection-with-a-redis-lock-planned)).

## 7. Product read when Redis is unavailable

| Item | Detail |
|---|---|
| Trigger | Redis stopped, network failure, command timeout (1 s), or OOM on write |
| Behavior | Cache read error = miss → MySQL; cache write skipped; `200` returned |
| Logs | `CACHE_READ_FAILED`, `CACHE_WRITE_SKIPPED` with the exception type |
| Speed | Lettuce rejects commands immediately while disconnected ([R-10]), so no 1 s delay per request during a full outage; a "black-holed" network can still cost up to 1 s per request |
| Recovery | Lettuce reconnects automatically; next request that misses refills the key (lazy rebuild) |

```mermaid
sequenceDiagram
    actor U as Client
    participant S as ProductService
    participant PC as ProductCacheService
    participant R as Redis
    participant DB as MySQL

    U->>S: GET /api/products/101
    S->>PC: get(101)
    PC->>R: GET key
    R--xPC: connection refused or timeout
    PC->>PC: log CACHE_READ_FAILED, return empty
    S->>DB: SELECT product 101
    DB-->>S: row
    S->>PC: put(product)
    PC->>R: SET key json EX 900
    R--xPC: error
    PC->>PC: log CACHE_WRITE_FAILED, ignore
    S-->>U: 200 OK from MySQL
```

---

## 8. Paginated product listing

`GET /api/products?page=0&size=20` (public) and `GET /api/admin/products` (admin).

| Item | Public | Admin |
|---|---|---|
| Validation | page ≥ 0, 1 ≤ size ≤ 50 → else `400 INVALID_PAGINATION` | same |
| Redis | key `products:page:{p}:size:{s}`, TTL 15 min | none |
| MySQL | `status='ACTIVE' ORDER BY created_at DESC, id DESC` + count | no status filter |
| Cache fill rule | not for empty pages with page > 0 ([R-15]) | – |

```mermaid
flowchart TD
    A["GET /api/products?page=0 and size=20"] --> RL["Rate limit check"]
    RL --> V{"page and size valid?"}
    V -->|no| E["400 INVALID_PAGINATION"]
    V -->|yes| K{"Redis GET products:page:0:size:20"}
    K -->|hit| H["Return cached PageResponse"]
    K -->|"miss or Redis error"| DB["MySQL: page query and count"]
    DB --> W{"Empty page beyond page 0?"}
    W -->|yes| R1["Return without caching"]
    W -->|no| S["SET key json EX 900 (errors ignored)"]
    S --> R2["Return PageResponse"]
```

**Why pages are invalidated together:** creating one product shifts every later page ([Confirmed] example: page 1 held products 80–61, after one insert it holds 81–62), so all cached pages become wrong.

## 9. Inventory read

`GET /api/inventory/101` — same pattern as product detail with TTL **30 s** and a status check.

```mermaid
sequenceDiagram
    actor U as Client
    participant S as InventoryService
    participant IC as InventoryCacheService
    participant R as Redis
    participant DB as MySQL

    U->>S: GET /api/inventory/101 (after rate limit)
    S->>IC: get(101)
    IC->>R: GET redis-lab:dev:inventory:101
    alt cache hit
        R-->>IC: JSON
        S-->>U: 200 stockQuantity (no MySQL)
    else miss or Redis error
        R-->>IC: nil / error
        S->>DB: SELECT inventory JOIN product WHERE product.status = ACTIVE
        alt row found
            DB-->>S: productId, stockQuantity
            S->>IC: put (SET EX 30)
            S-->>U: 200 OK
        else no row (missing or inactive)
            S-->>U: 404 INVENTORY_NOT_FOUND (not cached)
        end
    end
```

Because only **active** products are ever written to this cache and deactivation deletes the key, a hit is always safe to return without re-checking the product status (subject to the race in [PROJECT.md §11](PROJECT.md#known-cache-consistency-limits-accepted-in-phase-1-resolved-r-24)).

## 10. Inventory writes

Common steps: admin key → validation → MySQL transaction → **commit** → delete `redis-lab:dev:inventory:{id}` → return admin inventory DTO `{productId, stockQuantity, version}`. Redis failure on delete: log, still `200`; stale value lives ≤ 30 s.

### 10.1 Set (`PUT /api/admin/inventory/101`) — optimistic locking

```mermaid
sequenceDiagram
    actor Admin
    participant S as InventoryService
    participant DB as MySQL
    participant IC as InventoryCacheService

    Admin->>S: PUT stockQuantity 70, version 3
    S->>DB: BEGIN, SELECT inventory WHERE product_id = 101
    alt no row
        S-->>Admin: 404 INVENTORY_NOT_FOUND
    else version differs
        S-->>Admin: 409 CONCURRENT_MODIFICATION
    else quantity unchanged
        S-->>Admin: 200 current state (no write, no invalidation)
    else change
        S->>DB: UPDATE ... SET stock_quantity=70, version=4 WHERE id=? AND version=3
        S->>DB: COMMIT
        S->>IC: evict(101)
        S-->>Admin: 200 version 4
    end
```

### 10.2 Increment — atomic SQL

```mermaid
sequenceDiagram
    actor Admin
    participant S as InventoryService
    participant DB as MySQL
    participant IC as InventoryCacheService

    Admin->>S: POST increment quantity 20
    S->>DB: BEGIN
    S->>DB: UPDATE inventory SET stock = stock + 20, version = version + 1 WHERE product_id = 101 AND stock <= max - 20
    alt 1 row updated
        S->>DB: SELECT inventory (same transaction)
        S->>DB: COMMIT
        S->>IC: evict(101)
        S-->>Admin: 200 new quantity and version
    else 0 rows
        S->>DB: existsByProductId(101)?
        S-->>Admin: 404 INVENTORY_NOT_FOUND or 409 STOCK_LIMIT_EXCEEDED
    end
```

### 10.3 Decrement — conditional atomic SQL

Identical to 10.2 but the SQL is `stock = stock - :q … AND stock >= :q`; zero rows updated + product exists → `409 INSUFFICIENT_STOCK`.

```mermaid
flowchart TD
    A["POST decrement quantity 10"] --> B["UPDATE ... SET stock = stock - 10, version = version + 1 WHERE product_id = 101 AND stock >= 10"]
    B --> C{"Rows updated?"}
    C -->|1| D["Read new row, commit, delete inventory:101 in Redis"]
    D --> E["200 OK"]
    C -->|0| F{"Inventory row exists?"}
    F -->|no| G["404 INVENTORY_NOT_FOUND"]
    F -->|yes| H["409 INSUFFICIENT_STOCK"]
```

Why this is safe under concurrency: MySQL serializes updates to one row; the `WHERE stock >= :q` test and the subtraction happen in the same statement, so 100 customers racing for the last 5 units get exactly 5 successes (verified by a test in Stage 9).

---

## 11. Admin API-key validation

```mermaid
flowchart TD
    A["Request to /api/admin/** or /api/dev/**"] --> B{"X-Admin-Key header present?"}
    B -->|no| F["throw InvalidAdminKeyException"]
    B -->|yes| C{"Constant-time equals configured key?"}
    C -->|no| F
    C -->|yes| D["Continue to controller"]
    F --> G["GlobalExceptionHandler"]
    G --> H["401 JSON: code INVALID_ADMIN_KEY"]
```

The key is never logged. The check happens before body parsing/validation so unauthenticated callers learn nothing about the API's validation rules.

## 12. Cache invalidation

### 12.1 Invalidation matrix (authoritative)

| Trigger (after MySQL commit) | `product:{id}` | `inventory:{id}` | `products:page:*` | Notes |
|---|---|---|---|---|
| Create product | – | – | ✅ | New product shifts pagination |
| Update product (changed) | ✅ | – | ✅ | Price/name appear in lists |
| Update product (unchanged) | – | – | – | No write happened |
| Activate (INACTIVE→ACTIVE) | ✅ | ✅ | ✅ | |
| Deactivate (ACTIVE→INACTIVE) | ✅ | ✅ | ✅ | |
| Activate/deactivate repeat | – | – | – | Idempotent |
| Set inventory (changed) | – | ✅ | – | |
| Increment inventory | – | ✅ | – | |
| Decrement inventory | – | ✅ | – | |
| Admin reads | – | – | – | Never cached |
| TTL expiry | automatic | automatic | automatic | Safety net |

### 12.2 List-page deletion with SCAN **[Resolved R-14]**

```mermaid
flowchart TD
    S["evictAll()"] --> P["cursor = 0"]
    P --> Q["SCAN cursor MATCH redis-lab:dev:products:page:* COUNT 100"]
    Q --> R["UNLINK matched keys"]
    R --> T{"cursor == 0?"}
    T -->|no| Q
    T -->|yes| Z["done"]
    Q -. "Redis error" .-> L["log CACHE_INVALIDATE_FAILED and stop"]
```

`KEYS` is never used (it blocks Redis while scanning the whole keyspace). `SCAN` may miss keys created while scanning; those expire by TTL — acceptable for the lab and revisited in Phase 5 (page-version experiment, [O-05]).

### 12.3 Ordering and failure behavior

```mermaid
flowchart LR
    A["MySQL transaction"] -->|"rollback"| X["No Redis action"]
    A -->|"commit"| B["Invalidate Redis keys"]
    B -->|"ok"| C["Return success"]
    B -->|"Redis error"| D["Log, stale data until TTL"] --> C
```

---

## 13. Redis token bucket

Every rate-limited request consumes **1 token** from the bucket of its client IP. Bucket: capacity 10, refill 1/s, one shared bucket for all public reads, Redis Hash `redis-lab:dev:rate-limit:{ip}` with fields `tokens` and `last_refill_time`.

```mermaid
flowchart TD
    A["Public GET request"] --> B["Resolve client IP"]
    B --> C["Run token bucket Lua script"]
    C --> D{"Bucket exists?"}
    D -->|no| E["tokens = 10, last = now"]
    D -->|yes| F["tokens = min(10, tokens + elapsed seconds x 1)"]
    E --> G{"tokens >= 1?"}
    F --> G
    G -->|yes| H["tokens = tokens - 1, allowed"]
    G -->|no| I["rejected, retryAfter = ceil(1 - tokens)"]
    H --> J["HSET, EXPIRE = secondsToFull + 60"]
    I --> J
    J --> K["Return allowed, remaining, retryAfter, resetAt"]
    K --> L{"allowed?"}
    L -->|yes| M["Add X-RateLimit headers, continue to controller"]
    L -->|no| N["429 RATE_LIMIT_EXCEEDED + Retry-After"]
```

Example timeline (capacity 10, 1/s): t=0 → 10 rapid requests succeed (remaining 9…0); request 11 at t=0.2 s → rejected, `Retry-After: 1`; at t=1.2 s one token has refilled → one more request passes.

## 14. Lua script execution

Why Lua: "read tokens → compute refill → decrement" is several steps. Two app instances doing them separately could both spend the same token. A Lua script runs **atomically** inside Redis (no other command interleaves), and also reads the clock from Redis (`TIME`) so all instances share one clock **[R-11]**.

```mermaid
sequenceDiagram
    participant I as RateLimitInterceptor
    participant L as TokenBucketRateLimiter
    participant R as Redis
    participant Lua as token_bucket.lua (inside Redis)

    I->>L: tryConsume(clientIp)
    L->>R: EVALSHA sha KEYS bucketKey ARGV capacity refill requested ttlAfterFull
    R->>Lua: execute atomically
    Lua->>R: TIME
    Lua->>R: HMGET key tokens last_refill_time
    Lua->>Lua: refill, compare, consume
    Lua->>R: HSET key tokens last_refill_time
    Lua->>R: EXPIRE key secondsToFull+60
    Lua-->>R: allowed, remaining, retryAfter, resetAt
    R-->>L: array of 4 integers
    L-->>I: RateLimitResult
```

Spring sends the script's SHA via `EVALSHA` and falls back to `EVAL` automatically if the script is not cached yet. Script text: [IMPLEMENTATION.md §20.3](IMPLEMENTATION.md#203-lua-script).

## 15. Rate-limit rejection

```mermaid
sequenceDiagram
    actor U as Client (192.168.1.10)
    participant I as RateLimitInterceptor
    participant R as Redis
    participant G as GlobalExceptionHandler

    U->>I: GET /api/products/101 (11th request in a burst)
    I->>R: EVALSHA token bucket
    R-->>I: allowed 0, remaining 0, retryAfter 1, resetAt 1793880010
    I->>I: throw RateLimitExceededException(result)
    I->>G: exception
    G-->>U: 429, Retry-After 1, X-RateLimit-* headers, JSON RATE_LIMIT_EXCEEDED
    Note over I,G: Controller, service, cache and MySQL are never reached
```

The rejection is logged as `RATE_LIMIT_REJECTED ip=… ` (rate-limit logs are part of Phase 1 observability).

## 16. Redis failure during rate limiting

```mermaid
sequenceDiagram
    actor U as Client
    participant I as RateLimitInterceptor
    participant R as Redis
    participant C as Controller and Service

    U->>I: GET /api/products/101
    I->>R: EVALSHA token bucket
    R--xI: connection error, timeout, or OOM
    I->>I: log RATE_LIMIT_REDIS_FAILURE, fail open, no headers
    I->>C: continue request
    C-->>U: normal response (cache then MySQL fallback)
```

Trade-off **[Confirmed]**: limits can be bypassed while Redis is down or full. With `noeviction` and a full Redis, the script's `HSET` is refused (OOM), so the limiter fails open as well; this is a deliberate Phase 4 experiment. Fail-closed (`503`) is a later experiment ([O-11]).

---

## 17. Global exception handling

All failures converge on one JSON structure `{timestamp, status, code, message, path[, fieldErrors]}`.

```mermaid
flowchart TD
    E["Exception thrown in interceptor, controller or service"] --> G["GlobalExceptionHandler (@RestControllerAdvice)"]
    G --> T{"Exception type"}
    T -->|"MethodArgumentNotValidException"| V["400 VALIDATION_FAILED + fieldErrors"]
    T -->|"HttpMessageNotReadableException"| M["400 MALFORMED_REQUEST"]
    T -->|"type mismatch or bad pagination"| P["400 INVALID_PARAMETER / INVALID_PAGINATION"]
    T -->|"InvalidAdminKeyException"| A["401 INVALID_ADMIN_KEY"]
    T -->|"ProductNotFoundException"| N1["404 PRODUCT_NOT_FOUND"]
    T -->|"InventoryNotFoundException"| N2["404 INVENTORY_NOT_FOUND"]
    T -->|"ConcurrentModification / OptimisticLocking"| K1["409 CONCURRENT_MODIFICATION"]
    T -->|"InsufficientStockException"| K2["409 INSUFFICIENT_STOCK"]
    T -->|"StockLimitExceededException"| K3["409 STOCK_LIMIT_EXCEEDED"]
    T -->|"RateLimitExceededException"| R["429 RATE_LIMIT_EXCEEDED + headers"]
    T -->|"anything else"| I["Log full stack trace, return generic 500 INTERNAL_SERVER_ERROR"]
```

Redis exceptions are **not** in this list for business endpoints — they are caught in the cache layer ([IMPLEMENTATION.md §19](IMPLEMENTATION.md#19-redis-failure-fallback)). Only the dev sandbox maps them to `503 REDIS_UNAVAILABLE`. Interceptors (not servlet filters) are used so these exceptions reach this handler **[R-08]**.

## 18. Redis and MySQL interaction overview

```mermaid
flowchart LR
    subgraph Reads["Public reads"]
        RR["GET product / inventory / list"] -->|"1 lookup"| RD[("Redis")]
        RD -->|"miss"| MY[("MySQL")]
        MY -->|"2 populate with TTL"| RD
    end
    subgraph Writes["Admin writes"]
        WW["POST / PUT / PATCH"] -->|"1 transaction + commit"| MY2[("MySQL")]
        MY2 -->|"2 delete keys"| RD2[("Redis")]
    end
    subgraph Protect["Protection"]
        TB["Token bucket Lua"] --> RD3[("Redis")]
    end
```

| Operation | Redis | MySQL | Source of truth |
|---|---|---|---|
| Public read hit | GET | – | MySQL (cached copy served) |
| Public read miss | GET, SET EX | SELECT | MySQL |
| Admin read | – | SELECT | MySQL |
| Admin write | UNLINK/DEL (after) | tx | MySQL |
| Rate limit | EVALSHA | – | n/a (Redis owns it) |

---

## 19. Phase 2 flows (planned — details marked To Be Decided)

### 19.1 Stampede protection with a Redis lock (planned)

Problem: a hot key expires and many requests hit MySQL at once (reproduced in Stage 9, fixed in Stage 2.1). Parameters (lease, wait, poll interval) are **[Open O-06]**.

```mermaid
sequenceDiagram
    actor U1 as Request A
    actor U2 as Request B
    participant S as ProductService
    participant R as Redis
    participant DB as MySQL

    U1->>S: GET product 101
    S->>R: GET product:101
    R-->>S: nil (expired)
    U2->>S: GET product 101
    S->>R: GET product:101
    R-->>S: nil
    S->>R: SET lock:product:101 randomToken NX PX lease  (A wins)
    S->>R: SET lock:product:101 other NX PX lease  (B loses)
    S->>R: A re-checks GET product:101
    S->>DB: A loads product
    S->>R: A SET product:101 EX 900
    S->>R: A releases lock with compare-and-delete Lua (only if token matches)
    S->>R: B polls GET product:101 briefly
    R-->>S: B finds value, returns it
    Note over S,DB: If B times out waiting, it falls back to MySQL directly
```

### 19.2 Idempotency key on inventory writes (planned)

```mermaid
flowchart TD
    A["POST increment with Idempotency-Key header"] --> B{"Key present?"}
    B -->|no| N["Normal Phase 1 behavior"]
    B -->|yes| C["SET idempotency:key IN_PROGRESS NX EX ttl"]
    C --> D{"Set succeeded?"}
    D -->|no| E{"Existing state"}
    E -->|"IN_PROGRESS"| F["409 request in progress"]
    E -->|"COMPLETED"| G["Replay stored response"]
    D -->|yes| H["Run MySQL atomic update"]
    H --> I["Store COMPLETED + response with TTL"]
    I --> J["Return response"]
```

Limitation to demonstrate: Redis has no persistence in Phase 1, so a Redis restart forgets keys and a retry would apply twice; production idempotency needs durable storage ([O-02]).

### 19.3 Pub/Sub inventory events (planned)

```mermaid
sequenceDiagram
    participant S as InventoryService
    participant R as Redis
    participant A as Subscriber instance A
    participant B as Subscriber instance B

    S->>S: MySQL commit and cache invalidation
    S->>R: PUBLISH redis-lab:dev:events:inventory  {event INVENTORY_UPDATED, productId 101, newQuantity 70}
    R-->>A: message
    R-->>B: message
    Note over R,B: Fire-and-forget. A subscriber that is offline never receives it.
```

Event shape **[Confirmed]**: `{"event":"INVENTORY_UPDATED","productId":101,"newQuantity":70}`. Whether to publish before or after invalidation, and schema versioning, are **[Open O-07]**.

## 20. Phase 3 flows (planned)

### 20.1 Redis Streams with consumer groups

```mermaid
sequenceDiagram
    participant P as Producer (InventoryService)
    participant S as Stream redis-lab:dev:stream:inventory
    participant C1 as Consumer 1 (group member)
    participant C2 as Consumer 2 (group member)

    P->>S: XADD event
    C1->>S: XREADGROUP GROUP g c1 STREAMS key >
    S-->>C1: entry 1700-0
    C1->>C1: process
    C1->>S: XACK key g 1700-0
    C2->>S: XREADGROUP ... >
    S-->>C2: entry 1701-0
    Note over C2,S: C2 crashes before XACK, so the entry stays pending
    C1->>S: XAUTOCLAIM key g c1 min-idle-time 0
    S-->>C1: entry 1701-0 reassigned
    C1->>S: XACK key g 1701-0
```

Group/consumer names, retry count, dead-letter handling and retention are **To Be Decided** ([O-01]).

### 20.2 Multiple Spring Boot instances

```mermaid
flowchart TB
    CL["Clients or load generator"] --> A1["App instance 1 :8080"]
    CL --> A2["App instance 2 :8081"]
    A1 --> R[("Shared Redis: cache, rate limits, locks, Pub/Sub, Streams")]
    A2 --> R
    A1 --> M[("Shared MySQL")]
    A2 --> M
```

What this proves: a cache fill by instance 1 is a hit for instance 2; the rate limit counts requests across both instances (shared bucket per IP); a lock held by one instance blocks the other.
