package com.silverithm.vehicleplacementsystem.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.jwt.CarevPrincipal;
import com.silverithm.vehicleplacementsystem.repository.MemberRepository;
import com.silverithm.vehicleplacementsystem.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 운영에서 실제로 겹쳤던 상황을 그대로 세워둔다 — 관리자 3번(app_user)과 직원 3번(members).
 */
class ChatCallerResolverTest {

    private static final String ADMIN_EMAIL = "admin@carev.kr";
    private static final String MEMBER_USERNAME = "hong";

    private ChatCallerResolver resolver;

    @BeforeEach
    void setUp() {
        AppUser admin = mock(AppUser.class);
        lenient().when(admin.getId()).thenReturn(3L);
        Member member = mock(Member.class);
        lenient().when(member.getId()).thenReturn(3L);

        UserRepository users = mock(UserRepository.class);
        lenient().when(users.findActiveByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(admin));
        lenient().when(users.findActiveByEmail("nobody@carev.kr")).thenReturn(Optional.empty());

        MemberRepository members = mock(MemberRepository.class);
        lenient().when(members.findByUsername(MEMBER_USERNAME)).thenReturn(Optional.of(member));
        lenient().when(members.findByUsername(ADMIN_EMAIL)).thenReturn(Optional.empty());
        lenient().when(members.findByUsername("nobody@carev.kr")).thenReturn(Optional.empty());

        resolver = new ChatCallerResolver(users, members);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void login(String name, String type, Long id, String role) {
        CarevPrincipal principal = new CarevPrincipal(name, List.of(new SimpleGrantedAuthority(role)), type, id);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "", principal.getAuthorities()));
    }

    @Test
    @DisplayName("관리자는 토큰만으로 admin_ 식별자가 된다")
    void adminFromClaims() {
        login(ADMIN_EMAIL, CarevPrincipal.TYPE_ADMIN, 3L, "ROLE_ADMIN");
        assertEquals("admin_3", resolver.currentChatUserId());
    }

    @Test
    @DisplayName("옛 앱이 접두사 없이 보내도 서버가 자기 것으로 바로잡는다")
    void fixesLegacyAppValue() {
        login(ADMIN_EMAIL, CarevPrincipal.TYPE_ADMIN, 3L, "ROLE_ADMIN");
        assertEquals("admin_3", resolver.resolveSelf("3"));
    }

    @Test
    @DisplayName("남의 id를 적어 보내도 자기 것으로 바뀐다 (남의 방을 보던 구멍)")
    void ignoresSpoofedId() {
        login(ADMIN_EMAIL, CarevPrincipal.TYPE_ADMIN, 3L, "ROLE_ADMIN");
        assertEquals("admin_3", resolver.resolveSelf("9"));
    }

    @Test
    @DisplayName("직원에게는 접두사를 붙이지 않는다")
    void memberStaysRaw() {
        login(MEMBER_USERNAME, CarevPrincipal.TYPE_MEMBER, 3L, "ROLE_CAREGIVER");
        assertEquals("3", resolver.currentChatUserId());
        assertEquals("3", resolver.resolveSelf("admin_3"));
    }

    @Test
    @DisplayName("신원 클레임이 없는 옛 토큰은 이름으로 찾아 메운다")
    void legacyTokenFallsBackToDatabase() {
        login(ADMIN_EMAIL, null, null, "ROLE_ADMIN");
        assertEquals("admin_3", resolver.resolveSelf("3"));

        login(MEMBER_USERNAME, null, null, "ROLE_CAREGIVER");
        assertEquals("3", resolver.resolveSelf("3"));
    }

    @Test
    @DisplayName("호출자를 못 찾으면 보낸 값을 그대로 쓴다 — 채팅이 통째로 멈추지 않게")
    void keepsClientValueWhenUnknown() {
        login("nobody@carev.kr", null, null, "ROLE_ADMIN");
        assertEquals("77", resolver.resolveSelf("77"));
    }

    @Test
    @DisplayName("인증이 없으면 손대지 않는다")
    void anonymous() {
        assertNull(resolver.currentChatUserId());
        assertEquals("5", resolver.resolveSelf("5"));
    }
}
