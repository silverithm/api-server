package com.silverithm.vehicleplacementsystem.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

/**
 * 거절 사유가 ERROR 프레임의 **본문에도** 실린다.
 *
 * 이미 배포된 앱은 frame.body를 보고 인증 실패인지 판단한다. 스프링 기본대로 헤더에만 넣으면
 * 앱이 "401"을 못 보고 만료된 토큰으로 계속 다시 붙는다.
 */
class StompAuthErrorHandlerTest {

    @Test
    @DisplayName("사유가 헤더와 본문 양쪽에 같은 문구로 실린다")
    void reasonIsInBothHeaderAndBody() {
        StompHeaderAccessor client = StompHeaderAccessor.create(StompCommand.CONNECT);
        client.setSessionId("s1");
        Message<byte[]> clientMessage = MessageBuilder.createMessage(new byte[0], client.getMessageHeaders());

        Message<byte[]> error = new StompAuthErrorHandler().handleClientMessageProcessingError(
                clientMessage, new MessageDeliveryException(StompAuthChannelInterceptor.EXPIRED));

        StompHeaderAccessor headers = StompHeaderAccessor.wrap(error);
        assertThat(headers.getCommand()).isEqualTo(StompCommand.ERROR);
        assertThat(headers.getMessage()).contains("401 Unauthorized");
        assertThat(new String(error.getPayload(), StandardCharsets.UTF_8)).contains("401 Unauthorized");
    }
}
