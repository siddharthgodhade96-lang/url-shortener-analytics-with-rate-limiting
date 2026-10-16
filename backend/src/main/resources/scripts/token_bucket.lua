local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local cost = tonumber(ARGV[3])
local expiry_ms = tonumber(ARGV[4])

local redis_time = redis.call('TIME')
local now = tonumber(redis_time[1]) + tonumber(redis_time[2]) / 1000000
local state = redis.call('HMGET', key, 'tokens', 'last_refill_ts')
local tokens = tonumber(state[1])
local last_refill_ts = tonumber(state[2])

if tokens == nil or last_refill_ts == nil then
    tokens = capacity
    last_refill_ts = now
end

local elapsed = math.max(0, now - last_refill_ts)
tokens = math.min(capacity, tokens + elapsed * refill_rate)

local allowed = 0
local retry_after_seconds = 0
if tokens >= cost then
    tokens = tokens - cost
    allowed = 1
elseif cost > 0 then
    retry_after_seconds = math.ceil((cost - tokens) / refill_rate)
end

redis.call('HSET', key,
    'tokens', string.format('%.17g', tokens),
    'last_refill_ts', string.format('%.17g', now))
redis.call('PEXPIRE', key, expiry_ms)

return {allowed, string.format('%.17g', tokens), retry_after_seconds}
