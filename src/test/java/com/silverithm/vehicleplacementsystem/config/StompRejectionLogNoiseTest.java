package com.silverithm.vehicleplacementsystem.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.read.ListAppender;
import com.silverithm.vehicleplacementsystem.jwt.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.messaging.StompSubProtocolHandler;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 소켓 CONNECT 거절이 프레임워크 스택(수십 줄)을 남기지 않는지 확인한다.
 *
 * <p>{@code StompSubProtocolHandler.handleMessageFromClient}는 인터셉터가 던진
 * {@code MessageDeliveryException}을 감싸 DEBUG 레벨로 "Failed to send message to
 * MessageChannel..."을 스택(91프레임)과 함께 남긴다. 2026-09-14 배포에서 거절 2,156회가
 * 로그 1,060줄을 찍었다. logback-spring.xml에 이 로거를 INFO로 못박아 막았다 — 이 테스트는
 * 실제 커밋된 logback-spring.xml을 그대로 로드해 그 못박기가 살아있는지, 그리고 그 설정이
 * 적용된 채로 실제 거절 흐름을 태워 프레임워크발 스택 이벤트가 하나도 없는지를 검증한다.
 * 우리 쪽 WARN 한 줄(StompAuthChannelInterceptor)은 그대로 남아야 한다.
 */
class StompRejectionLogNoiseTest {

    private static final String NOISY_LOGGER = "org.springframework.web.socket.messaging.StompSubProtocolHandler";

    private LoggerContext testContext;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() throws JoranException, IOException {
        // 앱이 실제로 쓰는 logback-spring.xml을 별도 LoggerContext에 그대로 로드한다 —
        // 지금 테스트 실행 자체의 로깅 설정과 섞이지 않도록.
        testContext = new LoggerContext();
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(testContext);
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("logback-spring.xml")) {
            assertThat(in).as("logback-spring.xml on classpath").isNotNull();
            configurator.doConfigure(in);
        }

        appender = new ListAppender<>();
        appender.setContext(testContext);
        appender.start();
        testContext.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        testContext.stop();
    }

    @Test
    @DisplayName("logback-spring.xml이 소음 로거를 INFO로 못박아 뒀다")
    void noisyLoggerIsPinnedToInfo() {
        Logger logger = testContext.getLogger(NOISY_LOGGER);
        assertThat(logger.getLevel()).isEqualTo(Level.INFO);
        assertThat(logger.isDebugEnabled()).isFalse();
    }

    @Test
    @DisplayName("CONNECT 거절은 인터셉터의 WARN 한 줄만 남기고 프레임워크 스택은 남기지 않는다")
    void rejectedConnectDoesNotLeakFrameworkStack() throws Exception {
        JwtTokenProvider jwt = mock(JwtTokenProvider.class);
        when(jwt.validateToken("forged")).thenReturn(false);

        ExecutorSubscribableChannel outputChannel = new ExecutorSubscribableChannel();
        outputChannel.addInterceptor(new StompAuthChannelInterceptor(jwt));

        StompSubProtocolHandler handler = new StompSubProtocolHandler();
        handler.setErrorHandler(new StompAuthErrorHandler());

        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("s1");
        when(session.isOpen()).thenReturn(true);
        handler.afterSessionStarted(session, outputChannel);

        // STOMP 프레임은 NULL(0x00) 옥텟으로 끝나야 BufferingStompDecoder가 완결된 프레임으로 본다.
        String frame = "CONNECT\naccept-version:1.1,1.0\nAuthorization:Bearer forged\n\n\u0000";
        WebSocketMessage<?> msg = new TextMessage(frame);

        // handleMessageFromClient가 호출하는 StompSubProtocolHandler·인터셉터는 이 JVM의
        // "실제 활성" 로거 컨텍스트로 로그를 낸다(별도로 만든 testContext가 아니라). 그 활성
        // 컨텍스트에 커밋된 설정과 같은 레벨(소음 로거 INFO, 루트 WARN 이상만 통과)을 이 실행
        // 범위에서만 강제해 두고, 우리 WARN 한 줄과 프레임워크 스택 유무를 함께 확인한다.
        LoggerContext activeContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger activeNoisy = activeContext.getLogger(NOISY_LOGGER);
        Logger activeRoot = activeContext.getLogger(Logger.ROOT_LOGGER_NAME);
        Level originalNoisyLevel = activeNoisy.getLevel();
        Level originalRootLevel = activeRoot.getLevel();

        ListAppender<ILoggingEvent> liveAppender = new ListAppender<>();
        liveAppender.setContext(activeContext);
        liveAppender.start();

        activeNoisy.setLevel(Level.INFO);
        activeRoot.setLevel(Level.WARN);
        activeRoot.addAppender(liveAppender);
        try {
            handler.handleMessageFromClient(session, msg, outputChannel);
        } finally {
            activeRoot.detachAppender(liveAppender);
            activeRoot.setLevel(originalRootLevel);
            activeNoisy.setLevel(originalNoisyLevel);
        }

        List<ILoggingEvent> events = liveAppender.list;
        boolean hasFrameworkStack = events.stream()
                .anyMatch(e -> NOISY_LOGGER.equals(e.getLoggerName()) && e.getThrowableProxy() != null);
        assertThat(hasFrameworkStack)
                .as("StompSubProtocolHandler가 스택 있는 로그를 남기면 안 된다: %s", events)
                .isFalse();

        boolean hasOurWarn = events.stream()
                .anyMatch(e -> e.getLoggerName().equals(StompAuthChannelInterceptor.class.getName())
                        && e.getLevel() == Level.WARN);
        assertThat(hasOurWarn).as("인터셉터의 거절 WARN 한 줄은 그대로 남아야 한다").isTrue();
    }
}
