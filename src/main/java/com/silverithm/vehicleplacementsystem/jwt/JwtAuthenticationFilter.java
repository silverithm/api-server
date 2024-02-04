package com.silverithm.vehicleplacementsystem.jwt;

import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;




import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;


    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            jakarta.servlet.FilterChain chain) throws jakarta.servlet.ServletException, IOException {

        //1. Request Header 에서 JWT Token 추출
        String token = jwtTokenProvider.resolveToken((HttpServletRequest) request);
//        String token = "adsfasdfasdfasdfasdfasdfadsfasdfadsf";
        //2. validateToken 메서드로 토큰 유효성 검사
        if (token != null && jwtTokenProvider.validateToken(token)) {
            Authentication authentication = jwtTokenProvider.getAuthentication(token);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        chain.doFilter((jakarta.servlet.ServletRequest) request, (ServletResponse) response);
    }


}