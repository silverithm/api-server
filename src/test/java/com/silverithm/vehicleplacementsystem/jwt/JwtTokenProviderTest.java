package com.silverithm.vehicleplacementsystem.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.silverithm.vehicleplacementsystem.dto.UserResponseDTO;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class JwtTokenProviderTest {

    private final JwtTokenProvider provider = new JwtTokenProvider(
            Base64.getEncoder().encodeToString("carev-test-secret-key-carev-test-secret-key-32b".getBytes()));

    private CarevPrincipal principalOf(String token) {
        return (CarevPrincipal) provider.getAuthentication(token).getPrincipal();
    }

    @Test
    @DisplayName("관리자 토큰은 유형·id를 담고 다닌다")
    void adminToken() {
        UserResponseDTO.TokenInfo token = provider.generateToken(
                "admin@carev.kr", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")),
                CarevPrincipal.TYPE_ADMIN, 3L);

        CarevPrincipal principal = principalOf(token.getAccessToken());
        assertEquals("admin@carev.kr", principal.getUsername());
        assertEquals(CarevPrincipal.TYPE_ADMIN, principal.getPrincipalType());
        assertEquals(3L, principal.getPrincipalId());
        assertTrue(principal.isAdminAccount());
        assertTrue(principal.hasIdentity());
    }

    @Test
    @DisplayName("직원 토큰은 관리자로 보이지 않는다")
    void memberToken() {
        UserResponseDTO.TokenInfo token = provider.generateToken(
                "hong", List.of(new SimpleGrantedAuthority("ROLE_CAREGIVER")),
                CarevPrincipal.TYPE_MEMBER, 12L);

        CarevPrincipal principal = principalOf(token.getAccessToken());
        assertEquals(CarevPrincipal.TYPE_MEMBER, principal.getPrincipalType());
        assertEquals(12L, principal.getPrincipalId());
        assertFalse(principal.isAdminAccount());
    }

    @Test
    @DisplayName("신원 없이 발급된 옛 토큰도 그대로 열린다 — 재로그인 전까지 쓰던 토큰이 살아 있다")
    void legacyTokenStillWorks() {
        UserResponseDTO.TokenInfo legacy = provider.generateToken(
                "old@carev.kr", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        CarevPrincipal principal = principalOf(legacy.getAccessToken());
        assertEquals("old@carev.kr", principal.getUsername());
        assertNull(principal.getPrincipalType());
        assertNull(principal.getPrincipalId());
        assertFalse(principal.hasIdentity());
        assertTrue(provider.validateToken(legacy.getAccessToken()));
    }

    @Test
    @DisplayName("리프레시로 다시 낼 때 옮길 수 있게 신원을 꺼내준다")
    void readsIdentityForRefresh() {
        UserResponseDTO.TokenInfo token = provider.generateToken(
                "admin@carev.kr", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")),
                CarevPrincipal.TYPE_ADMIN, 3L);

        assertEquals(CarevPrincipal.TYPE_ADMIN, provider.getPrincipalType(token.getRefreshToken()));
        assertEquals(3L, provider.getPrincipalId(token.getRefreshToken()));
        assertTrue(provider.isRefreshToken(token.getRefreshToken()));

        UserResponseDTO.TokenInfo legacy = provider.generateToken(
                "old@carev.kr", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        assertNull(provider.getPrincipalType(legacy.getRefreshToken()));
        assertNull(provider.getPrincipalId(legacy.getRefreshToken()));
    }
}
