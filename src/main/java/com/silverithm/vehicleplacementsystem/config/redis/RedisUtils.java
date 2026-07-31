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

    private static final int MAX_DISPATCH_LIMIT = 5;

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisTemplate<String, Object> redisBlackListTemplate;
    private final RedisTemplate<String, Integer> integerRedisTemplate;

    public RedisUtils(RedisTemplate<String, Object> redisTemplate,
                      RedisTemplate<String, Object> redisBlackListTemplate,
                      RedisTemplate<String, Integer> integerRedisTemplate) {
        this.redisTemplate = redisTemplate;
        this.redisBlackListTemplate = redisBlackListTemplate;
        this.integerRedisTemplate = integerRedisTemplate;
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
        if (integerRedisTemplate.opsForValue().get(key) > 0) {
            Long currentCount = integerRedisTemplate.opsForValue().decrement(key, 1);
            log.info(key + ":Current count: {}", currentCount);
        }
    }

    private Long incrementRequestCount(String key) {
        return integerRedisTemplate.opsForValue().increment(key, 1);
    }

    public boolean isExceededDailyRequestLimit(String key) {
        Long currentCount = incrementRequestCount(key);
        log.info("Current count: {}", currentCount);

        if (isFirstRequest(currentCount)) {
            setExpirationToNextMidnight(key);
        }

        return isLimitExceeded(currentCount, MAX_DISPATCH_LIMIT);
    }


    private boolean isFirstRequest(Long count) {
        return count == 1;
    }

    private void setExpirationToNextMidnight(String key) {
        redisTemplate.expire(key, calculateSecondsUntilNextMidnight(), TimeUnit.SECONDS);
    }

    private long calculateSecondsUntilNextMidnight() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextMidnight = now.plusDays(1).with(LocalTime.MIDNIGHT);
        return ChronoUnit.SECONDS.between(now, nextMidnight);
    }

    private boolean isLimitExceeded(Long currentCount, int limit) {
        log.info("isLimitExceeded: currentCount={}, limit={}, boolean={}", currentCount, limit, currentCount >= limit);
        return currentCount > limit;
    }

    // 체험하기 어뷰즈 방지: IP당 시간당 3회, 전체 하루 200회
    private static final int MAX_DEMO_START_PER_IP_PER_HOUR = 3;
    private static final int MAX_DEMO_START_PER_DAY_GLOBAL = 200;

    public boolean isExceededDemoStartIpLimit(String clientIp) {
        String key = "demo:start:ip:" + clientIp;
        Long count = incrementRequestCount(key);
        if (isFirstRequest(count)) {
            integerRedisTemplate.expire(key, 1, TimeUnit.HOURS);
        }
        return isLimitExceeded(count, MAX_DEMO_START_PER_IP_PER_HOUR);
    }

    public boolean isExceededDemoStartGlobalLimit() {
        String key = "demo:start:global";
        Long count = incrementRequestCount(key);
        if (isFirstRequest(count)) {
            integerRedisTemplate.expire(key, calculateSecondsUntilNextMidnight(), TimeUnit.SECONDS);
        }
        return isLimitExceeded(count, MAX_DEMO_START_PER_DAY_GLOBAL);
    }

    public int getDailyDispatchLimit(String username) {
        Object count = redisTemplate.opsForValue().get(username);
        return count == null ? MAX_DISPATCH_LIMIT
                : ((int) count) >= MAX_DISPATCH_LIMIT ? 0 : (MAX_DISPATCH_LIMIT - (int) count);
    }
}
