package com.silverithm.vehicleplacementsystem.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.silverithm.vehicleplacementsystem.jwt.JwtTokenProvider;
import io.jsonwebtoken.ExpiredJwtException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

/**
 * 소켓 CONNECT의 토큰 검사. **틀린 토큰은 거절하고, 기한만 지난 토큰은 '나'를 채워 받아준다.**
 *
 * 전에는 만료된 토큰을 거절하지 않고 '나'만 비운 채 받아줬다. 그러면 요청에 적힌 senderId를
 * 그대로 믿게 되어 남의 이름으로 메시지를 보낼 수 있었다. 거절 문구의 "401"은 이미 배포된 앱이
 * 토큰을 갱신하는 신호라 빠지면 안 된다.
 */
class StompAuthChannelInterceptorTest {

    private JwtTokenProvider jwt;
    private StompAuthChannelInterceptor interceptor;
    private final MessageChannel channel = mock(MessageChannel.class);

    @BeforeEach
    void setUp() {
        jwt = mock(JwtTokenProvider.class);
        interceptor = new StompAuthChannelInterceptor(jwt);
    }

    private static StompHeaderAccessor connect(String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("s1");
        accessor.setLeaveMutable(true);
        if (authorization != null) {
            accessor.setNativeHeader("Authorization", authorization);
        }
        return accessor;
    }

    private Message<byte[]> send(StompHeaderAccessor accessor) {
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        return (Message<byte[]>) interceptor.preSend(message, channel);
    }

    @Test
    @DisplayName("멀쩡한 토큰이면 '나'가 세션에 붙는다")
    void validTokenAttachesPrincipal() {
        when(jwt.validateToken("good")).thenReturn(true);
        when(jwt.getAuthentication("good"))
                .thenReturn(new UsernamePasswordAuthenticationToken("kim@example.com", null, List.of()));

        StompHeaderAccessor accessor = connect("Bearer good");
        send(accessor);

        assertThat(accessor.getUser()).isNotNull();
        assertThat(accessor.getUser().getName()).isEqualTo("kim@example.com");
    }

    @Test
    @DisplayName("기한만 지난 토큰은 '나'를 채운 채 받아준다 — 거절하면 이미 배포된 웹이 새로고침 전까지 멈춘다")
    void expiredButGenuineTokenIsAcceptedWithIdentity() {
        when(jwt.validateToken("old")).thenThrow(new ExpiredJwtException(null, null, "JWT expired"));
        when(jwt.getAuthentication("old"))
                .thenReturn(new UsernamePasswordAuthenticationToken("kim@example.com", null, List.of()));

        StompHeaderAccessor accessor = connect("Bearer old");
        send(accessor);

        // 사칭 구멍을 막는 조건은 '거절'이 아니라 '나'가 비지 않는 것이다
        assertThat(accessor.getUser()).isNotNull();
        assertThat(accessor.getUser().getName()).isEqualTo("kim@example.com");
    }

    @Test
    @DisplayName("서명이 틀린 토큰도 거절한다 — 전에는 '나'만 비운 채 받아줬다")
    void invalidTokenIsRejected() {
        when(jwt.validateToken("forged")).thenReturn(false);

        assertThatThrownBy(() -> send(connect("Bearer forged")))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("401 Unauthorized");
    }

    @Test
    @DisplayName("토큰 검사 중 무슨 예외가 나든 받아주지 않는다")
    void unexpectedFailureIsRejected() {
        when(jwt.validateToken("weird")).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> send(connect("Bearer weird")))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("401 Unauthorized");
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 거절한다")
    void missingHeaderIsRejected() {
        assertThatThrownBy(() -> send(connect(null)))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("401 Unauthorized");
    }

    @Test
    @DisplayName("CONNECT가 아닌 프레임은 건드리지 않는다")
    void otherFramesPassThrough() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
    }
}
