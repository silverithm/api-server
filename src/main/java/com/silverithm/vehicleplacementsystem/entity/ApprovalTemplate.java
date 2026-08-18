package com.silverithm.vehicleplacementsystem.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "approval_templates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false)
    private String name;

    @Column(length = 500)
    private String description;

    /** 기안 대분류 (공문/교육/인사 등) — 기관이 자유롭게 지정 */
    @Column(length = 50)
    private String category;

    @Builder.Default
    @Column(name = "template_type", nullable = false, length = 10)
    private String templateType = "file";

    @Column(name = "form_schema", columnDefinition = "JSON")
    private String formSchema;

    /** 기본 결재선 — 이 양식으로 기안하면 자동으로 채워진다 (기안자가 수정 가능) */
    @Column(name = "default_approval_line", columnDefinition = "JSON")
    private String defaultApprovalLine;

    /** 이 양식으로 기안한 문서의 기본 열람 대상 — 기안 시 문서로 복사된다 */
    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private java.util.List<ApprovalTemplateViewer> defaultViewers = new java.util.ArrayList<>();

    @Column(nullable = true)
    private String fileUrl;

    @Column(nullable = true)
    private String fileName;

    @Column(nullable = true)
    private Long fileSize;

    @Column(nullable = false)
    private Boolean isActive;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (isActive == null) {
            isActive = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
