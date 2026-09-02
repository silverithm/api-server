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

    /**
     * 채팅 푸시 알림을 보내는 스레드.
     *
     * FCM 전송은 참가자 한 명당 구글로 나가는 HTTPS 호출이라 100~700ms씩 걸린다. 27명 방이면
     * 7초다(운영 로그에서 실측). 이것을 메시지 저장과 같은 스레드에서 하면 보낸 사람은 저장이
     * 3ms에 끝났는데도 7초를 기다린다 — 사장님이 말한 "채팅이 느리다"의 정체.
     * 알림은 받는 사람에게만 필요하고 보낸 사람의 응답과는 상관이 없으므로 여기로 넘긴다.
     *
     * 큐가 밀리면 호출한 스레드가 직접 처리한다(CallerRunsPolicy) — 알림을 버리는 것보다 낫다.
     */
    @Bean
    public ChatNotificationExecutor chatNotificationExecutor() {
        ChatNotificationExecutor executor = new ChatNotificationExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("chat-push-");
        executor.initialize();
        return executor;
    }

    /** 주입할 때 다른 실행기(유전 알고리즘용)와 헷갈리지 않도록 전용 타입을 둔다. */
    public static class ChatNotificationExecutor extends ThreadPoolTaskExecutor {
    }
}