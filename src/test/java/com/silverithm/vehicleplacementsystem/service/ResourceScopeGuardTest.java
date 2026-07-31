package com.silverithm.vehicleplacementsystem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("단건 리소스 기관 범위 검증")
class ResourceScopeGuardTest {

    private static final String REQUESTER = "admin@example.com";
    private static final Long MY_COMPANY = 7L;
    private static final Long OTHER_COMPANY = 99L;

    @Mock
    private CallerCompanyResolver callerCompanyResolver;

    @InjectMocks
    private ResourceScopeGuard guard;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(REQUESTER, "n/a", AuthorityUtils.NO_AUTHORITIES));
    }

    private Company company(Long id) {
        Company company = Company.of("케어브이요양원", "서울시 강남구", null);
        ReflectionTestUtils.setField(company, "id", id);
        return company;
    }

    @Test
    @DisplayName("같은 기관 리소스는 통과한다")
    void allowsSameCompany() {
        authenticate();
        when(callerCompanyResolver.resolveCompanyId(REQUESTER)).thenReturn(Optional.of(MY_COMPANY));

        assertThatCode(() -> guard.requireSameCompany(company(MY_COMPANY))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("타 기관 리소스는 403으로 차단한다")
    void blocksOtherCompany() {
        authenticate();
        when(callerCompanyResolver.resolveCompanyId(REQUESTER)).thenReturn(Optional.of(MY_COMPANY));

        assertThatThrownBy(() -> guard.requireSameCompany(company(OTHER_COMPANY)))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getHttpStatus())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("소속 기관이 없는 리소스는 차단한다")
    void blocksResourceWithoutCompany() {
        authenticate();
        when(callerCompanyResolver.resolveCompanyId(REQUESTER)).thenReturn(Optional.of(MY_COMPANY));

        assertThatThrownBy(() -> guard.requireSameCompany((Company) null))
                .isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("요청자의 소속 기관을 확인할 수 없으면 차단한다")
    void blocksCallerWithoutCompany() {
        authenticate();
        when(callerCompanyResolver.resolveCompanyId(REQUESTER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.requireSameCompany(company(MY_COMPANY)))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getHttpStatus())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("인증 컨텍스트가 없으면 401로 차단한다 (열려버리지 않는다)")
    void blocksWhenUnauthenticated() {
        assertThatThrownBy(() -> guard.requireSameCompany(company(MY_COMPANY)))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getHttpStatus())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));

        verify(callerCompanyResolver, never()).resolveCompanyId(anyString());
    }

    @Test
    @DisplayName("익명 토큰도 미인증으로 취급한다")
    void blocksAnonymous() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser",
                        AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThatThrownBy(() -> guard.requireSameCompany(company(MY_COMPANY)))
                .isInstanceOf(CustomException.class);
        verify(callerCompanyResolver, never()).resolveCompanyId(anyString());
    }
}
