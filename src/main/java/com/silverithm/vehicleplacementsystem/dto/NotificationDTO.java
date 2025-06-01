package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.Notification;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationDTO {
    
    private Long id;
    private String title;
    private String message;
    private String recipientUserId;
    private String recipientUserName;
    private String type;
    private Long relatedEntityId;
    private String relatedEntityType;
    private Boolean sent;
    private LocalDateTime sentAt;
    private String fcmMessageId;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    public static NotificationDTO fromEntity(Notification entity) {
        return NotificationDTO.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .message(entity.getMessage())
                .recipientUserId(entity.getRecipientUserId())
                .recipientUserName(entity.getRecipientUserName())
                .type(entity.getType().name().toLowerCase())
                .relatedEntityId(entity.getRelatedEntityId())
                .relatedEntityType(entity.getRelatedEntityType())
                .sent(entity.getSent())
                .sentAt(entity.getSentAt())
                .fcmMessageId(entity.getFcmMessageId())
                .errorMessage(entity.getErrorMessage())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
} 