package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
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
} 