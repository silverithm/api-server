package com.silverithm.vehicleplacementsystem.config;

import java.nio.charset.StandardCharsets;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

/**
 * 연결이 거절될 때 클라이언트에 가는 ERROR 프레임에 사유를 **본문에도** 싣는다.
 *
 * <p>스프링 기본은 사유를 {@code message} 헤더에만 넣고 본문은 비운다. 그런데 이미 배포된 앱은
 * 본문(frame.body)을 보고 인증 실패인지 판단한다 — 본문이 비면 "401"을 못 보고 만료된 토큰으로
 * 계속 다시 붙는다. 헤더와 본문에 같은 문구를 넣어 어느 쪽을 보든 알 수 있게 한다.
 */
public class StompAuthErrorHandler extends StompSubProtocolErrorHandler {

    @Override
    protected Message<byte[]> handleInternal(StompHeaderAccessor errorHeaderAccessor, byte[] errorPayload,
                                             Throwable cause, StompHeaderAccessor clientHeaderAccessor) {
        String reason = errorHeaderAccessor.getMessage();
        byte[] payload = (reason != null && errorPayload.length == 0)
                ? reason.getBytes(StandardCharsets.UTF_8)
                : errorPayload;
        return MessageBuilder.createMessage(payload, errorHeaderAccessor.getMessageHeaders());
    }
}
