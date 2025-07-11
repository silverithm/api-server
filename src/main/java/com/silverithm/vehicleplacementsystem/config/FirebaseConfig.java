package com.silverithm.vehicleplacementsystem.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;

@Configuration
@Slf4j
public class FirebaseConfig {
    
    @Value("${firebase.config.path:}")
    private String firebaseConfigPath;
    
    @PostConstruct
    public void initializeFirebase() {
        try {
            // Firebase 앱이 이미 초기화되어 있는지 확인
            if (FirebaseApp.getApps().isEmpty()) {
                
                // Firebase 설정 경로가 없으면 개발 모드로 동작
                if (!StringUtils.hasText(firebaseConfigPath)) {
                    log.warn("[Firebase Config] Firebase 설정 경로가 지정되지 않았습니다");
                    log.warn("[Firebase Config] FCM 서비스가 개발 모드로 동작합니다");
                    return;
                }
                
                // 클래스패스에서 Firebase 설정 파일 로드
                ClassPathResource resource = new ClassPathResource(firebaseConfigPath);
                
                if (resource.exists()) {
                    try (InputStream serviceAccount = resource.getInputStream()) {
                        FirebaseOptions options = FirebaseOptions.builder()
                                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                                .build();
                        
                        FirebaseApp.initializeApp(options);
                        log.info("[Firebase Config] Firebase 초기화 완료");
                    }
                } else {
                    log.warn("[Firebase Config] Firebase 설정 파일을 찾을 수 없습니다: {}", firebaseConfigPath);
                    log.warn("[Firebase Config] FCM 서비스가 개발 모드로 동작합니다");
                }
            } else {
                log.info("[Firebase Config] Firebase 앱이 이미 초기화되어 있습니다");
            }
        } catch (IOException e) {
            log.error("[Firebase Config] Firebase 초기화 중 오류 발생", e);
            log.warn("[Firebase Config] FCM 서비스가 개발 모드로 동작합니다");
        } catch (Exception e) {
            log.error("[Firebase Config] Firebase 초기화 중 예상치 못한 오류 발생", e);
            log.warn("[Firebase Config] FCM 서비스가 개발 모드로 동작합니다");
        }
    }
}