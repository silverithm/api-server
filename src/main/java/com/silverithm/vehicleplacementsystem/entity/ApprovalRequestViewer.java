package com.silverithm.vehicleplacementsystem.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 문서별 열람 대상.
 *
 * <p>기안할 때 양식의 기본 열람 대상이 복사되고, 기안자가 개인을 더하거나 뺄 수 있다.
 * 관리자·기안자 본인·결재선 참여자는 여기에 없어도 항상 열람할 수 있다.
 */
@Entity
@Table(name = "approval_request_viewers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalRequestViewer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approval_request_id", nullable = false)
    private ApprovalRequest approvalRequest;

    @Enumerated(EnumType.STRING)
    @Column(name = "viewer_type", nullable = false, columnDefinition = "varchar(10)")
    private ApprovalViewerType viewerType;

    @Column(name = "ref_id", nullable = false)
    private Long refId;

    /** 지정 시점의 직책명 또는 사람 이름 — 표시·검색용 스냅샷 */
    @Column(name = "viewer_name", nullable = false)
    private String viewerName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
