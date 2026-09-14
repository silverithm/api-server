package com.silverithm.vehicleplacementsystem.config;

import com.silverithm.vehicleplacementsystem.jwt.JwtTokenProvider;
import com.silverithm.vehicleplacementsystem.util.PrivacyMask;
import io.jsonwebtoken.ExpiredJwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;

/**
 * STOMP CONNECT의 토큰을 검사해 '나'를 세션에 붙인다. **토큰이 없거나, 만료됐거나, 틀리면 거절한다.**
 *
 * <p>전에는 만료된 토큰을 거절하지 않고 Principal만 비운 채 연결을 받아줬다. 그러면 메시지 전송
 * 때 '나'를 정할 근거가 없어 요청에 적힌 senderId를 그대로 믿었고 — 만료된 토큰만 있으면
 * 남의 이름으로 메시지를 보낼 수 있었다. 거절해야 클라이언트가 토큰을 새로 받아 다시 붙는다.
 *
 * <p>거절 문구에 {@code 401 Unauthorized}를 반드시 넣는다. 이미 배포된 앱은 이 글자를 보고
 * "토큰부터 갱신하고 다시 붙자"고 판단한다(앱 socket_reconnect.dart의 looksLikeAuthFailure).
 * 문구를 바꾸면 구버전 앱이 만료된 토큰으로 영원히 두드린다.
 */
@RequiredArgsConstructor
@Slf4j
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    static final String EXPIRED = "401 Unauthorized: 토큰이 만료되었습니다";
    static final String INVALID = "401 Unauthorized: 토큰이 유효하지 않습니다";
    static final String MISSING = "401 Unauthorized: 인증이 필요합니다";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            log.info("[WebSocket] 새 연결 시도: sessionId={}", accessor.getSessionId());
            accessor.setUser(authenticate(accessor));
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            log.debug("[WebSocket] 구독: destination={}, sessionId={}", accessor.getDestination(), accessor.getSessionId());
        } else if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
            log.info("[WebSocket] 연결 해제: sessionId={}", accessor.getSessionId());
        }
        return message;
    }

    private Authentication authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            log.warn("[WebSocket] Authorization 헤더 없음 — 연결 거부: sessionId={}", accessor.getSessionId());
            throw new MessageDeliveryException(MISSING);
        }

        String jwt = header.substring(7);
        try {
            if (!jwtTokenProvider.validateToken(jwt)) {
                log.warn("[WebSocket] 토큰 검증 실패 — 연결 거부: sessionId={}", accessor.getSessionId());
                throw new MessageDeliveryException(INVALID);
            }
            Authentication auth = jwtTokenProvider.getAuthentication(jwt);
            log.info("[WebSocket] 인증 성공: user={}", PrivacyMask.email(auth.getName()));
            return auth;
        } catch (ExpiredJwtException e) {
            log.warn("[WebSocket] 토큰 만료 — 연결 거부: sessionId={}", accessor.getSessionId());
            throw new MessageDeliveryException(EXPIRED);
        } catch (MessageDeliveryException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[WebSocket] 토큰 검증 실패 — 연결 거부: {}", e.getMessage());
            throw new MessageDeliveryException(INVALID);
        }
    }
}
