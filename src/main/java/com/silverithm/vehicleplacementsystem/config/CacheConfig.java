package com.silverithm.vehicleplacementsystem.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager caffeineCacheManager() {
        // Caffeine 캐시 설정
        CaffeineCache fitnessCache = new CaffeineCache("fitnessCache",
                Caffeine.newBuilder()
                        .maximumSize(10000)      // 최대 캐시 항목 수
                        .expireAfterWrite(5, TimeUnit.MINUTES)  // 캐시 만료 시간
                        .recordStats()           // 캐시 통계 기록
                        .build());

        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(Collections.singletonList(fitnessCache));
        return cacheManager;
    }
}