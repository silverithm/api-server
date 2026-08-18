package com.silverithm.vehicleplacementsystem.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 양식별 기본 열람 대상.
 * 이 양식으로 기안하면 여기 지정된 대상이 문서(ApprovalRequestViewer)로 복사된다.
 */
@Entity
@Table(name = "approval_template_viewers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalTemplateViewer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private ApprovalTemplate template;

    // Hibernate 6는 STRING enum을 MySQL ENUM 타입으로 기대하므로 VARCHAR를 명시 (ddl-auto=validate)
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
