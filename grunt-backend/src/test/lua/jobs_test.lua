-- Runs the production script against a deterministic in-memory Redis command model, without a service.
-- From repository root: lua grunt-backend/src/test/lua/jobs_test.lua
local db, now = {}, 100000
redis = {}
local function size(value) local n = 0; for _ in pairs(value or {}) do n = n + 1 end; return n end
local function prune(key) if type(db[key]) == 'table' and next(db[key]) == nil then db[key] = nil end end
local function num(value) if value == '-inf' then return -math.huge elseif value == '+inf' then return math.huge end; return tonumber(value) end
function redis.call(command, ...)
    local a = {...}; local key = a[1]; local value = db[key]
    if command == 'TIME' then return {tostring(math.floor(now / 1000)), tostring((now % 1000) * 1000)} end
    if command == 'EXISTS' then return value ~= nil and 1 or 0 end
    if command == 'GET' then return value or false end
    if command == 'DEL' then db[key] = nil; return 1 end
    if command == 'HGET' then return value and value[a[2]] or false end
    if command == 'HEXISTS' or command == 'SISMEMBER' then return value and value[a[2]] ~= nil and 1 or 0 end
    if command == 'SCARD' then return size(value) end
    if command == 'LLEN' then return value and #value or 0 end
    if command == 'LPOP' then
        local result = value and table.remove(value, 1) or false; prune(key); return result
    end
    if command == 'LTRIM' then
        local list, result = value or {}, {}
        local first = a[2] < 0 and #list + a[2] + 1 or a[2] + 1
        local last = a[3] < 0 and #list + a[3] + 1 or a[3] + 1
        for i = math.max(1, first), math.min(#list, last) do result[#result + 1] = list[i] end
        db[key] = result; prune(key); return 'OK'
    end
    if command == 'ZSCORE' then return value and value[a[2]] or false end
    if command == 'ZRANGEBYSCORE' then
        local result = {}
        for id, score in pairs(value or {}) do
            if score >= num(a[2]) and score <= num(a[3]) then result[#result + 1] = id end
        end
        table.sort(result, function(x, y) if value[x] == value[y] then return x < y end; return value[x] < value[y] end)
        local limited = {}; for i = (a[5] or 0) + 1, math.min(#result, (a[5] or 0) + (a[6] or #result)) do limited[#limited + 1] = result[i] end
        return limited
    end
    if command == 'INCRBY' or command == 'DECRBY' then
        db[key] = tostring(tonumber(value or '0') + tonumber(a[2]) * (command == 'INCRBY' and 1 or -1)); return tonumber(db[key])
    end
    db[key] = value or {}; value = db[key]
    if command == 'HSET' then for i = 2, #a, 2 do value[a[i]] = tostring(a[i + 1]) end
    elseif command == 'HDEL' or command == 'SREM' or command == 'ZREM' then for i = 2, #a do value[a[i]] = nil end
    elseif command == 'SADD' then for i = 2, #a do value[a[i]] = true end
    elseif command == 'ZADD' then value[a[3]] = tonumber(a[2])
    elseif command == 'RPUSH' then for i = 2, #a do value[#value + 1] = a[i] end
    else error('Unsupported command: ' .. command) end
    prune(key); return 1
end
function redis.error_reply(message) error(message) end
local script = assert(loadfile('grunt-backend/src/main/resources/redis/jobs.lua'))
local function execute(op, id, ...)
    KEYS = {'queue', 'leases', 'active', 'bytes', 'total', 'staged', 'terminal', 'job:' .. id, 'cancelled', 'wakeups'}
    ARGV = {op, 'job:', ...}
    return script()
end
local assertions = 0
local function equal(expected, actual) assertions = assertions + 1; assert(expected == actual, tostring(expected) .. ' ~= ' .. tostring(actual)) end
local function reserve(id, bytes) return execute('reserve', id, id, tostring(bytes), '2', '100') end
local function create(id) return execute('create', id, id, '/work/' .. id, '2026-01-01T00:00:00Z') end
local function claim(token) return execute('claim', '', token, '1000', '2026-01-01T00:00:01Z', '16') end
local function complete(id, token, status, result) return execute('complete', id, id, token, status, '2026-01-01T00:00:02Z', '', result or '') end

equal('1', reserve('a', 60)); equal('0', reserve('b', 50)); equal('1', reserve('b', 40)); equal('0', reserve('c', 1))
equal('1', create('a')); equal('1', create('a')); equal(1, redis.call('LLEN', 'queue')); equal('1', create('b'))
equal('a', claim('token-a')); equal('RUNNING', redis.call('HGET', 'job:a', 'status'))
equal('0', execute('renew', 'a', 'a', 'wrong-token', '1000'))
equal('1', execute('renew', 'a', 'a', 'token-a', '1000'))
redis.call('HSET', 'job:a', 'error', 'stale error')
equal('1', complete('a', 'token-a', 'SUCCESS', 'attempts/token-a/result.zip'))
equal(false, redis.call('HGET', 'job:a', 'error')); equal('1', redis.call('HGET', 'job:a', 'resultAvailable'))
equal(false, redis.call('ZSCORE', 'leases', 'a')); equal(1, redis.call('SCARD', 'active'))
local terminalTime = redis.call('ZSCORE', 'terminal', 'a'); now = now + 1
equal('1', complete('a', 'token-a', 'SUCCESS', 'attempts/token-a/result.zip'))
equal(terminalTime, redis.call('ZSCORE', 'terminal', 'a'))
equal('0', complete('a', 'wrong-token', 'FAILED')); equal('0', execute('cancelUpload', 'a', 'a'))
equal('0', reserve('c', 1)) -- Retained uploads continue to count towards the byte quota.
equal('b', claim('old-b')); now = now + 1001
equal('0', execute('renew', 'b', 'b', 'old-b', '1000'))
equal('0', complete('b', 'old-b', 'SUCCESS', 'stale.zip'))
equal('b', claim('new-b')); equal('0', complete('b', 'old-b', 'SUCCESS', 'stale.zip'))
equal('0', execute('beginDelete', 'b', 'b', '0')) -- Never clean an active claim.
equal('1', complete('b', 'new-b', 'FAILED')); equal('0', redis.call('HGET', 'job:b', 'resultAvailable'))
equal('0', execute('beginDelete', 'a', 'a', '5000'))
now = now + 5001
equal('1', execute('beginDelete', 'a', 'a', '5000')); equal('0', redis.call('HGET', 'job:a', 'resultAvailable'))
equal('1', execute('finishDelete', 'a', 'a')); equal(false, redis.call('HGET', 'job:a', 'status'))
equal('40', redis.call('GET', 'total')); equal('1', reserve('c', 50))
equal('0', execute('cancelUpload', 'c', 'c', '5000')) -- Redis time guards against a fast janitor host clock.
equal('1', execute('cancelUpload', 'c', 'c')); equal('0', create('c'))
equal('1', execute('releaseUpload', 'c', 'c')); equal('1', execute('releaseUpload', 'c', 'c'))
equal('40', redis.call('GET', 'total')); equal('0', create('c'))
-- Duplicate pending entries and a lost claim reply cannot create a second claim until the first lease expires.
redis.call('HSET', 'job:legacy', 'status', 'QUEUED'); redis.call('RPUSH', 'queue', 'legacy', 'legacy')
equal('legacy', claim('legacy-token')); equal('', claim('duplicate-token'))
now = now + 1001; equal('legacy', claim('recovered-token'))
equal('0', complete('legacy', 'legacy-token', 'SUCCESS', 'old.zip'))
equal('1', complete('legacy', 'recovered-token', 'SUCCESS', 'new.zip'))
print('PASS: admission, quotas, atomic create/claim/ack, leases, fencing, idempotency, cleanup guards (' .. assertions .. ' assertions)')
