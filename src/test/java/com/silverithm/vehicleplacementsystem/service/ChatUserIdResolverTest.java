package com.silverithm.vehicleplacementsystem.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChatUserIdResolverTest {

    private static final String ADMIN_EMAIL = "admin@carev.kr";

    private ChatUserIdResolver resolverWithAdmin(long adminId) {
        UserRepository repository = mock(UserRepository.class);
        AppUser admin = mock(AppUser.class);
        lenient().when(admin.getId()).thenReturn(adminId);
        lenient().when(repository.findActiveByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(admin));
        lenient().when(repository.findActiveByEmail("staff@carev.kr")).thenReturn(Optional.empty());
        return new ChatUserIdResolver(repository);
    }

    @Test
    @DisplayName("관리자가 접두사 없이 자기 id를 보내면 admin_을 붙인다")
    void prefixesAdminOwnId() {
        assertEquals("admin_3", resolverWithAdmin(3).resolve("3", ADMIN_EMAIL));
    }

    @Test
    @DisplayName("이미 붙어 있으면 그대로 둔다")
    void keepsAlreadyPrefixed() {
        assertEquals("admin_3", resolverWithAdmin(3).resolve("admin_3", ADMIN_EMAIL));
    }

    @Test
    @DisplayName("직원 토큰이면 손대지 않는다")
    void leavesMemberAlone() {
        assertEquals("3", resolverWithAdmin(3).resolve("3", "staff@carev.kr"));
    }

    @Test
    @DisplayName("관리자라도 남의 id를 보내면 손대지 않는다")
    void leavesOtherPeopleAlone() {
        assertEquals("9", resolverWithAdmin(3).resolve("9", ADMIN_EMAIL));
    }

    @Test
    @DisplayName("인증 정보가 없으면 손대지 않는다")
    void leavesAnonymousAlone() {
        ChatUserIdResolver resolver = resolverWithAdmin(3);
        assertEquals("3", resolver.resolve("3", null));
        assertEquals("3", resolver.resolve("3", ""));
        assertEquals("3", resolver.resolve("3", "anonymousUser"));
    }

    @Test
    @DisplayName("빈 값·숫자가 아닌 값은 그대로")
    void leavesNonNumericAlone() {
        ChatUserIdResolver resolver = resolverWithAdmin(3);
        assertEquals(null, resolver.resolve(null, ADMIN_EMAIL));
        assertEquals("", resolver.resolve("", ADMIN_EMAIL));
        assertEquals("kim@carev.kr", resolver.resolve("kim@carev.kr", ADMIN_EMAIL));
    }
}
