package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.FCMNotificationRequestDTO;
import com.silverithm.vehicleplacementsystem.entity.Notification;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 업무진행도 알림 — 앱에는 푸시로, 웹에는 알림함(확인할 때까지 떠 있는 팝업)으로 간다.
 *
 * <p>기기 토큰이 없는 사람(앱을 안 깐 관리자 등)도 웹에서는 봐야 하므로 알림함에는 늘 남긴다.
 * 업무 저장이 끝난(커밋된) 뒤에 보낸다 — 저장이 되돌려졌는데 알림만 가는 일이 없고,
 * 알림이 실패해도 업무 저장은 그대로다.
 */
@Slf4j
@Component
public class WorkTaskNotifier {

    static final String TYPE = "work_task";
    /**
     * 매일 아침 요약의 알림함 종류 — 담당 지정·완료 알림("work_task")과 갈라둔다.
     * 요약은 그날에만 의미가 있어 다음 날 아침에 지난 것을 읽음 처리하는데, 종류가 같으면
     * 아직 안 본 담당 지정 알림까지 같이 사라진다.
     */
    static final String DAILY_ENTITY_TYPE = "work_task_daily";

    private final NotificationService notificationService;
    private final WorkTaskPeople people;
    private final TransactionTemplate ownTransaction;

    public WorkTaskNotifier(NotificationService notificationService, WorkTaskPeople people,
                            PlatformTransactionManager transactionManager) {
        this.notificationService = notificationService;
        this.people = people;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** 받는 사람 한 명분 알림. relatedEntityType은 알림함에 남는 종류 — 담당 지정·완료는 "work_task", 매일 요약은 "work_task_daily" */
    public record Message(String personId, String personName, String title, String body, Long taskId,
                          String relatedEntityType) {

        /** 담당 지정·완료 같은 일반 알림 */
        public Message(String personId, String personName, String title, String body, Long taskId) {
            this(personId, personName, title, body, taskId, TYPE);
        }

        /** 매일 아침 요약 */
        public static Message daily(String personId, String personName, String title, String body, Long taskId) {
            return new Message(personId, personName, title, body, taskId, DAILY_ENTITY_TYPE);
        }
    }

    /** 지금 트랜잭션이 커밋되면 보낸다. 트랜잭션 밖이면 바로 보낸다. */
    public void sendAfterCommit(List<Message> messages) {
        if (messages.isEmpty()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendNow(messages);
                }
            });
        } else {
            sendNow(messages);
        }
    }

    /** @return 알림함에 남긴 건수 */
    public int sendNow(List<Message> messages) {
        int saved = 0;
        for (Message message : messages) {
            try {
                ownTransaction.executeWithoutResult(status -> sendOne(message));
                saved++;
            } catch (Exception e) {
                log.error("[WorkTask] 알림 실패: taskId={}, {}", message.taskId(), e.getMessage());
            }
        }
        return saved;
    }

    private void sendOne(Message message) {
        String token = people.fcmTokenOf(message.personId()).orElse(null);
        if (token == null) {
            // 푸시는 못 보내도 웹 알림함에는 남긴다
            notificationService.saveNotification(message.title(), message.body(), "", message.personId(),
                    message.personName(), Notification.NotificationType.WORK_TASK, message.taskId(),
                    message.relatedEntityType());
            return;
        }
        notificationService.sendAndSaveNotification(FCMNotificationRequestDTO.builder()
                .recipientToken(token)
                .title(message.title())
                .message(message.body())
                .recipientUserId(message.personId())
                .recipientUserName(message.personName())
                .type(TYPE)
                .relatedEntityId(message.taskId())
                .relatedEntityType(message.relatedEntityType())
                .data(Map.of("type", TYPE, "taskId", String.valueOf(message.taskId())))
                .build());
    }
}
