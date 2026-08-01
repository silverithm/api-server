package com.silverithm.vehicleplacementsystem.security;

import com.silverithm.vehicleplacementsystem.entity.AuditLog;
import com.silverithm.vehicleplacementsystem.repository.AuditLogRepository;
import com.silverithm.vehicleplacementsystem.service.CallerCompanyResolver;
import com.silverithm.vehicleplacementsystem.util.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 감사 로그 — 인증된 쓰기 요청(POST/PUT/DELETE/PATCH)을 자동 기록한다.
 *
 * <p>요청 본문은 개인정보가 섞일 수 있어 저장하지 않고, 누가·언제·어떤 리소스에·
 * 어떤 결과(상태코드)였는지만 남긴다. 채팅 메시지 전송/읽음, FCM 토큰 갱신처럼
 * 고빈도·저가치 경로는 제외한다. 기록 실패가 본 요청을 깨뜨리지 않도록 삼킨다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogInterceptor implements HandlerInterceptor {

    private final AuditLogRepository auditLogRepository;
    private final CallerCompanyResolver callerCompanyResolver;

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception ex) {
        try {
            String method = request.getMethod();
            if (HttpMethod.GET.matches(method) || HttpMethod.OPTIONS.matches(method)
                    || HttpMethod.HEAD.matches(method)) {
                return;
            }

            String uri = request.getRequestURI();
            if (isHighVolumeLowValue(uri)) {
                return;
            }

            String username = authenticatedUsername();
            if (username == null) {
                return;   // 비인증 요청(로그인·가입·데모 시작 등)은 대상이 아니다
            }

            Long companyId = callerCompanyResolver.resolveCompanyId(username).orElse(null);

            auditLogRepository.save(AuditLog.builder()
                    .occurredAt(LocalDateTime.now())
                    .username(username)
                    .companyId(companyId)
                    .method(method)
                    .uri(uri.length() > 500 ? uri.substring(0, 500) : uri)
                    .statusCode(response.getStatus())
                    .clientIp(ClientIp.from(request))
                    .build());
        } catch (Exception e) {
            log.warn("[Audit] 감사 로그 기록 실패 (요청에는 영향 없음): {}", e.getMessage());
        }
    }

    private boolean isHighVolumeLowValue(String uri) {
        return uri.contains("/messages")
                || uri.contains("/read")
                || uri.contains("fcm-token")
                || uri.startsWith("/ws");
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
