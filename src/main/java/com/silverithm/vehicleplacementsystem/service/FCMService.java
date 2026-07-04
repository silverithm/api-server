package com.silverithm.vehicleplacementsystem.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.repository.MemberRepository;
import com.silverithm.vehicleplacementsystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class FCMService {

    private final MemberRepository memberRepository;
    private final UserRepository userRepository;

    public String sendNotification(String token, String title, String body) {
        return sendNotification(token, title, body, null);
    }

    public String sendNotification(String token, String title, String body, Map<String, String> data) {
        log.info("[FCM Service] 알림 전송 요청: token={}, title={}", maskToken(token), title);

        if (token == null || token.trim().isEmpty()) {
            throw new RuntimeException("FCM 토큰이 비어 있습니다");
        }

        // Firebase 미초기화(키 미설정) 시 실제 전송 없이 개발 모드로 동작
        if (FirebaseApp.getApps().isEmpty()) {
            log.warn("[FCM Service] Firebase 미설정 - 개발 모드로 동작");
            return "dev-mode-" + System.currentTimeMillis();
        }

        try {
            Message.Builder builder = Message.builder()
                    .setToken(token)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build());
            if (data != null && !data.isEmpty()) {
                builder.putAllData(data);
            }

            String response = FirebaseMessaging.getInstance().send(builder.build());
            log.info("[FCM Service] Firebase 알림 전송 성공: messageId={}", response);
            return response;

        } catch (FirebaseMessagingException e) {
            MessagingErrorCode errorCode = e.getMessagingErrorCode();
            log.error("[FCM Service] Firebase 알림 전송 실패: token={}, errorCode={}, message={}",
                    maskToken(token), errorCode, e.getMessage());

            // 앱 삭제/토큰 만료 등으로 무효화된 토큰은 DB에서 제거해 재발송 낭비를 막는다
            if (errorCode == MessagingErrorCode.UNREGISTERED
                    || errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
                removeDeadToken(token);
            }
            throw new RuntimeException("FCM 전송 실패(" + errorCode + "): " + e.getMessage(), e);
        }
    }

    /**
     * 무효 토큰을 보유한 Member/AppUser에서 토큰을 제거한다.
     * 알림 발송의 부수 작업이므로 실패해도 예외를 전파하지 않는다.
     */
    private void removeDeadToken(String token) {
        try {
            List<Member> members = memberRepository.findByFcmToken(token);
            for (Member member : members) {
                member.setFcmToken(null);
                memberRepository.save(member);
                log.info("[FCM Service] 무효 토큰 제거: memberId={}", member.getId());
            }

            List<AppUser> users = userRepository.findByFcmToken(token);
            for (AppUser user : users) {
                user.updateFcmToken(null);
                userRepository.save(user);
                log.info("[FCM Service] 무효 토큰 제거: appUserId={}", user.getId());
            }
        } catch (Exception e) {
            log.error("[FCM Service] 무효 토큰 정리 실패: {}", e.getMessage());
        }
    }

    public boolean validateToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }
        return token.length() > 100 && token.matches("^[a-zA-Z0-9_-]+:.*");
    }

    private String maskToken(String token) {
        if (token == null || token.length() < 10) {
            return "invalid-token";
        }
        return token.substring(0, 8) + "..." + token.substring(token.length() - 8);
    }
}
