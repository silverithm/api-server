package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    
    List<Notification> findByRecipientUserIdOrderByCreatedAtDesc(String recipientUserId);
    
    List<Notification> findByRecipientUserNameOrderByCreatedAtDesc(String recipientUserName);
    
    List<Notification> findByTypeOrderByCreatedAtDesc(Notification.NotificationType type);
    
    List<Notification> findBySentFalse();
    
    @Query("SELECT n FROM Notification n WHERE n.relatedEntityId = :entityId AND n.relatedEntityType = :entityType")
    List<Notification> findByRelatedEntity(@Param("entityId") Long entityId, @Param("entityType") String entityType);
    
    @Query("SELECT n FROM Notification n WHERE n.createdAt BETWEEN :startDate AND :endDate ORDER BY n.createdAt DESC")
    List<Notification> findByCreatedAtBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
    
    Long countByRecipientUserIdAndSentTrue(String recipientUserId);

    Long countByRecipientUserIdAndIsReadFalse(String recipientUserId);

    List<Notification> findByRecipientUserIdAndIsReadFalseOrderByCreatedAtDesc(String recipientUserId);

    /**
     * 채팅방을 읽었을 때 그 방과 관련된 안읽은 알림들을 한 번에 읽음 처리한다
     * (알림 건마다 조회+저장하지 않고 대량 UPDATE 한 번으로 끝낸다).
     */
    // flushAutomatically가 없으면 이 벌크 UPDATE 전에 영속성 컨텍스트가 flush되지 않는데,
    // clearAutomatically가 컨텍스트를 비워 버려 아직 안 쓰인 변경(읽음 위치 등)이 통째로 사라진다.
    // 2026-09-17 '읽었는데 안읽음 숫자가 다시 생김' 사고의 원인이었다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :readAt " +
            "WHERE n.recipientUserId = :recipientUserId " +
            "AND n.relatedEntityId = :relatedEntityId " +
            "AND n.relatedEntityType = :relatedEntityType " +
            "AND n.isRead = false")
    int markReadByRelatedEntity(@Param("recipientUserId") String recipientUserId,
                                 @Param("relatedEntityId") Long relatedEntityId,
                                 @Param("relatedEntityType") String relatedEntityType,
                                 @Param("readAt") LocalDateTime readAt);
} 