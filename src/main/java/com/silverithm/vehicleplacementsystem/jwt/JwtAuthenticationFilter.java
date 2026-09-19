package com.silverithm.vehicleplacementsystem.jwt;

import com.silverithm.vehicleplacementsystem.config.redis.RedisUtils;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;


import java.io.IOException;

@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final RedisUtils redisUtils;


    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, RedisUtils redisUtils) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.redisUtils = redisUtils;
    }


    /**
     * 인증이 필요 없는 공개 경로는 이 필터를 아예 거치지 않는다.
     *
     * 이 필터는 만료된 토큰을 보면 경로와 상관없이 즉시 401을 돌려보낸다. 그래서 앱이 만료된
     * 토큰을 헤더에 실은 채 소켓 핸드셰이크(/ws/chat)를 열면, 기한 지난 토큰을 받아주도록 만든
     * STOMP CONNECT 단계까지 가지도 못하고 막혔다 — 하루 717건, 한 기관 기기만 409건
     * (2026-09-19 지표 점검). 소켓 인증은 STOMP CONNECT 프레임에서 따로 하므로
     * (StompAuthChannelInterceptor) 핸드셰이크 HTTP 요청은 여기서 볼 필요가 없다.
     * 앱 버전 확인·토큰 재발급도 만료 토큰 때문에 막히면 안 되는 공개 경로다.
     */
    static boolean isPublicPath(String path) {
        if (path == null) return false;
        return path.startsWith("/ws/")
                || path.equals("/ws")
                || path.equals("/api/v1/app-version")
                || path.equals("/api/v1/refresh-token");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return isPublicPath(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            jakarta.servlet.FilterChain chain) throws jakarta.servlet.ServletException, IOException {

        //1. Request Header 에서 JWT Token 추출
        String token = jwtTokenProvider.resolveToken((HttpServletRequest) request);

        try {
            // 2. validateToken 메서드로 토큰 유효성 검사
            if (token != null && jwtTokenProvider.validateToken(token)) {
                if (redisUtils.getBlackList(token) == null) {
                    Authentication authentication = jwtTokenProvider.getAuthentication(token);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        } catch (ExpiredJwtException e) {

            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Token expired\"}");
            return;
        }

        chain.doFilter((jakarta.servlet.ServletRequest) request, (ServletResponse) response);
    }


}