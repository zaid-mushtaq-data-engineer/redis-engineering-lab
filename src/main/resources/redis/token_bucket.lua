-- KEYS[1] bucket key
-- ARGV[1] capacity, ARGV[2] refill tokens/second, ARGV[3] tokens requested, ARGV[4] seconds to keep key after full
local capacity    = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local requested   = tonumber(ARGV[3])
local ttl_after   = tonumber(ARGV[4])

local t = redis.call('TIME')
local now_ms = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)

local data   = redis.call('HMGET', KEYS[1], 'tokens', 'last_refill_time')
local tokens = tonumber(data[1])
local last   = tonumber(data[2])
if tokens == nil or last == nil then
  tokens = capacity
  last   = now_ms
end

local elapsed = math.max(0, now_ms - last)
tokens = math.min(capacity, tokens + (elapsed / 1000) * refill_rate)

local allowed, retry_after = 0, 0
if tokens >= requested then
  tokens  = tokens - requested
  allowed = 1
else
  retry_after = math.ceil((requested - tokens) / refill_rate)
end

local seconds_to_full = math.ceil((capacity - tokens) / refill_rate)
redis.call('HSET', KEYS[1], 'tokens', tostring(tokens), 'last_refill_time', now_ms)
redis.call('EXPIRE', KEYS[1], seconds_to_full + ttl_after)

return { allowed, math.floor(tokens), retry_after, math.floor(now_ms / 1000) + seconds_to_full }
