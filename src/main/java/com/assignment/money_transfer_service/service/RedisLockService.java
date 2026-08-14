package com.assignment.money_transfer_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisLockService {

    private static final String LOCK_PREFIX = "lock:account:";
    private static final long LOCK_TTL_SECONDS = 5;
    
    private final StringRedisTemplate redisTemplate;

    public String acquireLock(Long accountId) {
        String lockKey = LOCK_PREFIX + accountId;
        String token = UUID.randomUUID().toString();
        
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, token, LOCK_TTL_SECONDS, TimeUnit.SECONDS);
        
        if (Boolean.TRUE.equals(acquired)) {
            log.debug("Acquired lock for account: {}", accountId);
            return token;
        }
        
        log.debug("Failed to acquire lock for account: {}", accountId);
        return null;
    }

    public boolean releaseLock(Long accountId, String token) {
        String lockKey = LOCK_PREFIX + accountId;
        
        String luaScript = """
            if redis.call("get", KEYS[1]) == ARGV[1] then
                return redis.call("del", KEYS[1])
            else
                return 0
            end
            """;
        
        Long result = redisTemplate.execute(
                (org.springframework.data.redis.core.RedisCallback<Long>) connection -> 
                        connection.scriptingCommands()
                        .eval(luaScript.getBytes(),
                                org.springframework.data.redis.connection.ReturnType.INTEGER,
                                1,
                                lockKey.getBytes(),
                                token.getBytes())
        );
        
        boolean released = result != null && result == 1;
        log.debug("Released lock for account: {}, success: {}", accountId, released);
        return released;
    }

    public boolean isEventProcessed(String key, String eventId) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(key, eventId));
    }

    public void markEventProcessed(String key, String eventId) {
        redisTemplate.opsForSet().add(key, eventId);
        redisTemplate.expire(key, 24, TimeUnit.HOURS);
    }
}
