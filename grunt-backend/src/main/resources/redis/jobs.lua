-- One atomic operation per transition. Lease time is Redis server time, not host clocks.
-- KEYS: pending, leases, active, uploadBytes, totalBytes, staged, terminal, job, cancelledUploads, wakeups
local op, prefix = ARGV[1], ARGV[2]
local clock = redis.call('TIME')
local now = tonumber(clock[1]) * 1000 + math.floor(tonumber(clock[2]) / 1000)
local function release(id)
    local bytes = redis.call('HGET', KEYS[4], id)
    if bytes then
        redis.call('DECRBY', KEYS[5], bytes)
        redis.call('HDEL', KEYS[4], id)
    end
    redis.call('SREM', KEYS[3], id)
    redis.call('ZREM', KEYS[6], id)
    redis.call('SREM', KEYS[9], id)
end
if op == 'reserve' then
    local id, bytes = ARGV[3], tonumber(ARGV[4])
    if redis.call('HEXISTS', KEYS[4], id) == 1 then return '1' end
    if math.max(redis.call('SCARD', KEYS[3]), redis.call('LLEN', KEYS[1])) >= tonumber(ARGV[5]) then return '0' end
    if tonumber(redis.call('GET', KEYS[5]) or '0') + bytes > tonumber(ARGV[6]) then return '0' end
    redis.call('HSET', KEYS[4], id, bytes)
    redis.call('INCRBY', KEYS[5], bytes)
    redis.call('SADD', KEYS[3], id)
    redis.call('ZADD', KEYS[6], now, id)
    return '1'
elseif op == 'create' then
    local id = ARGV[3]
    if redis.call('EXISTS', KEYS[8]) == 1 then return '1' end
    if not redis.call('ZSCORE', KEYS[6], id) or redis.call('SISMEMBER', KEYS[9], id) == 1 then return '0' end
    redis.call('HSET', KEYS[8], 'id', id, 'dir', ARGV[4], 'status', 'QUEUED',
        'createdAt', ARGV[5], 'updatedAt', ARGV[5], 'resultAvailable', '0')
    redis.call('RPUSH', KEYS[1], id)
    redis.call('RPUSH', KEYS[10], '1')
    redis.call('LTRIM', KEYS[10], -math.max(1, tonumber(ARGV[6] or '2')), -1)
    redis.call('ZREM', KEYS[6], id)
    return '1'
elseif op == 'cancelUpload' then
    if redis.call('EXISTS', KEYS[8]) == 1 then return '0' end
    local staged = redis.call('ZSCORE', KEYS[6], ARGV[3])
    if not staged or tonumber(staged) > now - tonumber(ARGV[4] or '0') then return '0' end
    redis.call('SADD', KEYS[9], ARGV[3])
    return '1'
elseif op == 'releaseUpload' then
    if redis.call('EXISTS', KEYS[8]) == 1 then return '0' end
    release(ARGV[3])
    return '1'
elseif op == 'claim' then
    local token, lease, stamp, batch = ARGV[3], tonumber(ARGV[4]), ARGV[5], tonumber(ARGV[6])
    for _, id in ipairs(redis.call('ZRANGEBYSCORE', KEYS[2], '-inf', now, 'LIMIT', 0, batch)) do
        local key = prefix .. id
        if redis.call('HGET', key, 'status') == 'RUNNING' then
            redis.call('HSET', key, 'status', 'QUEUED', 'updatedAt', stamp)
            redis.call('HDEL', key, 'claimToken')
            redis.call('RPUSH', KEYS[1], id)
        end
        redis.call('ZREM', KEYS[2], id)
    end
    for i = 1, batch do
        local id = redis.call('LPOP', KEYS[1])
        if not id then return '' end
        local key = prefix .. id
        if redis.call('HGET', key, 'status') == 'QUEUED' then
            redis.call('HSET', key, 'status', 'RUNNING', 'updatedAt', stamp, 'claimToken', token)
            redis.call('ZADD', KEYS[2], now + lease, id)
            redis.call('SADD', KEYS[3], id)
            return id
        end
    end
    return ''
elseif op == 'renew' or op == 'complete' then
    local id, token = ARGV[3], ARGV[4]
    if redis.call('HGET', KEYS[8], 'claimToken') ~= token then return '0' end
    local status = redis.call('HGET', KEYS[8], 'status')
    -- A lost reply can be retried after the atomic terminal commit/ack.
    if op == 'complete' and (status == 'SUCCESS' or status == 'FAILED') then return '1' end
    if status ~= 'RUNNING' or tonumber(redis.call('ZSCORE', KEYS[2], id) or '0') <= now then return '0' end
    if op == 'renew' then
        redis.call('ZADD', KEYS[2], now + tonumber(ARGV[5]), id)
    else
        redis.call('HSET', KEYS[8], 'status', ARGV[5], 'updatedAt', ARGV[6],
            'resultAvailable', ARGV[8] ~= '' and '1' or '0', 'resultFile', ARGV[8])
        if ARGV[7] == '' then redis.call('HDEL', KEYS[8], 'error')
        else redis.call('HSET', KEYS[8], 'error', ARGV[7]) end
        redis.call('ZREM', KEYS[2], id)
        redis.call('SREM', KEYS[3], id)
        redis.call('ZADD', KEYS[7], now, id)
    end
    return '1'
elseif op == 'beginDelete' then
    local id = ARGV[3]
    local status = redis.call('HGET', KEYS[8], 'status')
    local finished = tonumber(redis.call('ZSCORE', KEYS[7], id) or tostring(now))
    if (status ~= 'SUCCESS' and status ~= 'FAILED') or finished > now - tonumber(ARGV[4]) then return '0' end
    if redis.call('ZSCORE', KEYS[2], id) then return '0' end
    redis.call('HSET', KEYS[8], 'deleting', '1', 'resultAvailable', '0')
    return '1'
elseif op == 'finishDelete' then
    if redis.call('HGET', KEYS[8], 'deleting') ~= '1' then return '0' end
    release(ARGV[3])
    redis.call('ZREM', KEYS[7], ARGV[3])
    redis.call('DEL', KEYS[8])
    return '1'
end
return redis.error_reply('Unknown job operation')
