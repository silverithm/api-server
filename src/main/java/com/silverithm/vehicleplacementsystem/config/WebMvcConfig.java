package com.silverithm.vehicleplacementsystem.config;

import com.silverithm.vehicleplacementsystem.security.CompanyScopeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final CompanyScopeInterceptor companyScopeInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 모든 API 요청에서 companyId가 요청자의 소속 기관과 일치하는지 확인한다.
        registry.addInterceptor(companyScopeInterceptor)
                .addPathPatterns("/api/**");
    }
}
