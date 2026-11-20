local key = KEYS[1]
local now = tonumber(ARGV[1])
local limit = tonumber(ARGV[2])
local window = tonumber(ARGV[3])
local requestId = ARGV[4]

redis.call('ZREMRANGEBYSCORE', key, '-inf', now - window)

if redis.call('ZCARD', key) >= limit then
  local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
  return math.max(1, math.ceil((tonumber(oldest[2]) + window - now) / 1000))
end

redis.call('ZADD', key, now, requestId)
redis.call('PEXPIRE', key, window)
return 0
