package com.silverithm.vehicleplacementsystem.config;

import com.silverithm.vehicleplacementsystem.security.AuditLogInterceptor;
import com.silverithm.vehicleplacementsystem.security.CompanyScopeInterceptor;
import com.silverithm.vehicleplacementsystem.security.DemoPlazaWriteGuardInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final CompanyScopeInterceptor companyScopeInterceptor;
    private final DemoPlazaWriteGuardInterceptor demoPlazaWriteGuardInterceptor;
    private final AuditLogInterceptor auditLogInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 모든 API 요청에서 companyId가 요청자의 소속 기관과 일치하는지 확인한다.
        registry.addInterceptor(companyScopeInterceptor)
                .addPathPatterns("/api/**");
        // 광장은 전 기관 공유 게시판 — 체험(데모) 계정의 쓰기를 차단한다.
        registry.addInterceptor(demoPlazaWriteGuardInterceptor)
                .addPathPatterns("/api/v1/plaza/**");
        // 인증된 쓰기 요청의 감사 로그 (누가·언제·무엇을)
        registry.addInterceptor(auditLogInterceptor)
                .addPathPatterns("/api/**");
    }
}
