package net.spartanb312.grunteon.backend

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class RedisJobQueue(
    private val redis: StringRedisTemplate,
    private val properties: BackendProperties,
) {
    fun enqueue(jobId: String) {
        redis.opsForList().rightPush(properties.queueKey, jobId)
    }

    fun poll(timeout: Duration): String? {
        return redis.opsForList().leftPop(properties.queueKey, timeout)
    }
}
