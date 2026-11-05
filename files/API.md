# API.md — Complete API Reference

> Part of the documentation set: [`PROJECT.md`](PROJECT.md) · [`IMPLEMENTATION.md`](IMPLEMENTATION.md) · [`FLOW.md`](FLOW.md) · [`ROADMAP.md`](ROADMAP.md) · **`API.md`**
> Decision tags: **[Confirmed]**, **[Resolved R-xx]**, **[Open O-xx]** — see [PROJECT.md §17](PROJECT.md#17-decision-register).

## 1. Conventions

| Item | Value |
|---|---|
| Base URL | `http://localhost:8080` (API root `/api`) |
| Content type | `application/json` for requests and responses |
| Success body | The DTO itself — **no wrapper** **[Confirmed]** |
| Money | JSON number with 2 decimals (`799.00`), stored as `DECIMAL(10,2)` |
| Time | ISO-8601 UTC (`2026-11-05T10:30:00Z`) |
| IDs | Positive integers. A non-numeric or non-positive id → `400 INVALID_PARAMETER` |
| Unknown JSON fields | Ignored (so a `status` field sent to product `PUT` has no effect — [R-04](PROJECT.md#172-resolved-decisions)) |

### 1.1 Endpoint summary

| # | Method | URL | Auth | Rate limited | Redis | Purpose |
|---|---|---|---|---|---|---|
| 1 | GET | `/api/products/{id}` | public | ✅ | cache-aside 15 min | Public product detail |
| 2 | GET | `/api/products` | public | ✅ | page cache 15 min | Public product list |
| 3 | GET | `/api/inventory/{productId}` | public | ✅ | cache-aside 30 s | Public inventory |
| 4 | POST | `/api/admin/products` | admin | – | invalidates lists | Create product |
| 5 | PUT | `/api/admin/products/{id}` | admin | – | invalidates product + lists | Update product |
| 6 | PATCH | `/api/admin/products/{id}/activate` | admin | – | invalidates product + inventory + lists | Activate |
| 7 | PATCH | `/api/admin/products/{id}/deactivate` | admin | – | invalidates product + inventory + lists | Deactivate |
| 8 | GET | `/api/admin/products` | admin | – | none | Admin product list |
| 9 | GET | `/api/admin/products/{id}` | admin | – | none | Admin product detail |
| 10 | GET | `/api/admin/inventory/{productId}` | admin | – | none | Admin inventory (with version) |
| 11 | PUT | `/api/admin/inventory/{productId}` | admin | – | invalidates inventory | Set stock |
| 12 | POST | `/api/admin/inventory/{productId}/increment` | admin | – | invalidates inventory | Add stock |
| 13 | POST | `/api/admin/inventory/{productId}/decrement` | admin | – | invalidates inventory | Remove stock |
| S | GET | `/actuator/health` | none | – | – | Health |
| D1–D6 | various | `/api/dev/redis/**` | admin | – | direct | **Development-only** Redis sandbox |

Endpoint 10 was added during the final API consolidation so that admins can obtain the inventory `version` needed by endpoint 11 **[Confirmed]**.

### 1.2 Admin authentication header **[Confirmed]**

```
X-Admin-Key: <configured-secret>
```

- Secret comes from the `ADMIN_API_KEY` environment variable; the app will not start without it ([R-26](PROJECT.md#172-resolved-decisions)).
- Missing **or** wrong key → `401` with code `INVALID_ADMIN_KEY`.
- Checked **before** body validation or any database access.

### 1.3 Rate-limit headers **[Confirmed + R-29]**

Returned on **every** response of rate-limited endpoints (1, 2, 3) — including `404` and `429` — whenever Redis was reachable.

| Header | Meaning | Example |
|---|---|---|
| `X-RateLimit-Limit` | Bucket capacity | `10` |
| `X-RateLimit-Remaining` | Whole tokens left after this request | `7` |
| `X-RateLimit-Reset` | Unix timestamp (seconds) when the bucket will be full again | `1793880012` |
| `Retry-After` | Integer seconds until a rejected client may retry. **Only on `429`** | `1` |

If Redis is unavailable the request is allowed (fail-open) and these headers are **omitted**.

### 1.4 Pagination behavior **[Confirmed]**

| Parameter | Default | Rules |
|---|---|---|
| `page` | `0` | integer ≥ 0 |
| `size` | `20` | integer 1–50 |

- Invalid or non-numeric values → `400 INVALID_PAGINATION` (e.g., `size=51`).
- Sort is fixed: **newest first** (`created_at DESC, id DESC`). A `sort` parameter is ignored.
- A page past the end returns `200` with `content: []` ([R-17](PROJECT.md#172-resolved-decisions)); such empty pages (page > 0) are not cached ([R-15](PROJECT.md#172-resolved-decisions)).
- `last` is `true` when `page + 1 >= totalPages` (also `true` when `totalPages = 0`).

Paged response shape (custom DTO, not Spring's `Page`):

| Field | Type | Meaning |
|---|---|---|
| `content` | array | Items on this page |
| `page` | int | Current page (0-based) |
| `size` | int | Requested page size |
| `totalElements` | long | Total matching items |
| `totalPages` | int | Total pages |
| `last` | boolean | Final page? |

### 1.5 Common error response **[Confirmed]**

```json
{
  "timestamp": "2026-11-05T10:30:00Z",
  "status": 404,
  "code": "PRODUCT_NOT_FOUND",
  "message": "Product not found",
  "path": "/api/products/101"
}
```

### 1.6 Validation error response **[Confirmed]** — all field errors together

```json
{
  "timestamp": "2026-11-05T10:30:00Z",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "path": "/api/admin/products",
  "fieldErrors": {
    "name": "Name is required",
    "price": "Price must be greater than zero"
  }
}
```

### 1.7 HTTP 429 response **[Confirmed]**

```
HTTP/1.1 429 Too Many Requests
Retry-After: 1
X-RateLimit-Limit: 10
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1793880010
Content-Type: application/json
```
```json
{
  "timestamp": "2026-11-05T10:30:00Z",
  "status": 429,
  "code": "RATE_LIMIT_EXCEEDED",
  "message": "Too many requests. Please try again later.",
  "path": "/api/products/101"
}
```

### 1.5.1 Complete error-code reference

| Code | HTTP | Meaning | Where |
|---|---|---|---|
| `VALIDATION_FAILED` | 400 | Body failed bean validation; `fieldErrors` present | create/update product, inventory writes |
| `MALFORMED_REQUEST` | 400 | Body is missing, not valid JSON, or has wrong types ([R-25](PROJECT.md#172-resolved-decisions)) | all bodies |
| `INVALID_PARAMETER` | 400 | Path/query value has wrong type or range (not pagination) ([R-25]) | `{id}`, `{productId}` |
| `INVALID_PAGINATION` | 400 | `page`/`size` invalid or `size > 50` | list endpoints |
| `INVALID_ADMIN_KEY` | 401 | `X-Admin-Key` missing or wrong | all `/api/admin/**`, `/api/dev/**` |
| `PRODUCT_NOT_FOUND` | 404 | Product does not exist (public: or is inactive) | product endpoints |
| `INVENTORY_NOT_FOUND` | 404 | Product does not exist (public: or is inactive) **when asked through an inventory endpoint**; also if a product somehow has no inventory row ([R-03](PROJECT.md#172-resolved-decisions)) | inventory endpoints |
| `ENDPOINT_NOT_FOUND` | 404 | Unknown URL ([R-25]) | any |
| `METHOD_NOT_ALLOWED` | 405 | Wrong HTTP method ([R-25]) | any |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | Body not `application/json` ([R-25]) | writes |
| `CONCURRENT_MODIFICATION` | 409 | Stale `version` (product or inventory) | product PUT, inventory PUT |
| `INSUFFICIENT_STOCK` | 409 | Decrement larger than current stock | decrement |
| `STOCK_LIMIT_EXCEEDED` | 409 | Increment would exceed 1,000,000,000 ([R-16](PROJECT.md#172-resolved-decisions)) | increment |
| `RATE_LIMIT_EXCEEDED` | 429 | Token bucket empty | endpoints 1–3 |
| `INTERNAL_SERVER_ERROR` | 500 | Unexpected failure; details only in server logs | any |
| `REDIS_KEY_NOT_FOUND` | 404 | Dev sandbox key absent | dev only |
| `REDIS_UNAVAILABLE` | 503 | Redis failed during a dev sandbox call | dev only |

Redis failures on business endpoints are **never** returned as errors; fallback rules apply ([PROJECT.md §13](PROJECT.md#13-redis-failure-handling-strategy)).

---

## 2. Public APIs

### 2.1 Get product — `GET /api/products/{id}`

| | |
|---|---|
| Purpose | Return an **ACTIVE** product |
| Auth | None |
| Rate limit | Yes (shared bucket) |
| Path params | `id` — product id |
| Query params | none |
| Request headers | none required |
| Request body | none |

**Success `200 OK`** — public DTO (no `version`):

```json
{
  "id": 101,
  "name": "Wireless Mouse",
  "description": "Bluetooth wireless mouse",
  "price": 799.00,
  "status": "ACTIVE"
}
```

| Status | Code | When |
|---|---|---|
| 400 | `INVALID_PARAMETER` | `id` not a positive integer |
| 404 | `PRODUCT_NOT_FOUND` | Missing **or** inactive |
| 429 | `RATE_LIMIT_EXCEEDED` | Bucket empty |
| 500 | `INTERNAL_SERVER_ERROR` | Unexpected |

**Redis:** `GET redis-lab:dev:product:{id}` → hit returns cached JSON. Miss → MySQL → `SET … EX 900`. Not-found/inactive results are **not** cached. Redis error → MySQL fallback. Flow: [FLOW.md §5–7](FLOW.md#5-product-detail-read--cache-hit).
**MySQL:** `SELECT … FROM products WHERE id = ? AND status = 'ACTIVE'` only on miss/fallback.
**Rules:** Inactive products behave exactly like missing ones.

### 2.2 List products — `GET /api/products`

| | |
|---|---|
| Purpose | Paginated list of **ACTIVE** products, newest first |
| Auth / rate limit | None / yes |
| Query params | `page` (default 0), `size` (default 20, max 50) |
| Request body | none |

Example: `GET /api/products?page=0&size=2`

**Success `200 OK`**

```json
{
  "content": [
    { "id": 102, "name": "Keyboard", "description": "Mechanical keyboard", "price": 1499.00, "status": "ACTIVE" },
    { "id": 101, "name": "Wireless Mouse", "description": "Bluetooth wireless mouse", "price": 799.00, "status": "ACTIVE" }
  ],
  "page": 0,
  "size": 2,
  "totalElements": 100,
  "totalPages": 50,
  "last": false
}
```

| Status | Code | When |
|---|---|---|
| 400 | `INVALID_PAGINATION` | Bad `page`/`size` |
| 429 | `RATE_LIMIT_EXCEEDED` | Bucket empty |
| 500 | `INTERNAL_SERVER_ERROR` | Unexpected |

**Redis:** key `redis-lab:dev:products:page:{page}:size:{size}`, whole response cached 15 min; all such keys are deleted on any product create/update/activate/deactivate. Empty out-of-range pages (page > 0) are not cached. Redis error → MySQL.
**MySQL:** `SELECT … WHERE status='ACTIVE' ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?` + `COUNT(*)`.

### 2.3 Get inventory — `GET /api/inventory/{productId}`

| | |
|---|---|
| Purpose | Current stock of an **ACTIVE** product |
| Auth / rate limit | None / yes |
| Path params | `productId` |

**Success `200 OK`**

```json
{ "productId": 101, "stockQuantity": 50 }
```

| Status | Code | When |
|---|---|---|
| 400 | `INVALID_PARAMETER` | Bad id |
| 404 | `INVENTORY_NOT_FOUND` | Product missing or inactive |
| 429 | `RATE_LIMIT_EXCEEDED` | Bucket empty |
| 500 | `INTERNAL_SERVER_ERROR` | Unexpected |

**Redis:** `redis-lab:dev:inventory:{productId}`, TTL **30 s**, cache-aside, no negative caching, MySQL fallback.
**MySQL (miss):** inventory row joined with product where `status = 'ACTIVE'`.
**Note:** The value can be stale for up to 30 s if an invalidation failed or raced ([PROJECT.md §11](PROJECT.md#known-cache-consistency-limits-accepted-in-phase-1-resolved-r-24)). Clients derive in-stock/out-of-stock from the number.

---

## 3. Admin product APIs

All require `X-Admin-Key`. Common additional errors: `401 INVALID_ADMIN_KEY`, `500`.

### 3.1 Create product — `POST /api/admin/products`

Request:

```json
{
  "name": "Wireless Mouse",
  "description": "Bluetooth wireless mouse",
  "price": 799.00
}
```

| Field | Required | Rules |
|---|---|---|
| `name` | yes | not blank, 3–100 chars (trimmed) |
| `description` | no | ≤ 500 chars |
| `price` | yes | > 0, ≤ 99,999,999.99, max 2 decimals |

`status` is not accepted (always `ACTIVE`).

**Success `201 Created`** (`Location: /api/admin/products/101`)

```json
{
  "id": 101,
  "name": "Wireless Mouse",
  "description": "Bluetooth wireless mouse",
  "price": 799.00,
  "status": "ACTIVE",
  "version": 0
}
```

| Status | Code | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` / `MALFORMED_REQUEST` | Bad body |
| 401 | `INVALID_ADMIN_KEY` | Key problem |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | Not JSON |

**MySQL:** one transaction: insert product, insert inventory (`stock_quantity = 0`, `version = 0`); both or neither.
**Redis:** after commit, delete all `products:page:*` keys. Failure is logged; response still `201`.
**Rules:** Names need not be unique.

### 3.2 Update product — `PUT /api/admin/products/{id}`

Full update; **all four fields required**.

```json
{
  "name": "Wireless Mouse Pro",
  "description": "Bluetooth wireless mouse with USB receiver",
  "price": 999.00,
  "version": 0
}
```

**Success `200 OK`**

```json
{
  "id": 101,
  "name": "Wireless Mouse Pro",
  "description": "Bluetooth wireless mouse with USB receiver",
  "price": 999.00,
  "status": "ACTIVE",
  "version": 1
}
```

| Status | Code | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Missing/invalid field (incl. missing `version`) |
| 404 | `PRODUCT_NOT_FOUND` | No such product (inactive products **can** be updated by admin) |
| 409 | `CONCURRENT_MODIFICATION` | `version` ≠ stored version |

**Business rules / order of checks:** (1) product exists → (2) `version` equals current, else 409 → (3) if name, description and price (`price` compared with `compareTo`) are unchanged → return current product with **no write and no invalidation**, `version` unchanged → (4) otherwise update; `version` increments.
**Redis:** after commit, delete `product:{id}` and all list pages.
**MySQL:** `UPDATE … WHERE id=? AND version=?` (Hibernate optimistic lock); a race that slips past step 2 also yields 409.

### 3.3 Activate product — `PATCH /api/admin/products/{id}/activate`
### 3.4 Deactivate product — `PATCH /api/admin/products/{id}/deactivate`

No body. **Success `200 OK`** (`id`, `status`, and `version`, [R-05](PROJECT.md#172-resolved-decisions)):

```json
{ "id": 101, "status": "INACTIVE", "version": 2 }
```

| Status | Code | When |
|---|---|---|
| 404 | `PRODUCT_NOT_FOUND` | No such product |

**Idempotent [Confirmed]:**

| Current | Request | Result |
|---|---|---|
| ACTIVE | activate | `200`, no write, no invalidation, same version |
| INACTIVE | deactivate | `200`, no write, no invalidation |
| INACTIVE | activate | status → ACTIVE, version +1 |
| ACTIVE | deactivate | status → INACTIVE, version +1 |

**Redis (only on real change, after commit):** delete `product:{id}`, `inventory:{id}`, and all list pages.
**Rules:** Product and inventory rows and stock quantity are preserved. Because `version` changes, an admin holding an older version gets `409` on the next `PUT` (intended).

### 3.5 Admin product list — `GET /api/admin/products`

Query: `page`, `size` (same rules as §1.4). Returns ACTIVE **and** INACTIVE products, newest first, items include `version`.

```json
{
  "content": [
    { "id": 102, "name": "Keyboard", "description": "Mechanical keyboard", "price": 1499.00, "status": "INACTIVE", "version": 2 },
    { "id": 101, "name": "Wireless Mouse", "description": "Bluetooth wireless mouse", "price": 799.00, "status": "ACTIVE", "version": 0 }
  ],
  "page": 0, "size": 20, "totalElements": 2, "totalPages": 1, "last": true
}
```

Errors: `400 INVALID_PAGINATION`, `401`. **Redis:** none — always MySQL. **MySQL:** no status filter.

### 3.6 Admin product detail — `GET /api/admin/products/{id}`

**Success `200 OK`**

```json
{
  "id": 101,
  "name": "Wireless Mouse",
  "description": "Bluetooth wireless mouse",
  "price": 799.00,
  "status": "ACTIVE",
  "version": 0
}
```

Errors: `400 INVALID_PARAMETER`, `401`, `404 PRODUCT_NOT_FOUND`. **Redis:** none. Product fields only; inventory is a separate API.

---

## 4. Admin inventory APIs

Admin inventory DTO: `{ productId, stockQuantity, version }`. Common errors: `401`, `404 INVENTORY_NOT_FOUND` (product does not exist; inactive products **are** accessible), `400 INVALID_PARAMETER`.

### 4.1 Get inventory (admin) — `GET /api/admin/inventory/{productId}`

```json
{ "productId": 101, "stockQuantity": 50, "version": 3 }
```

**Redis:** none (always MySQL) so the version is authoritative.

### 4.2 Set inventory — `PUT /api/admin/inventory/{productId}`

```json
{ "stockQuantity": 70, "version": 3 }
```

| Field | Rules |
|---|---|
| `stockQuantity` | required, 0 – 1,000,000,000 |
| `version` | required |

**Success `200 OK`**

```json
{ "productId": 101, "stockQuantity": 70, "version": 4 }
```

| Status | Code | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` | negative / missing / too large |
| 404 | `INVENTORY_NOT_FOUND` | no such product |
| 409 | `CONCURRENT_MODIFICATION` | stale `version` |

**Rules:** Replaces the quantity. If the quantity equals the stored value, nothing is written (no version bump, no invalidation) and the current state is returned ([R-17](PROJECT.md#172-resolved-decisions)).
**MySQL:** optimistic locking (`@Version`). **Redis:** delete `inventory:{productId}` after commit.

### 4.3 Increment inventory — `POST /api/admin/inventory/{productId}/increment`

```json
{ "quantity": 20 }
```

**Success `200 OK`** (stock was 70, version was 4)

```json
{ "productId": 101, "stockQuantity": 90, "version": 5 }
```

| Status | Code | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` | `quantity` missing, ≤ 0, or > 1,000,000,000 |
| 404 | `INVENTORY_NOT_FOUND` | no such product |
| 409 | `STOCK_LIMIT_EXCEEDED` | result would exceed 1,000,000,000 |

**MySQL:** atomic `UPDATE inventory SET stock_quantity = stock_quantity + :q, version = version + 1 … WHERE product_id = :id AND stock_quantity <= :max - :q`. No `version` is required in the request (atomic SQL cannot lose updates). **Redis:** delete `inventory:{productId}` after commit.

### 4.4 Decrement inventory — `POST /api/admin/inventory/{productId}/decrement`

```json
{ "quantity": 10 }
```

**Success `200 OK`**

```json
{ "productId": 101, "stockQuantity": 80, "version": 6 }
```

| Status | Code | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` | `quantity` missing, ≤ 0, or too large |
| 404 | `INVENTORY_NOT_FOUND` | no such product |
| 409 | `INSUFFICIENT_STOCK` | `quantity` > current stock |

**MySQL:** conditional atomic update (`… WHERE product_id = :id AND stock_quantity >= :q`); 0 rows updated → the service checks existence to choose between 404 and 409. Stock can never go negative, even under concurrency. **Redis:** delete `inventory:{productId}` after commit.

**Concurrency summary [Confirmed]**

| Operation | Strategy |
|---|---|
| Set | Optimistic locking (`version`) |
| Increment | Atomic SQL |
| Decrement | Atomic conditional SQL |

---

## 5. Support endpoint

### `GET /actuator/health` (no auth, not rate limited)

```json
{
  "status": "UP",
  "components": {
    "db":    { "status": "UP" },
    "redis": { "status": "UP" }
  }
}
```

- Only statuses are shown — no connection details **[Confirmed]**.
- With Redis unavailable the **overall status stays `UP`** and the `redis` component shows `DEGRADED` ([R-12](PROJECT.md#172-resolved-decisions); implementation in [IMPLEMENTATION.md §22](IMPLEMENTATION.md#22-actuator-and-logging)). MySQL `DOWN` → overall `DOWN` (HTTP 503).

---

## 6. Development-only Redis learning endpoints

> ⚠️ **Temporary. Enabled only with the `dev` Spring profile** (`spring.profiles.active=dev`) and `app.dev-endpoints.enabled=true`. They are removed or disabled before the final application. Not part of the product API.
> Hardening ([R-13](PROJECT.md#172-resolved-decisions)): require `X-Admin-Key`; every key is stored as `redis-lab:dev:sandbox:{key}` so they can never touch real cache keys; key pattern `[A-Za-z0-9:_-]{1,100}`; value ≤ 1,000 chars; **no fail-open** — Redis errors return `503 REDIS_UNAVAILABLE`; not rate limited.

Each endpoint mirrors a Redis CLI command so you can compare the two:

| # | Method & URL | Redis command | Request | Success response |
|---|---|---|---|---|
| D1 | `POST /api/dev/redis/keys` | `SET key value [EX s]` | `{ "key": "demo", "value": "hello", "ttlSeconds": 60 }` (`ttlSeconds` optional, 1–86400) | `200` `{ "key": "demo", "storedAs": "redis-lab:dev:sandbox:demo", "ttlSeconds": 60 }` |
| D2 | `GET /api/dev/redis/keys/{key}` | `GET` | – | `200` `{ "key": "demo", "value": "hello" }` · `404 REDIS_KEY_NOT_FOUND` |
| D3 | `DELETE /api/dev/redis/keys/{key}` | `DEL` | – | `200` `{ "key": "demo", "deleted": true }` (`false` if absent) |
| D4 | `GET /api/dev/redis/keys/{key}/ttl` | `TTL` | – | `200` `{ "key": "demo", "ttlSeconds": 57 }` (raw Redis meaning: `-1` no expiry, `-2` missing) |
| D5 | `POST /api/dev/redis/keys/{key}/expire` | `EXPIRE` | `{ "seconds": 30 }` | `200` `{ "key": "demo", "applied": true }` (`false` if key missing) |
| D6 | `GET /api/dev/redis/keys/{key}/exists` | `EXISTS` | – | `200` `{ "key": "demo", "exists": true }` |

D6 was added because `EXISTS` was in the Stage 2 command list without an endpoint ([R-13]).

Example comparison:

```bash
curl -X POST localhost:8080/api/dev/redis/keys -H "X-Admin-Key: $ADMIN_API_KEY" \
     -H "Content-Type: application/json" -d '{"key":"demo","value":"hello","ttlSeconds":60}'

docker exec -it redislab-redis redis-cli GET redis-lab:dev:sandbox:demo
```

Errors: `400 VALIDATION_FAILED` (bad key/value/seconds), `401 INVALID_ADMIN_KEY`, `404 REDIS_KEY_NOT_FOUND`, `503 REDIS_UNAVAILABLE`. When the dev profile is off these URLs return `404 ENDPOINT_NOT_FOUND`.

---

## 7. API documentation tooling

Swagger UI (`/swagger-ui.html`, `/v3/api-docs`) is enabled in the `dev` profile only. Springdoc is added in Stage 4. Postman holds saved scenarios (e.g., "10 requests then 429", "update with stale version"). **[Confirmed]**

## 8. Quick scenario checklist (for Postman)

| Scenario | Expected |
|---|---|
| Create product, then `GET /api/inventory/{id}` | stock `0` |
| Decrement 1 from stock 0 | `409 INSUFFICIENT_STOCK` |
| Deactivate, then `GET /api/products/{id}` | `404 PRODUCT_NOT_FOUND`; admin detail still `200` |
| Two `PUT`s with the same `version` | first `200`, second `409 CONCURRENT_MODIFICATION` |
| 11 quick public `GET`s from one IP | 10 × `200`, then `429` with `Retry-After` |
| Missing `X-Admin-Key` | `401 INVALID_ADMIN_KEY` |
| `GET /api/products?size=51` | `400 INVALID_PAGINATION` |
