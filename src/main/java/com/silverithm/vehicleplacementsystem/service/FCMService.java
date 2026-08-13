package com.silverithm.vehicleplacementsystem.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
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
    private final DeviceTokenService deviceTokenService;

    public String sendNotification(String token, String title, String body) {
        return sendNotification(token, title, body, null);
    }

    /**
     * 토큰 하나를 받지만 <b>그 사람의 로그인된 모든 기기</b>로 보낸다.
     *
     * <p>발송 지점이 여러 서비스에 흩어져 있어 각자 기기 목록을 챙기게 두면 새 알림을 붙일 때마다
     * 빠뜨린다. 수신 거부 확인과 마찬가지로 여기 한 곳을 지나가게 한다.
     *
     * <p>반환값은 첫 기기의 messageId다 — 호출부는 성공/실패만 보고, 기기별 결과는 로그에 남긴다.
     */
    public String sendNotification(String token, String title, String body, Map<String, String> data) {
        log.info("[FCM Service] 알림 전송 요청: token={}, title={}", maskToken(token), title);

        if (token == null || token.trim().isEmpty()) {
            throw new RuntimeException("FCM 토큰이 비어 있습니다");
        }

        // 수신 거부는 여기 한 곳에서 막는다. 발송 지점이 여러 서비스에 흩어져 있어
        // 각자 확인하게 두면 새 알림을 붙일 때마다 빠뜨린다.
        if (!isPushEnabledFor(token)) {
            log.info("[FCM Service] 수신 거부 사용자 — 전송 건너뜀: token={}", maskToken(token));
            return "push-disabled";
        }

        // Firebase 미초기화(키 미설정) 시 실제 전송 없이 개발 모드로 동작
        if (FirebaseApp.getApps().isEmpty()) {
            log.warn("[FCM Service] Firebase 미설정 - 개발 모드로 동작");
            return "dev-mode-" + System.currentTimeMillis();
        }

        List<String> targets = deviceTokenService.siblingTokensOf(token);
        if (targets.size() > 1) {
            log.info("[FCM Service] 기기 {}대로 전송", targets.size());
        }

        String firstMessageId = null;
        RuntimeException firstFailure = null;
        for (String target : targets) {
            try {
                String messageId = sendToSingleDevice(target, title, body, data);
                if (firstMessageId == null) {
                    firstMessageId = messageId;
                }
            } catch (RuntimeException e) {
                // 한 기기가 실패해도 나머지 기기에는 보낸다
                if (firstFailure == null) {
                    firstFailure = e;
                }
            }
        }

        if (firstMessageId != null) {
            return firstMessageId;
        }
        throw firstFailure != null ? firstFailure : new RuntimeException("보낼 수 있는 기기가 없습니다");
    }

    private String sendToSingleDevice(String token, String title, String body, Map<String, String> data) {
        try {
            Message.Builder builder = Message.builder()
                    .setToken(token)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    // iOS는 aps.sound가 없으면 배너만 조용히 뜨고 소리·진동이 울리지 않는다.
                    // "default"를 주면 기기 설정(무음 스위치·햅틱)에 따라 소리/진동이 나간다.
                    .setApnsConfig(ApnsConfig.builder()
                            .setAps(Aps.builder().setSound("default").build())
                            .build())
                    // Android는 앱이 만들어둔 고중요도 채널(소리·진동 켜짐)로 태워 보낸다.
                    // 채널을 지정하지 않으면 기본 채널로 가서 조용히 표시될 수 있다.
                    .setAndroidConfig(AndroidConfig.builder()
                            .setNotification(AndroidNotification.builder()
                                    .setSound("default")
                                    .setChannelId("high_importance_channel")
                                    .build())
                            .build());
            if (data != null && !data.isEmpty()) {
                builder.putAllData(data);
            }

            String response = FirebaseMessaging.getInstance().send(builder.build());
            log.info("[FCM Service] Firebase 알림 전송 성공: messageId={}", response);
            return response;

        } catch (FirebaseMessagingException e) {
            MessagingErrorCode errorCode = e.getMessagingErrorCode();

            // 앱 삭제/토큰 만료 등으로 무효화된 토큰은 DB에서 제거해 재발송 낭비를 막는다.
            // 손상됐거나 다른 프로젝트에서 발급된 토큰은 errorCode 없이 HTTP 401로만 떨어진다 —
            // 정상 토큰의 전송 성공과 공존하므로 자격증명 문제가 아니라 해당 토큰의 문제다.
            boolean deadToken = errorCode == MessagingErrorCode.UNREGISTERED
                    || errorCode == MessagingErrorCode.INVALID_ARGUMENT
                    || errorCode == MessagingErrorCode.SENDER_ID_MISMATCH
                    || (errorCode == null && e.getMessage() != null
                        && e.getMessage().contains("Unexpected HTTP response with status: 401"));

            if (deadToken) {
                // 예상 가능한 정리 대상 — 에러가 아니라 경고로 남긴다 (에러율 오염 방지)
                log.warn("[FCM Service] 무효 토큰으로 전송 실패(제거함): token={}, errorCode={}, message={}",
                        maskToken(token), errorCode, e.getMessage());
                removeDeadToken(token);
            } else {
                log.error("[FCM Service] Firebase 알림 전송 실패: token={}, errorCode={}, message={}",
                        maskToken(token), errorCode, e.getMessage());
            }
            throw new RuntimeException("FCM 전송 실패(" + errorCode + "): " + e.getMessage(), e);
        }
    }

    /**
     * 이 토큰을 가진 사용자가 알림을 받기로 해두었는지.
     *
     * 토큰 주인을 못 찾으면 보낸다 — 알림을 조용히 삼키는 것보다 낫고,
     * 주인 없는 토큰은 어차피 FCM이 거절해 정리된다.
     */
    private boolean isPushEnabledFor(String token) {
        try {
            // 기기 테이블로 주인을 먼저 찾는다 — 새로 등록된 기기는 사용자 행의 컬럼에 없을 수 있다
            var device = deviceTokenService.ownerOf(token);
            if (device.isPresent()) {
                Long memberId = device.get().getMemberId();
                Long appUserId = device.get().getAppUserId();
                if (memberId != null) {
                    return memberRepository.findById(memberId)
                            .map(m -> !Boolean.FALSE.equals(m.getPushEnabled()))
                            .orElse(true);
                }
                if (appUserId != null) {
                    return userRepository.findById(appUserId)
                            .map(u -> !Boolean.FALSE.equals(u.getPushEnabled()))
                            .orElse(true);
                }
            }

            for (Member member : memberRepository.findByFcmToken(token)) {
                if (Boolean.FALSE.equals(member.getPushEnabled())) {
                    return false;
                }
            }
            for (AppUser user : userRepository.findByFcmToken(token)) {
                if (Boolean.FALSE.equals(user.getPushEnabled())) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            log.error("[FCM Service] 수신 설정 확인 실패 — 전송은 진행: {}", e.getMessage());
            return true;
        }
    }

    /**
     * 무효 토큰을 보유한 Member/AppUser에서 토큰을 제거한다.
     * 알림 발송의 부수 작업이므로 실패해도 예외를 전파하지 않는다.
     */
    private void removeDeadToken(String token) {
        try {
            // 죽은 기기 행부터 뗀다 — 이게 실제 발송 대상이다
            deviceTokenService.remove(token);

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
