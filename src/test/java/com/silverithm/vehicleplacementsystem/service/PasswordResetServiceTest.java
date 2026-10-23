package com.silverithm.vehicleplacementsystem.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.silverithm.vehicleplacementsystem.config.redis.RedisUtils;
import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.repository.MemberRepository;
import com.silverithm.vehicleplacementsystem.repository.UserRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/** 비밀번호 찾기 — 요청만으로는 비밀번호가 바뀌지 않고, 링크는 한 번만 쓸 수 있다 */
class PasswordResetServiceTest {

    private final Map<String, String> redis = new HashMap<>();
    private final Set<String> cooldowns = new HashSet<>();
    private final RedisUtils redisUtils = mock(RedisUtils.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final EmailService email = mock(EmailService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final MemberRepository members = mock(MemberRepository.class);
    private final AppUser admin = mock(AppUser.class);
    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.doAnswer(i -> redis.put(i.getArgument(0), i.getArgument(1)))
                .when(redisUtils).set(anyString(), anyString(), anyInt());
        when(redisUtils.get(anyString())).thenAnswer(i -> redis.get(i.<String>getArgument(0)));
        when(redisUtils.hasKey(anyString())).thenAnswer(i -> redis.containsKey(i.<String>getArgument(0)));
        when(redisUtils.delete(anyString())).thenAnswer(i -> redis.remove(i.<String>getArgument(0)) != null);
        when(redisUtils.tryAcquireOnce(anyString(), anyInt())).thenAnswer(i -> cooldowns.add(i.getArgument(0)));
        when(encoder.encode(anyString())).thenAnswer(i -> "ENC(" + i.getArgument(0) + ")");
        service = new PasswordResetService(redisUtils, encoder, email, users, members);
        ReflectionTestUtils.setField(service, "webBaseUrl", "https://www.carev.kr");
        when(users.findActiveByEmail("boss@example.com")).thenReturn(Optional.of(admin));
        when(users.findActiveByEmail("nobody@example.com")).thenReturn(Optional.empty());
    }

    private String sentToken() {
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(email).sendPasswordResetEmail(eq("boss@example.com"), link.capture(), anyInt());
        assertTrue(link.getValue().startsWith("https://www.carev.kr/reset-password?token="));
        return link.getValue().substring(link.getValue().indexOf("token=") + 6);
    }

    // 요청만으로는 비밀번호도 로그인 토큰도 건드리지 않는다 — 이게 새벽 3시 사고의 원인이었다
    @Test
    void requestAloneChangesNothing() {
        String message = service.requestForAdmin("boss@example.com").password();

        assertEquals(PasswordResetService.SENT_MESSAGE, message);
        verify(admin, never()).updatePassword(anyString());
        verify(admin, never()).updateRefreshToken(any());
        assertEquals(1, redis.size());
        sentToken();
    }

    // 가입 없는 이메일도 같은 말을 한다 — 가입 여부를 알아내는 데 못 쓰게
    @Test
    void unknownEmailGetsSameAnswerAndNoMail() {
        String message = service.requestForAdmin("nobody@example.com").password();

        assertEquals(PasswordResetService.SENT_MESSAGE, message);
        verify(email, never()).sendPasswordResetEmail(anyString(), anyString(), anyInt());
        assertTrue(redis.isEmpty());
    }

    // 레디스에는 토큰 원문이 아니라 해시만 있다
    @Test
    void tokenIsStoredOnlyAsHash() {
        service.requestForAdmin("boss@example.com");
        String token = sentToken();

        assertFalse(redis.containsKey(token));
        assertTrue(redis.keySet().stream().allMatch(k -> k.startsWith("pwreset:token:") && !k.contains(token)));
    }

    // 같은 이메일로 연달아 눌러도 메일은 한 통
    @Test
    void secondRequestInsideCooldownSendsNoSecondMail() {
        service.requestForAdmin("boss@example.com");
        service.requestForAdmin("boss@example.com");

        verify(email, org.mockito.Mockito.times(1)).sendPasswordResetEmail(anyString(), anyString(), anyInt());
    }

    @Test
    void confirmChangesPasswordAndTokenWorksOnce() {
        service.requestForAdmin("boss@example.com");
        String token = sentToken();
        assertTrue(service.isTokenValid(token));

        service.confirm(token, "new-password-1");

        verify(admin).updatePassword("ENC(new-password-1)");
        verify(admin).updateRefreshToken(null);
        assertFalse(service.isTokenValid(token));
        CustomException second = assertThrows(CustomException.class, () -> service.confirm(token, "new-password-2"));
        assertTrue(second.getMessage().contains("만료"));
        verify(admin, org.mockito.Mockito.times(1)).updatePassword(anyString());
    }

    @Test
    void unknownOrExpiredTokenIsRejected() {
        assertThrows(CustomException.class, () -> service.confirm("not-a-token", "new-password-1"));
        assertThrows(CustomException.class, () -> service.confirm(null, "new-password-1"));
        assertFalse(service.isTokenValid("not-a-token"));
        verify(admin, never()).updatePassword(anyString());
    }

    // 약한 비밀번호는 토큰을 태우지 않고 거절 — 다시 입력할 수 있다
    @Test
    void shortPasswordIsRejectedWithoutBurningToken() {
        service.requestForAdmin("boss@example.com");
        String token = sentToken();

        assertThrows(CustomException.class, () -> service.confirm(token, "short"));

        assertTrue(service.isTokenValid(token));
        verify(admin, never()).updatePassword(anyString());
    }
}
