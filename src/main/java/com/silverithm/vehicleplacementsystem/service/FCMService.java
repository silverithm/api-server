package com.silverithm.vehicleplacementsystem.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class FCMService {
    
    public String sendNotification(String token, String title, String body) {
        return sendNotification(token, title, body, null);
    }
    
    public String sendNotification(String token, String title, String body, Map<String, String> data) {
        try {
            log.info("[FCM Service] 알림 전송 요청: token={}, title={}", maskToken(token), title);
            
            // 개발 모드에서는 실제 전송 없이 로그만 출력
            if (isFirebaseAvailable()) {
                return sendFirebaseNotification(token, title, body, data);
            } else {
                log.warn("[FCM Service] Firebase 미설정 - 개발 모드로 동작");
                return "dev-mode-" + System.currentTimeMillis();
            }
            
        } catch (Exception e) {
            log.error("[FCM Service] 알림 전송 중 오류: token={}, title={}", maskToken(token), title, e);
            throw new RuntimeException("알림 전송 중 오류 발생: " + e.getMessage(), e);
        }
    }
    
    private boolean isFirebaseAvailable() {
        try {
            Class.forName("com.google.firebase.FirebaseApp");
            // Firebase 클래스가 존재하면 추가 검증
            return checkFirebaseInitialization();
        } catch (ClassNotFoundException e) {
            log.debug("[FCM Service] Firebase 클래스를 찾을 수 없음 - 개발 모드");
            return false;
        }
    }
    
    private boolean checkFirebaseInitialization() {
        try {
            // 리플렉션을 사용하여 Firebase 초기화 상태 확인
            Class<?> firebaseAppClass = Class.forName("com.google.firebase.FirebaseApp");
            Object apps = firebaseAppClass.getMethod("getApps").invoke(null);
            
            if (apps instanceof java.util.List) {
                return !((java.util.List<?>) apps).isEmpty();
            }
            return false;
        } catch (Exception e) {
            log.debug("[FCM Service] Firebase 초기화 확인 실패: {}", e.getMessage());
            return false;
        }
    }
    
    private String sendFirebaseNotification(String token, String title, String body, Map<String, String> data) {
        try {
            // 리플렉션을 사용하여 Firebase 메시지 전송
            Class<?> messageClass = Class.forName("com.google.firebase.messaging.Message");
            Class<?> notificationClass = Class.forName("com.google.firebase.messaging.Notification");
            Class<?> messagingClass = Class.forName("com.google.firebase.messaging.FirebaseMessaging");
            
            // Notification 빌더 생성
            Object notificationBuilder = notificationClass.getMethod("builder").invoke(null);
            Object notification = notificationBuilder.getClass()
                    .getMethod("setTitle", String.class).invoke(notificationBuilder, title);
            notification = notificationBuilder.getClass()
                    .getMethod("setBody", String.class).invoke(notificationBuilder, body);
            notification = notificationBuilder.getClass()
                    .getMethod("build").invoke(notificationBuilder);
            
            // Message 빌더 생성
            Object messageBuilder = messageClass.getMethod("builder").invoke(null);
            Object message = messageBuilder.getClass()
                    .getMethod("setToken", String.class).invoke(messageBuilder, token);
            message = messageBuilder.getClass()
                    .getMethod("setNotification", notificationClass).invoke(messageBuilder, notification);
            
            // 데이터 추가
            if (data != null && !data.isEmpty()) {
                for (Map.Entry<String, String> entry : data.entrySet()) {
                    messageBuilder.getClass()
                            .getMethod("putData", String.class, String.class)
                            .invoke(messageBuilder, entry.getKey(), entry.getValue());
                }
            }
            
            message = messageBuilder.getClass().getMethod("build").invoke(messageBuilder);
            
            // FirebaseMessaging 인스턴스 가져오기 및 전송
            Object firebaseMessaging = messagingClass.getMethod("getInstance").invoke(null);
            String response = (String) firebaseMessaging.getClass()
                    .getMethod("send", messageClass).invoke(firebaseMessaging, message);
            
            log.info("[FCM Service] Firebase 알림 전송 성공: messageId={}", response);
            return response;
            
        } catch (Exception e) {
            log.error("[FCM Service] Firebase 알림 전송 실패: {}", e.getMessage(), e);
            throw new RuntimeException("Firebase 알림 전송 실패: " + e.getMessage(), e);
        }
    }
    
    public boolean validateToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }
        
        // 기본적인 FCM 토큰 형식 검증
        return token.length() > 100 && token.matches("^[a-zA-Z0-9_-]+:.*");
    }
    
    private String maskToken(String token) {
        if (token == null || token.length() < 10) {
            return "invalid-token";
        }
        return token.substring(0, 8) + "..." + token.substring(token.length() - 8);
    }
} 