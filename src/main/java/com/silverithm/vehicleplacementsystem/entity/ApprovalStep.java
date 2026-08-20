package com.silverithm.vehicleplacementsystem.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 전자결재 결재선 단계.
 * 결재선이 지정된 요청은 step_order 순서대로 승인이 진행되며,
 * 중간 단계에서 반려되면 요청 전체가 반려된다.
 */
@Entity
@Table(name = "approval_steps")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approval_request_id", nullable = false)
    private ApprovalRequest approvalRequest;

    @Column(name = "step_order", nullable = false)
    private Integer stepOrder;

    // Hibernate 6는 STRING enum을 MySQL ENUM 타입으로 기대하므로 VARCHAR를 명시 (ddl-auto=validate)
    @Enumerated(EnumType.STRING)
    @Column(name = "approver_type", nullable = false, columnDefinition = "varchar(10)")
    private ApproverType approverType;

    /** 결재자 PK. 이관 문서에서 옛 결재자의 계정을 못 찾으면 null이고, 이름만 남는다 */
    @Column(name = "approver_ref_id")
    private Long approverRefId;

    // 프론트 호환용 문자열 (admin_<id> 또는 memberId) — 표시/응답 전용, 인가 매칭은 (type, refId) 사용
    @Column(name = "approver_id_legacy")
    private String approverIdLegacy;

    @Column(name = "approver_name", nullable = false)
    private String approverName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_label", nullable = false, columnDefinition = "varchar(20)")
    private StepRole roleLabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private StepStatus status;

    @Column(name = "signature_url", length = 1000)
    private String signatureUrl;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "reject_reason", length = 1000)
    private String rejectReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = StepStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum ApproverType {
        ADMIN,   // AppUser (기관 관리자 계정)
        MEMBER   // Member (직원)
    }

    public enum StepRole {
        REVIEWER,  // 검토
        FINAL      // 최종 결재
    }

    // ApprovalRequest.ApprovalStatus와 별개의 enum — 요청 상태 enum은 앱 호환을 위해 불변 유지
    public enum StepStatus {
        PENDING,   // 대기 (내 차례이거나 앞 단계 진행중)
        APPROVED,  // 승인
        REJECTED,  // 반려
        SKIPPED    // 건너뜀 (예약)
    }
}
