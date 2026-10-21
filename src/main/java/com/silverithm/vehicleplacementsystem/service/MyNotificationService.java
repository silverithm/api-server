package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.entity.Notification;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.repository.NotificationRepository;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 알림함 — 웹이 "확인할 때까지 떠 있는 알림"을 띄우는 데 쓴다(2026-10-22 원장 요청).
 *
 * <p>받는 사람은 토큰으로 정한다(채팅 식별자 — 직원 "12", 관리자 "admin_3"). 경로에 사람 id를 받는
 * 옛 알림 API와 달리 남의 알림을 읽거나 확인할 수 없다.
 * 채팅 알림은 뺀다 — 채팅은 말풍선 배지와 새 메시지 알림이 따로 있다.
 */
@Service
@RequiredArgsConstructor
public class MyNotificationService {

    static final int DEFAULT_DAYS = 3;
    static final int MAX_DAYS = 14;
    static final int LIMIT = 30;

    private final NotificationRepository notifications;
    private final ChatCallerResolver chatCallerResolver;

    public record View(Long id, String title, String message, String type, Long relatedEntityId,
                       String relatedEntityType, LocalDateTime createdAt) {
    }

    /** 확인 안 한 알림(최근 [days]일, 새것부터, 최대 30건) */
    @Transactional(readOnly = true)
    public List<View> unread(Integer days) {
        String me = requireMe();
        int window = days == null ? DEFAULT_DAYS : Math.max(1, Math.min(MAX_DAYS, days));
        LocalDateTime since = LocalDateTime.now().minusDays(window);
        return notifications.findByRecipientUserIdAndIsReadFalseOrderByCreatedAtDesc(me).stream()
                .filter(n -> n.getType() != Notification.NotificationType.CHAT)
                .filter(n -> n.getCreatedAt() != null && !n.getCreatedAt().isBefore(since))
                .limit(LIMIT)
                .map(MyNotificationService::toView)
                .toList();
    }

    /** 확인 — 내 알림만 읽음으로 한다. 남의 것이나 없는 id는 조용히 건너뛴다. @return 읽음으로 바꾼 건수 */
    @Transactional
    public int markRead(Collection<Long> ids) {
        String me = requireMe();
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        int changed = 0;
        for (Notification n : notifications.findAllById(ids.stream().limit(100).toList())) {
            if (me.equals(n.getRecipientUserId()) && !Boolean.TRUE.equals(n.getIsRead())) {
                n.setIsRead(true);
                n.setReadAt(now);
                changed++;
            }
        }
        return changed;
    }

    private String requireMe() {
        String me = chatCallerResolver.currentChatUserId();
        if (me == null) {
            throw new CustomException("로그인이 필요합니다", HttpStatus.UNAUTHORIZED);
        }
        return me;
    }

    private static View toView(Notification n) {
        return new View(n.getId(), n.getTitle(), n.getMessage(), n.getType() == null ? null : n.getType().name(),
                n.getRelatedEntityId(), n.getRelatedEntityType(), n.getCreatedAt());
    }
}
