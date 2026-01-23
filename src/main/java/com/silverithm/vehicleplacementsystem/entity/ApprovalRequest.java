package com.silverithm.vehicleplacementsystem.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "approval_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private ApprovalTemplate template;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String requesterId;

    @Column(nullable = false)
    private String requesterName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalStatus status;

    // 첨부파일 정보
    @Column
    private String attachmentUrl;

    @Column
    private String attachmentFileName;

    @Column
    private Long attachmentFileSize;

    // 처리 정보
    @Column
    private String processedBy;

    @Column
    private String processedByName;

    @Column
    private LocalDateTime processedAt;

    @Column(length = 1000)
    private String rejectReason;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = ApprovalStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum ApprovalStatus {
        PENDING,    // 대기중
        APPROVED,   // 승인됨
        REJECTED    // 반려됨
    }
}
