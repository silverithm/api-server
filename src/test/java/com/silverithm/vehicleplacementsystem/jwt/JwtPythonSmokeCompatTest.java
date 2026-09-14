package com.silverithm.vehicleplacementsystem.jwt;

import com.silverithm.vehicleplacementsystem.config.StompAuthChannelInterceptor;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * verify/stomp_smoke.py가 표준 라이브러리(hmac/hashlib/base64)만으로 민팅한 HS256 토큰이
 * 실제 {@link JwtTokenProvider}와 같은 형식으로 검증되는지 고정한다.
 *
 * <p>토큰 문자열은 이 테스트가 실행 시점에 만드는 게 아니라, stomp_smoke.py의 {@code mint_jwt}/
 * {@code mint_forged_jwt}를 고정 키·고정 subject로 한 번 실행해 나온 실제 출력을 그대로
 * 상수로 옮겨 왔다(재현 방법은 클래스 하단 주석). 파이썬을 이 테스트 실행 경로에 끌어들이지
 * 않으면서, "파이썬이 만든 토큰 문자열이 Java에서 그대로 먹히는가"를 고정 회귀로 지킨다.
 *
 * <p>키는 32바이트(256비트, HS256 최소 요건)를 base64로 인코딩한 테스트 전용 값이며 운영
 * 키가 아니다.
 */
class JwtPythonSmokeCompatTest {

    // base64("0123456789abcdef0123456789abcdef") — 테스트 전용, 운영 키 아님
    private static final String SECRET_B64 = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final String SUBJECT = "python-compat-test@internal";

    // exp를 100년 뒤로 잡았다(ttl_sec=3153600000) — 상수를 매번 갱신하지 않아도 되도록.
    private static final String VALID_TOKEN =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9."
            + "eyJzdWIiOiJweXRob24tY29tcGF0LXRlc3RAaW50ZXJuYWwiLCJhdXRoIjoiUk9MRV9BRE1JTiIsInR5cGUiOiJhY2Nlc3MiLCJpYXQiOjE3ODkzODA3MDMsImV4cCI6NDk0Mjk4MDcwM30."
            + "FfYm4wm0wYfcjAjskYhgEuNJF42mbkh0bFzSXcrFyfc";

    private static final String EXPIRED_TOKEN =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9."
            + "eyJzdWIiOiJweXRob24tY29tcGF0LXRlc3RAaW50ZXJuYWwiLCJhdXRoIjoiUk9MRV9BRE1JTiIsInR5cGUiOiJhY2Nlc3MiLCJpYXQiOjE3ODkzODA2NzIsImV4cCI6MTc4OTM4MDYxMn0."
            + "6W-sPJgoOj3tMke553gd95D2PIkXUuNUeZKMw_XklTU";

    private static final String FORGED_TOKEN =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9."
            + "eyJzdWIiOiJweXRob24tY29tcGF0LXRlc3RAaW50ZXJuYWwiLCJhdXRoIjoiUk9MRV9BRE1JTiIsInR5cGUiOiJhY2Nlc3MiLCJpYXQiOjE3ODkzODA2NzIsImV4cCI6MTc4OTM4MjQ3Mn0."
            + "4Ko4bC77rXeempDAqmz6l1vAFy7-7-_bkSIbARmeb0c";

    private final JwtTokenProvider provider = new JwtTokenProvider(SECRET_B64);

    @Test
    @DisplayName("파이썬이 민팅한 정상 토큰을 검증·해석한다")
    void validTokenFromPythonIsAccepted() {
        assertThat(provider.validateToken(VALID_TOKEN)).isTrue();

        Authentication auth = provider.getAuthentication(VALID_TOKEN);
        assertThat(auth.getName()).isEqualTo(SUBJECT);
    }

    @Test
    @DisplayName("파이썬이 민팅한 만료 토큰은 ExpiredJwtException을 던지되 신원은 그대로 읽힌다")
    void expiredTokenFromPythonThrowsButIdentityStillReadable() {
        assertThatThrownBy(() -> provider.validateToken(EXPIRED_TOKEN))
                .isInstanceOf(ExpiredJwtException.class);

        // StompAuthChannelInterceptor가 ExpiredJwtException을 잡은 뒤 하는 것과 동일 —
        // 서명이 맞으므로 만료 토큰에서도 신원을 그대로 읽을 수 있어야 한다.
        Authentication auth = provider.getAuthentication(EXPIRED_TOKEN);
        assertThat(auth.getName()).isEqualTo(SUBJECT);
    }

    @Test
    @DisplayName("파이썬이 다른 키로 위조한 토큰은 validateToken() 자체에서 예외로 걸러진다")
    void forgedTokenFromPythonFailsSignatureCheck() {
        // validateToken()의 catch(SecurityException ...)는 java.lang.SecurityException을
        // 가리켜서(import 없음) jjwt의 io.jsonwebtoken.security.SignatureException을 잡지
        // 못한다 — 그래서 boolean을 반환하는 대신 예외가 던져진다. 이 자체는 이 스모크
        // 작업의 범위 밖이라 고치지 않았고(팀 리드 보고에 전달), 실제 소켓 경로가 이 결과를
        // 어떻게 401로 바꾸는지는 아래 forgedTokenIsRejectedAtSocketLayer에서 확인한다.
        assertThatThrownBy(() -> provider.validateToken(FORGED_TOKEN))
                .isInstanceOf(io.jsonwebtoken.security.SignatureException.class);
    }

    @Test
    @DisplayName("위조 토큰은 소켓 계층(인터셉터)에서 결국 401로 거절된다 — stomp_smoke.py가 보는 것과 동일")
    void forgedTokenIsRejectedAtSocketLayer() {
        // StompAuthChannelInterceptor.authenticate()의 catch(Exception e) 분기가
        // validateToken()이 던진 SignatureException을 받아 401로 바꾼다 — validateToken()의
        // catch 버그와 무관하게 소켓 레벨에서는 여전히 올바르게 거절된다. stomp_smoke.py의
        // "forged" 시나리오가 ERROR/401을 기대하는 근거가 바로 이것이다.
        StompAuthChannelInterceptor interceptor = new StompAuthChannelInterceptor(provider);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("s1");
        accessor.setLeaveMutable(true);
        accessor.setNativeHeader("Authorization", "Bearer " + FORGED_TOKEN);
        var message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("401 Unauthorized");
    }
}

/*
 * VALID_TOKEN/EXPIRED_TOKEN/FORGED_TOKEN 재현 방법 (stomp_smoke.py와 이 상수들이
 * 어긋나면 아래를 다시 돌려 상수를 갱신한다):
 *
 *   python3 - <<'EOF'
 *   import importlib.util
 *   spec = importlib.util.spec_from_file_location("s", "verify/stomp_smoke.py")
 *   m = importlib.util.module_from_spec(spec); spec.loader.exec_module(m)
 *   secret_b64 = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
 *   key = m._decode_secret(secret_b64)
 *   subject = "python-compat-test@internal"
 *   print(m.mint_jwt(key, subject, expired=False, ttl_sec=3153600000))  # 100년 — 상수 부패 방지
 *   print(m.mint_jwt(key, subject, expired=True))
 *   print(m.mint_forged_jwt(subject))
 *   EOF
 */
