package com.assignment.money_transfer_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitService {

    private static final String RATE_LIMIT_PREFIX = "ratelimit:transfer:";
    private static final int MAX_REQUESTS = 10;
    private static final Duration WINDOW = Duration.ofSeconds(60);
    
    private final StringRedisTemplate redisTemplate;

    public boolean isAllowed(Long accountId) {
        String key = RATE_LIMIT_PREFIX + accountId;
        long currentTime = System.currentTimeMillis();
        long windowStart = currentTime - WINDOW.toMillis();
        
        redisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);
        
        Long currentCount = redisTemplate.opsForZSet().count(key, windowStart, currentTime);
        
        boolean allowed = currentCount != null && currentCount < MAX_REQUESTS;
        
        if (allowed) {
            redisTemplate.opsForZSet().add(key, String.valueOf(currentTime), currentTime);
            redisTemplate.expire(key, WINDOW.getSeconds(), TimeUnit.SECONDS);
        } else {
            log.warn("Rate limit exceeded for account: {}", accountId);
        }
        
        return allowed;
    }

    public long getRetryAfterSeconds(Long accountId) {
        String key = RATE_LIMIT_PREFIX + accountId;
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return ttl != null && ttl > 0 ? ttl : 0;
    }
}
