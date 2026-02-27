package com.silverithm.vehicleplacementsystem.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String title;
    
    @Column(nullable = false, length = 1000)
    private String message;
    
    @Column(nullable = false)
    private String recipientToken;
    
    @Column
    private String recipientUserId;
    
    @Column
    private String recipientUserName;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;
    
    @Column
    private Long relatedEntityId;
    
    @Column
    private String relatedEntityType;
    
    @Column(nullable = false)
    private Boolean sent;

    @Column
    private LocalDateTime sentAt;

    @Column(nullable = false)
    private Boolean isRead;

    @Column
    private LocalDateTime readAt;
    
    @Column
    private String fcmMessageId;
    
    @Column
    private String errorMessage;
    
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (sent == null) {
            sent = false;
        }
        if (isRead == null) {
            isRead = false;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public enum NotificationType {
        VACATION_APPROVED,      // 휴가 승인
        VACATION_REJECTED,      // 휴가 거부
        VACATION_SUBMITTED,     // 휴가 신청 (관리자에게)
        VACATION_REMINDER,      // 휴가 리마인더
        MEMBER_JOIN_REQUESTED,  // 회원가입 요청 (관리자에게)
        MEMBER_JOIN_APPROVED,   // 회원가입 승인
        MEMBER_JOIN_REJECTED,   // 회원가입 거부
        NOTICE,                 // 공지사항
        CHAT,                   // 채팅 알림
        APPROVAL,               // 결재 알림
        GENERAL                 // 일반 알림
    }
} 