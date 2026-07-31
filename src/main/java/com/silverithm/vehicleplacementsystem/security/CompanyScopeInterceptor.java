package com.silverithm.vehicleplacementsystem.security;

import com.silverithm.vehicleplacementsystem.service.CallerCompanyResolver;
import com.silverithm.vehicleplacementsystem.util.PrivacyMask;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * 요청에 실린 {@code companyId}가 요청자의 소속 기관과 일치하는지 검증한다.
 *
 * <p>대부분의 API가 조회 범위를 쿼리 파라미터/경로 변수의 {@code companyId}로만 결정하고
 * 요청자와 대조하지 않아, 로그인한 사용자가 값만 바꾸면 타 기관의 공지·휴가·전자결재·채팅·
 * 근무일정·출퇴근 기록을 그대로 열람할 수 있었다(IDOR). 컨트롤러마다 검증을 흩뿌리는 대신
 * 진입 지점 한 곳에서 차단한다.
 *
 * <p>인증되지 않은 요청(permitAll 엔드포인트: 로그인, 가입 요청, 기관 목록 등)은 검증 대상이 아니다.
 * 이들은 애초에 요청자의 소속 기관이라는 개념이 없다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CompanyScopeInterceptor implements HandlerInterceptor {

    private static final String COMPANY_ID = "companyId";

    private final CallerCompanyResolver callerCompanyResolver;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;   // CORS preflight
        }

        Long requestedCompanyId = extractCompanyId(request);
        if (requestedCompanyId == null) {
            return true;
        }

        String username = authenticatedUsername();
        if (username == null) {
            // 인증이 필요 없는 엔드포인트 — 소속 기관 개념이 없으므로 통과시킨다.
            return true;
        }

        Optional<Long> callerCompanyId = callerCompanyResolver.resolveCompanyId(username);

        if (callerCompanyId.isPresent() && callerCompanyId.get().equals(requestedCompanyId)) {
            return true;
        }

        log.warn("[CompanyScope] 타 기관 접근 차단: user={}, 요청 companyId={}, 소속 companyId={}, uri={}",
                PrivacyMask.email(username), requestedCompanyId, callerCompanyId.orElse(null),
                request.getRequestURI());

        writeForbidden(response);
        return false;
    }

    /** 쿼리/폼 파라미터를 먼저 보고, 없으면 경로 변수(@PathVariable)를 본다. */
    private Long extractCompanyId(HttpServletRequest request) {
        Long fromParameter = parseId(request.getParameter(COMPANY_ID));
        if (fromParameter != null) {
            return fromParameter;
        }

        Object attribute = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (attribute instanceof Map<?, ?> pathVariables) {
            Object value = pathVariables.get(COMPANY_ID);
            return value != null ? parseId(value.toString()) : null;
        }

        return null;
    }

    private Long parseId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            // 숫자가 아니면 컨트롤러의 바인딩 단계에서 400으로 처리된다.
            return null;
        }
    }

    private String authenticatedUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        String name = authentication.getName();
        if (name == null || name.isBlank() || "anonymousUser".equals(name)) {
            return null;
        }
        return name;
    }

    private void writeForbidden(HttpServletResponse response) throws java.io.IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"error\":\"해당 기관의 정보에 접근할 권한이 없습니다\"}");
    }
}
