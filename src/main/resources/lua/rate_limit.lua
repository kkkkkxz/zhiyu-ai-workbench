-- KEYS[1] redis key
-- ARGV[1] limit
-- ARGV[2] window seconds
local current = redis.call('INCR', KEYS[1])
if tonumber(current) == 1 then
    redis.call('EXPIRE', KEYS[1], tonumber(ARGV[2]))
end
if tonumber(current) > tonumber(ARGV[1]) then
    return 0
end
return 1
