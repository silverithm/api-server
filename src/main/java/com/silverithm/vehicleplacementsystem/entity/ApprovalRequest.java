package com.silverithm.vehicleplacementsystem.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    // 폼 데이터 (form 타입 양식)
    @Column(name = "form_data", columnDefinition = "JSON")
    private String formData;

    // 첨부파일 정보
    @Column(length = 1000)
    private String attachmentUrl;

    @Column(length = 500)
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

    // 결재선 (없으면 legacy 단일 승인 방식)
    @OneToMany(mappedBy = "approvalRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stepOrder ASC")
    @Builder.Default
    private List<ApprovalStep> steps = new ArrayList<>();

    @Column(name = "doc_number", length = 50)
    private String docNumber;

    @Column(name = "doc_number_display", length = 50)
    private String docNumberDisplay;

    @Column(name = "current_step_order")
    private Integer currentStepOrder;

    @Column(name = "has_approval_line", nullable = false)
    @Builder.Default
    private Boolean hasApprovalLine = false;

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

    public boolean hasSteps() {
        return Boolean.TRUE.equals(hasApprovalLine) && steps != null && !steps.isEmpty();
    }

    public ApprovalStep currentStep() {
        if (!hasSteps() || currentStepOrder == null) {
            return null;
        }

        return steps.stream()
                .filter(step -> currentStepOrder.equals(step.getStepOrder()))
                .findFirst()
                .orElse(null);
    }

    public boolean isFinalStep(ApprovalStep step) {
        return step != null && step.getRoleLabel() == ApprovalStep.StepRole.FINAL;
    }

    public enum ApprovalStatus {
        /**
         * 임시저장. 아직 상신하지 않은 문서로 기안자 본인에게만 보이고 결재함에는 뜨지 않는다.
         * 공문은 한 번에 다 쓰기 어려워, 중간까지 적어두었다가 나중에 마저 쓰는 경우가 많다.
         */
        DRAFT,
        PENDING,    // 대기중
        APPROVED,   // 승인됨
        REJECTED    // 반려됨
    }
}
