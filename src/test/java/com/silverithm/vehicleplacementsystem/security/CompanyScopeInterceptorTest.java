package com.silverithm.vehicleplacementsystem.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.silverithm.vehicleplacementsystem.service.CallerCompanyResolver;
import com.silverithm.vehicleplacementsystem.service.ResourceCompanyLookup;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerMapping;

@ExtendWith(MockitoExtension.class)
@DisplayName("기관 범위 검증 인터셉터")
class CompanyScopeInterceptorTest {

    private static final String REQUESTER = "caregiver@example.com";
    private static final Long MY_COMPANY = 7L;
    private static final Long OTHER_COMPANY = 99L;

    @Mock
    private CallerCompanyResolver callerCompanyResolver;

    @Mock
    private ResourceCompanyLookup resourceCompanyLookup;

    @InjectMocks
    private CompanyScopeInterceptor interceptor;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "n/a", AuthorityUtils.NO_AUTHORITIES));
    }

    private MockHttpServletRequest get(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        return request;
    }

    @Test
    @DisplayName("타 기관 companyId 요청은 403으로 차단한다")
    void blocksOtherCompanyId() throws Exception {
        authenticateAs(REQUESTER);
        when(callerCompanyResolver.resolveCompanyId(REQUESTER)).thenReturn(Optional.of(MY_COMPANY));

        MockHttpServletRequest request = get("/api/v1/notices");
        request.setParameter("companyId", String.valueOf(OTHER_COMPANY));
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertThat(proceed).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(response.getContentAsString()).contains("권한이 없습니다");
    }

    @Test
    @DisplayName("본인 기관 companyId 요청은 통과시킨다")
    void allowsOwnCompanyId() throws Exception {
        authenticateAs(REQUESTER);
        when(callerCompanyResolver.resolveCompanyId(REQUESTER)).thenReturn(Optional.of(MY_COMPANY));

        MockHttpServletRequest request = get("/api/v1/notices");
        request.setParameter("companyId", String.valueOf(MY_COMPANY));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    @DisplayName("경로 변수의 companyId도 검증한다")
    void blocksOtherCompanyIdInPathVariable() throws Exception {
        authenticateAs(REQUESTER);
        when(callerCompanyResolver.resolveCompanyId(REQUESTER)).thenReturn(Optional.of(MY_COMPANY));

        MockHttpServletRequest request = get("/api/v1/elders/company/" + OTHER_COMPANY);
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
                Map.of("companyId", String.valueOf(OTHER_COMPANY)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    @Test
    @DisplayName("소속 기관이 없는 계정은 차단한다")
    void blocksCallerWithoutCompany() throws Exception {
        authenticateAs(REQUESTER);
        when(callerCompanyResolver.resolveCompanyId(REQUESTER)).thenReturn(Optional.empty());

        MockHttpServletRequest request = get("/api/v1/notices");
        request.setParameter("companyId", String.valueOf(MY_COMPANY));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    @Test
    @DisplayName("companyId가 없는 요청은 그대로 통과시킨다")
    void ignoresRequestWithoutCompanyId() throws Exception {
        authenticateAs(REQUESTER);

        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(get("/api/v1/users/info"), response, new Object())).isTrue();
        verify(callerCompanyResolver, never()).resolveCompanyId(anyString());
    }

    @Test
    @DisplayName("인증 없는 요청(가입·기관목록 등)은 검증 대상이 아니다")
    void ignoresUnauthenticatedRequest() throws Exception {
        MockHttpServletRequest request = get("/api/v1/members/join-request");
        request.setParameter("companyId", String.valueOf(OTHER_COMPANY));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        verify(callerCompanyResolver, never()).resolveCompanyId(anyString());
    }

    @Test
    @DisplayName("익명 인증 토큰도 미인증으로 취급한다")
    void treatsAnonymousAsUnauthenticated() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser",
                        AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        MockHttpServletRequest request = get("/api/v1/members/companies");
        request.setParameter("companyId", String.valueOf(OTHER_COMPANY));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        verify(callerCompanyResolver, never()).resolveCompanyId(anyString());
    }

    @Test
    @DisplayName("타 기관 채팅방은 roomId만으로도 차단한다")
    void blocksChatRoomOfOtherCompany() throws Exception {
        authenticateAs(REQUESTER);
        when(resourceCompanyLookup.chatRoomCompanyId(42L)).thenReturn(Optional.of(OTHER_COMPANY));
        when(callerCompanyResolver.resolveCompanyId(REQUESTER)).thenReturn(Optional.of(MY_COMPANY));

        MockHttpServletRequest request = get("/api/v1/chat/rooms/42/messages");
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("roomId", "42"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    @Test
    @DisplayName("본인 기관 채팅방은 통과시킨다")
    void allowsOwnChatRoom() throws Exception {
        authenticateAs(REQUESTER);
        when(resourceCompanyLookup.chatRoomCompanyId(42L)).thenReturn(Optional.of(MY_COMPANY));
        when(callerCompanyResolver.resolveCompanyId(REQUESTER)).thenReturn(Optional.of(MY_COMPANY));

        MockHttpServletRequest request = get("/api/v1/chat/rooms/42/messages");
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("roomId", "42"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

    @Test
    @DisplayName("존재하지 않는 채팅방은 통과시켜 컨트롤러가 404를 내게 한다")
    void passesThroughUnknownChatRoom() throws Exception {
        authenticateAs(REQUESTER);
        when(resourceCompanyLookup.chatRoomCompanyId(404L)).thenReturn(Optional.empty());

        MockHttpServletRequest request = get("/api/v1/chat/rooms/404/messages");
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("roomId", "404"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        verify(callerCompanyResolver, never()).resolveCompanyId(anyString());
    }

    @Test
    @DisplayName("CORS preflight(OPTIONS)는 통과시킨다")
    void allowsPreflight() throws Exception {
        authenticateAs(REQUESTER);

        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/notices");
        request.setParameter("companyId", String.valueOf(OTHER_COMPANY));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        verify(callerCompanyResolver, never()).resolveCompanyId(anyString());
    }
}
