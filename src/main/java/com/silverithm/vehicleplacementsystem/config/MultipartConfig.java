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
 *
 * <p><b>한도를 100MB로 맞춘 이유.</b> 앱은 동영상을 720p로 압축해 올리되 압축에 실패하면
 * 원본이 100MB 이내일 때 그대로 올린다(chat_room_screen.dart의 _maxVideoFileSize = 100MB).
 * 서버가 50MB에서 튕기면 선생님은 긴 영상을 한참 올린 끝에 실패를 본다. 받는 쪽을 앱에 맞춘다.
 *
 * <p><b>메모리.</b> fileSizeThreshold(2MB)를 넘는 파일은 톰캣이 힙이 아니라 디스크 임시 파일에
 * 쓴다. 그래서 100MB 동영상이 들어와도 톰캣 쪽 힙 사용은 일정하다. 애플리케이션에서도
 * 이미지가 아닌 큰 파일은 읽지 않고 임시 파일에서 곧장 S3로 흘려보낸다
 * (FileStorageService의 STREAM_THRESHOLD_BYTES).
 */
@Configuration
@Slf4j
public class MultipartConfig {

    @PostConstruct
    public void init() {
        log.info("[MultipartConfig] Multipart 설정 초기화: maxFileSize=100MB, maxRequestSize=120MB");
    }

    @Bean
    public MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();

        // 단일 파일 최대 크기: 100MB (앱의 동영상 상한과 같은 값)
        factory.setMaxFileSize(DataSize.ofMegabytes(100));

        // 전체 요청 최대 크기: 120MB
        // 멀티파트는 파일 외에 경계 문자열과 senderId 같은 폼 필드가 함께 실린다.
        // 파일과 같은 값으로 두면 100MB짜리 파일이 요청 한도에 걸려 튕긴다.
        factory.setMaxRequestSize(DataSize.ofMegabytes(120));

        // 이 크기를 넘으면 힙이 아니라 디스크 임시 파일에 쓴다. 100MB 동영상이 힙에 앉지 않게 하는 지점.
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
