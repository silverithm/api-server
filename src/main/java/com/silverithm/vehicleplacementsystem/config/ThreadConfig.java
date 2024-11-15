package com.silverithm.vehicleplacementsystem.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ThreadConfig {
    @Bean(name = "geneticAlgorithmExecutor")  // Bean 이름 지정
    public ThreadPoolTaskExecutor geneticAlgorithmExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // t3.medium 기준 설정
        executor.setCorePoolSize(2);  // vCPU 수와 동일하게
        executor.setMaxPoolSize(2);   // 최대 스레드 수도 동일하게
        executor.setQueueCapacity(100); // 대기열 크기 제한

        // 대기열이 가득 찼을 때 처리 방식
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // 유휴 스레드 대기 시간
        executor.setKeepAliveSeconds(60);

        executor.setThreadNamePrefix("GA-");
        executor.initialize();
        return executor;
    }
}