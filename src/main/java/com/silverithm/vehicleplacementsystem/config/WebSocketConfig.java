package com.silverithm.vehicleplacementsystem.config;

import com.silverithm.vehicleplacementsystem.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /** 서버·클라이언트가 서로 살아 있는지 확인하는 간격. 앱·웹 클라이언트도 10초로 맞춰져 있다. */
    private static final long HEARTBEAT_MS = 10_000L;

    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 브로커 하트비트용 스케줄러. 이게 없으면 심플 브로커의 하트비트가 꺼진 채 돈다.
     *
     * 꺼져 있으면 폰이 화면을 끄거나 망을 옮겨 소켓이 조용히 죽어도 서버는 세션을 살아 있다고
     * 믿는다 — 그 세션으로 방송한 메시지는 아무 데도 가지 않는다.
     */
    @Bean
    public ThreadPoolTaskScheduler webSocketHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 클라이언트가 구독할 수 있는 토픽 prefix
        config.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{HEARTBEAT_MS, HEARTBEAT_MS})
                .setTaskScheduler(webSocketHeartbeatScheduler());

        // 클라이언트가 메시지를 보낼 때 사용할 prefix
        config.setApplicationDestinationPrefixes("/app");

        // 특정 사용자에게 메시지를 보낼 때 사용할 prefix
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 거절 사유를 본문에도 싣는다 — 이유는 StompAuthErrorHandler 참고
        registry.setErrorHandler(new StompAuthErrorHandler());

        // WebSocket 엔드포인트 설정
        registry.addEndpoint("/ws/chat")
                .setAllowedOriginPatterns("*")
                .withSockJS();

        // SockJS 없이 순수 WebSocket 연결도 허용
        registry.addEndpoint("/ws/chat")
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new StompAuthChannelInterceptor(jwtTokenProvider));
    }
}
