package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.FCMNotificationRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.NotificationDTO;
import com.silverithm.vehicleplacementsystem.entity.Notification;
import com.silverithm.vehicleplacementsystem.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {
    
    private final NotificationRepository notificationRepository;
    private final FCMService fcmService;
    
    @Transactional
    public NotificationDTO sendAndSaveNotification(FCMNotificationRequestDTO requestDTO) {
        log.info("[Notification Service] 알림 전송 및 저장: {}", requestDTO.getTitle());
        
        // 알림 엔티티 생성
        Notification notification = Notification.builder()
                .title(requestDTO.getTitle())
                .message(requestDTO.getMessage())
                .recipientToken(requestDTO.getRecipientToken())
                .recipientUserId(requestDTO.getRecipientUserId())
                .recipientUserName(requestDTO.getRecipientUserName())
                .type(parseNotificationType(requestDTO.getType()))
                .relatedEntityId(requestDTO.getRelatedEntityId())
                .relatedEntityType(requestDTO.getRelatedEntityType())
                .sent(false)
                .build();
        
        // 데이터베이스에 저장
        Notification saved = notificationRepository.save(notification);
        
        // FCM 전송 시도
        try {
            String fcmMessageId = fcmService.sendNotification(
                    requestDTO.getRecipientToken(),
                    requestDTO.getTitle(),
                    requestDTO.getMessage(),
                    requestDTO.getData()
            );
            
            // 전송 성공 시 업데이트
            saved.setSent(true);
            saved.setSentAt(LocalDateTime.now());
            saved.setFcmMessageId(fcmMessageId);
            saved = notificationRepository.save(saved);
            
            log.info("[Notification Service] 알림 전송 성공: ID={}, FCM ID={}", saved.getId(), fcmMessageId);
            
        } catch (Exception e) {
            log.error("[Notification Service] FCM 전송 실패: ID={}, 오류={}", saved.getId(), e.getMessage());
            
            // 전송 실패 시 오류 메시지 저장
            saved.setSent(false);
            saved.setErrorMessage(e.getMessage());
            saved = notificationRepository.save(saved);
        }
        
        return NotificationDTO.fromEntity(saved);
    }
    
    @Transactional
    public NotificationDTO saveNotification(
            String title, 
            String message, 
            String recipientToken,
            String recipientUserId,
            String recipientUserName,
            Notification.NotificationType type,
            Long relatedEntityId,
            String relatedEntityType) {
        
        Notification notification = Notification.builder()
                .title(title)
                .message(message)
                .recipientToken(recipientToken)
                .recipientUserId(recipientUserId)
                .recipientUserName(recipientUserName)
                .type(type)
                .relatedEntityId(relatedEntityId)
                .relatedEntityType(relatedEntityType)
                .sent(false)
                .build();
        
        Notification saved = notificationRepository.save(notification);
        
        log.info("[Notification Service] 알림 저장 완료: ID={}, 제목={}", saved.getId(), title);
        
        return NotificationDTO.fromEntity(saved);
    }
    
    public List<NotificationDTO> getNotificationsByUserId(String userId) {
        log.info("[Notification Service] 사용자 알림 조회: userId={}", userId);
        
        List<Notification> notifications = notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(userId);
        
        return notifications.stream()
                .map(NotificationDTO::fromEntity)
                .collect(Collectors.toList());
    }
    
    public List<NotificationDTO> getNotificationsByUserName(String userName) {
        log.info("[Notification Service] 사용자 알림 조회: userName={}", userName);
        
        List<Notification> notifications = notificationRepository.findByRecipientUserNameOrderByCreatedAtDesc(userName);
        
        return notifications.stream()
                .map(NotificationDTO::fromEntity)
                .collect(Collectors.toList());
    }
    
    public List<NotificationDTO> getNotificationsByType(Notification.NotificationType type) {
        log.info("[Notification Service] 타입별 알림 조회: type={}", type);
        
        List<Notification> notifications = notificationRepository.findByTypeOrderByCreatedAtDesc(type);
        
        return notifications.stream()
                .map(NotificationDTO::fromEntity)
                .collect(Collectors.toList());
    }
    
    public List<NotificationDTO> getFailedNotifications() {
        log.info("[Notification Service] 전송 실패 알림 조회");
        
        List<Notification> notifications = notificationRepository.findBySentFalse();
        
        return notifications.stream()
                .map(NotificationDTO::fromEntity)
                .collect(Collectors.toList());
    }
    
    @Transactional
    public void retryFailedNotifications() {
        log.info("[Notification Service] 실패한 알림 재전송 시작");
        
        List<Notification> failedNotifications = notificationRepository.findBySentFalse();
        
        for (Notification notification : failedNotifications) {
            try {
                String fcmMessageId = fcmService.sendNotification(
                        notification.getRecipientToken(),
                        notification.getTitle(),
                        notification.getMessage()
                );
                
                notification.setSent(true);
                notification.setSentAt(LocalDateTime.now());
                notification.setFcmMessageId(fcmMessageId);
                notification.setErrorMessage(null);
                notificationRepository.save(notification);
                
                log.info("[Notification Service] 재전송 성공: ID={}", notification.getId());
                
            } catch (Exception e) {
                log.error("[Notification Service] 재전송 실패: ID={}, 오류={}", 
                        notification.getId(), e.getMessage());
                
                notification.setErrorMessage(e.getMessage());
                notificationRepository.save(notification);
            }
        }
        
        log.info("[Notification Service] 실패한 알림 재전송 완료");
    }
    
    // 휴가 관련 알림 전송 헬퍼 메서드들
    public NotificationDTO sendVacationApprovedNotification(
            String recipientToken, 
            String recipientUserId, 
            String recipientUserName, 
            String vacationDate,
            Long vacationId) {
        
        FCMNotificationRequestDTO request = FCMNotificationRequestDTO.builder()
                .recipientToken(recipientToken)
                .title("휴가 승인 알림")
                .message(vacationDate + " 휴가 신청이 승인되었습니다.")
                .recipientUserId(recipientUserId)
                .recipientUserName(recipientUserName)
                .type("vacation_approved")
                .relatedEntityId(vacationId)
                .relatedEntityType("vacation_request")
                .data(Map.of(
                        "type", "vacation_approved",
                        "vacationId", String.valueOf(vacationId),
                        "vacationDate", vacationDate
                ))
                .build();
        
        return sendAndSaveNotification(request);
    }
    
    public NotificationDTO sendVacationRejectedNotification(
            String recipientToken, 
            String recipientUserId, 
            String recipientUserName, 
            String vacationDate,
            Long vacationId) {
        
        FCMNotificationRequestDTO request = FCMNotificationRequestDTO.builder()
                .recipientToken(recipientToken)
                .title("휴가 거부 알림")
                .message(vacationDate + " 휴가 신청이 거부되었습니다.")
                .recipientUserId(recipientUserId)
                .recipientUserName(recipientUserName)
                .type("vacation_rejected")
                .relatedEntityId(vacationId)
                .relatedEntityType("vacation_request")
                .data(Map.of(
                        "type", "vacation_rejected",
                        "vacationId", String.valueOf(vacationId),
                        "vacationDate", vacationDate
                ))
                .build();
        
        return sendAndSaveNotification(request);
    }
    
    public NotificationDTO sendVacationSubmittedNotification(
            String recipientToken, 
            String recipientUserId, 
            String recipientUserName, 
            String submitterName,
            String vacationDate,
            Long vacationId) {
        
        FCMNotificationRequestDTO request = FCMNotificationRequestDTO.builder()
                .recipientToken(recipientToken)
                .title("새 휴가 신청")
                .message(submitterName + "님이 " + vacationDate + " 휴가를 신청했습니다.")
                .recipientUserId(recipientUserId)
                .recipientUserName(recipientUserName)
                .type("vacation_submitted")
                .relatedEntityId(vacationId)
                .relatedEntityType("vacation_request")
                .data(Map.of(
                        "type", "vacation_submitted",
                        "vacationId", String.valueOf(vacationId),
                        "vacationDate", vacationDate,
                        "submitterName", submitterName
                ))
                .build();
        
        return sendAndSaveNotification(request);
    }
    
    private Notification.NotificationType parseNotificationType(String type) {
        if (type == null) {
            return Notification.NotificationType.GENERAL;
        }
        
        try {
            return Notification.NotificationType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("[Notification Service] 알 수 없는 알림 타입: {}", type);
            return Notification.NotificationType.GENERAL;
        }
    }
} 