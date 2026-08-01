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

    // 체험하기 어뷰즈 방지: 기관 사무실은 공용 IP를 쓰므로 IP당은 봇 폭주만 막을 만큼 넉넉하게,
    // 실질적인 상한은 전역 하루 한도로 건다.
    private static final int MAX_DEMO_START_PER_IP_PER_HOUR = 20;
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

    // 로그인 브루트포스 방어: 같은 IP에서 15분 창 안에 10회 실패하면 창이 끝날 때까지 차단.
    // 성공 시 카운터를 지우므로 정상 사용자는 영향을 받지 않는다.
    private static final int MAX_LOGIN_FAILURES_PER_WINDOW = 10;
    private static final int LOGIN_FAILURE_WINDOW_MINUTES = 15;

    public boolean isLoginTemporarilyBlocked(String clientIp) {
        Integer count = integerRedisTemplate.opsForValue().get("login:fail:" + clientIp);
        return count != null && count >= MAX_LOGIN_FAILURES_PER_WINDOW;
    }

    public void recordLoginFailure(String clientIp) {
        String key = "login:fail:" + clientIp;
        Long count = incrementRequestCount(key);
        if (isFirstRequest(count)) {
            integerRedisTemplate.expire(key, LOGIN_FAILURE_WINDOW_MINUTES, TimeUnit.MINUTES);
        }
        if (count != null && count >= MAX_LOGIN_FAILURES_PER_WINDOW) {
            log.warn("[Login] 로그인 실패 한도 도달 — IP 일시 차단: {} ({}회)", clientIp, count);
        }
    }

    public void clearLoginFailures(String clientIp) {
        integerRedisTemplate.delete("login:fail:" + clientIp);
    }

    /**
     * 스케줄 배치용 분산 락 (블루그린 배포로 두 인스턴스가 동시에 떠 있어도 한쪽만 실행).
     * Redis SET NX가 원자적이므로 동시에 호출해도 정확히 한 인스턴스만 true를 받는다.
     * 날짜가 키에 포함되므로 같은 날 재획득이 불가능하고, TTL로 키가 자동 정리된다.
     */
    public boolean tryAcquireDailySchedulerLock(String jobName, int ttlMinutes) {
        String key = "scheduler:lock:" + jobName + ":" + java.time.LocalDate.now();
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(key, "locked", ttlMinutes, TimeUnit.MINUTES);
        return Boolean.TRUE.equals(acquired);
    }

    public int getDailyDispatchLimit(String username) {
        Object count = redisTemplate.opsForValue().get(username);
        return count == null ? MAX_DISPATCH_LIMIT
                : ((int) count) >= MAX_DISPATCH_LIMIT ? 0 : (MAX_DISPATCH_LIMIT - (int) count);
    }
}
