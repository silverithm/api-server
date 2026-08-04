package com.silverithm.vehicleplacementsystem.security;

import com.silverithm.vehicleplacementsystem.service.CallerCompanyResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 체험(데모) 계정의 광장 쓰기 차단.
 *
 * <p>광장은 전 기관이 공유하는 cross-company 게시판이라, 데모 방문자가 글·댓글·좋아요·자료를
 * 남기면 실제 사용자들에게 그대로 노출된다. 읽기(GET)는 허용하고 쓰기만 403으로 막는다.
 * 컨트롤러의 개별 catch 블록(401/500 변환)을 타지 않도록 인터셉터에서 선차단한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoPlazaWriteGuardInterceptor implements HandlerInterceptor {

    private final CallerCompanyResolver callerCompanyResolver;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        if (HttpMethod.GET.matches(request.getMethod()) || HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        String username = authenticatedUsername();
        if (username == null) {
            return true;   // 비인증 쓰기는 시큐리티 설정이 이미 거른다
        }

        if (callerCompanyResolver.isDemoCaller(username)) {
            log.info("[Demo] 데모 계정 광장 쓰기 차단: uri={}", request.getRequestURI());
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"error\":\"체험 모드에서는 커뮤니티에 참여할 수 없습니다. 정식 가입 후 이용해주세요.\"}");
            return false;
        }

        return true;
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
}
