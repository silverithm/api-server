package com.silverithm.vehicleplacementsystem.lab;

import com.silverithm.vehicleplacementsystem.lab.service.LabAccessService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * /api/v1/lab/** 진입 가드 — 실험실이 열린 기관의 관리자가 아니면 403.
 *
 * <p>컨트롤러의 예외 처리를 타지 않도록 인터셉터에서 직접 JSON 본문을 쓴다. 실험실이 열리지 않은 기관에는
 * 어떤 단서도 주지 않으려고 "없는 기능"이 아니라 항상 같은 문구의 403을 돌려준다.
 * 열려 있는지 알려 주는 /api/v1/lab/access는 WebMvcConfig에서 이 가드 대상에서 뺀다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LabGuardInterceptor implements HandlerInterceptor {

    private final LabAccessService labAccessService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;   // CORS preflight
        }
        if (labAccessService.isEnabled()) {
            return true;
        }
        log.info("[Lab] 실험실 접근 차단: uri={}", request.getRequestURI());
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"message\":\"실험실은 아직 이 기관에 열려 있지 않습니다\"}");
        return false;
    }
}
