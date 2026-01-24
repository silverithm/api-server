package com.silverithm.vehicleplacementsystem.config;

import jakarta.servlet.MultipartConfigElement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;

import jakarta.annotation.PostConstruct;

/**
 * Multipart 파일 업로드 설정
 * YAML 설정이 제대로 적용되지 않는 경우를 대비한 명시적 설정
 */
@Configuration
@Slf4j
public class MultipartConfig {

    @PostConstruct
    public void init() {
        log.info("[MultipartConfig] Multipart 설정 초기화: maxFileSize=50MB, maxRequestSize=50MB");
    }

    @Bean
    public MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();

        // 단일 파일 최대 크기: 50MB
        factory.setMaxFileSize(DataSize.ofMegabytes(50));

        // 전체 요청 최대 크기: 50MB
        factory.setMaxRequestSize(DataSize.ofMegabytes(50));

        // 파일 임계값 설정 (이 크기 이상이면 디스크에 저장)
        factory.setFileSizeThreshold(DataSize.ofMegabytes(2));

        log.info("[MultipartConfig] MultipartConfigElement Bean 생성됨");
        return factory.createMultipartConfig();
    }

    @Bean
    public MultipartResolver multipartResolver() {
        log.info("[MultipartConfig] StandardServletMultipartResolver Bean 생성됨");
        return new StandardServletMultipartResolver();
    }
}
