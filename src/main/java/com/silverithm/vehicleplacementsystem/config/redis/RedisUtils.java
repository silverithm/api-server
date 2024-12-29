package com.silverithm.vehicleplacementsystem.config.redis;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RedisUtils {
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisTemplate<String, Object> redisBlackListTemplate;

    public RedisUtils(RedisTemplate<String, Object> redisTemplate,
                      RedisTemplate<String, Object> redisBlackListTemplate) {
        this.redisTemplate = redisTemplate;
        this.redisBlackListTemplate = redisBlackListTemplate;
    }

    public void set(String key, String userEmail, int minutes) {
        redisTemplate.opsForValue().set(key, userEmail, minutes, TimeUnit.MINUTES);
    }

    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public boolean delete(String key) {
        return Boolean.TRUE.equals(redisTemplate.delete(key));
    }

    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public void setBlackList(String key, String userEmail, Long milliSeconds) {
        redisBlackListTemplate.opsForValue().set(key, userEmail, milliSeconds, TimeUnit.MILLISECONDS);
    }

    public Object getBlackList(String key) {
        return redisBlackListTemplate.opsForValue().get(key);
    }

    public boolean deleteBlackList(String key) {
        return Boolean.TRUE.equals(redisBlackListTemplate.delete(key));
    }

    public boolean hasKeyBlackList(String key) {
        return Boolean.TRUE.equals(redisBlackListTemplate.hasKey(key));
    }

    public void deleteAll() {
        redisTemplate.delete(Objects.requireNonNull(redisTemplate.keys("*")));
    }

    public void decrementDailyRequestCount(String key) {
        Long currentCount = redisTemplate.opsForValue().decrement(key, 1);
        log.info("Current count: {}", currentCount);
    }

    public boolean isExceededDailyRequestLimit(String key, int dailyLimit) {
        Long currentCount = incrementRequestCount(key);
        log.info("Current count: {}", currentCount);

        if (isFirstRequest(currentCount)) {
            setExpirationToNextMidnight(key);
        }

        return isLimitExceeded(currentCount, dailyLimit);
    }

    private Long incrementRequestCount(String key) {
        return redisTemplate.opsForValue().increment(key, 1);
    }

    private boolean isFirstRequest(Long count) {
        return count == 1;
    }

    private void setExpirationToNextMidnight(String key) {
        redisTemplate.expire(key, calculateSecondsUntilNextMidnight(), TimeUnit.SECONDS);
    }

    private long calculateSecondsUntilNextMidnight() {
//        LocalDateTime now = LocalDateTime.now();
//        LocalDateTime nextMidnight = now.plusDays(1).with(LocalTime.MIDNIGHT);
//        return ChronoUnit.SECONDS.between(now, nextMidnight);
        return 180L;

    }

    private boolean isLimitExceeded(Long currentCount, int limit) {
        log.info("isLimitExceeded: currentCount={}, limit={}, boolean={}", currentCount, limit, currentCount >= limit);
        return currentCount > limit;
    }

}
