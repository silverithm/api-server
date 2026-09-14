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
 * STOMP CONNECT의 토큰을 검사해 '나'를 세션에 붙인다. **토큰이 없거나 틀리면 거절한다.**
 * 기한만 지난 토큰은 '나'를 채운 채 받아준다(아래 ExpiredJwtException 처리의 이유 참고).
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
            // 기한만 지난 토큰은 '나'를 살린 채 받아준다 — 서명이 맞으니 누가 보냈는지는 확실하다.
            //
            // 2026-09-14 배포에서 이걸 거절하자 이미 배포된 웹이 그대로 멈췄다: 웹은 소켓에 붙을 때
            // 읽은 토큰을 다시 붙을 때도 그대로 쓰고(REST로 갱신한 새 토큰을 소켓엔 안 준다),
            // 5초마다 401만 받으며 새로고침 전까지 남의 메시지를 못 받았다(사무실 PC 한 대가
            // 40분간 2,156번). 거절의 목적은 '나'가 비어 senderId를 그대로 믿는 사칭 구멍을 막는
            // 것이었고, 그건 여기서 '나'를 채우면 달성된다. 만료된 토큰이 갱신 없이 무한정
            // 통하는 것은 아니다 — REST는 여전히 거절하므로 앱·웹은 곧 토큰을 새로 받는다.
            Authentication auth = jwtTokenProvider.getAuthentication(jwt);
            log.warn("[WebSocket] 만료된 토큰이지만 서명이 맞아 연결 허용: user={}, sessionId={}",
                    PrivacyMask.email(auth.getName()), accessor.getSessionId());
            return auth;
        } catch (MessageDeliveryException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[WebSocket] 토큰 검증 실패 — 연결 거부: {}", e.getMessage());
            throw new MessageDeliveryException(INVALID);
        }
    }
}
