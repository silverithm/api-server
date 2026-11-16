package com.silverithm.vehicleplacementsystem.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 결재 양식의 한 시점 모습 — 저장할 때마다 한 벌(2026-11-16, 구글 시트 버전 기록처럼).
 *
 * <p>숲속 업무일지에서 항목을 복제한 뒤 원본을 지우자 지난 문서 본문이 화면에서 사라졌다.
 * 양식의 예전 모습이 어디에도 남지 않아 무엇이 바뀌었는지 서버 로그와 문서 내용으로 거꾸로 맞춰야 했다.
 * 이제 저장마다 모습을 남기고, 양식 관리에서 골라 보고 되돌린다. 버전은 고치지 않는다(덧붙이기만).
 */
@Entity
@Table(name = "approval_template_versions",
        uniqueConstraints = @UniqueConstraint(name = "uk_approval_template_version",
                columnNames = {"template_id", "version_no"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApprovalTemplateVersion {

    public enum Kind {
        /** 기록을 시작할 때(배포·첫 수정 직전) 있던 모습 — 누가 만들었는지 모른다 */
        BASELINE,
        CREATED,
        EDITED,
        /** 예전 버전으로 되돌려 저장 */
        RESTORED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    // 운영 표는 VARCHAR(20) — columnDefinition 없이 두면 Hibernate가 MySQL ENUM을 기대해
    // 시작 때 스키마 검사에서 멈춘다(2026-11-16 첫 배포 실패). 다른 enum 칸(열람 대상 종류 등)과 같은 방식.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private Kind kind;

    @Column(name = "restored_from_no")
    private Integer restoredFromNo;

    @Column(nullable = false)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(length = 50)
    private String category;

    @Column(name = "template_type", nullable = false, length = 10)
    private String templateType;

    @Column(name = "form_schema", columnDefinition = "JSON")
    private String formSchema;

    @Column(name = "default_approval_line", columnDefinition = "JSON")
    private String defaultApprovalLine;

    @Column(name = "file_url")
    private String fileUrl;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_size")
    private Long fileSize;

    /** 저장한 사람 — 채팅 식별자(직원 "12", 관리자 "admin_3") */
    @Column(name = "saved_by_id", length = 40)
    private String savedById;

    @Column(name = "saved_by_name", length = 50)
    private String savedByName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    private ApprovalTemplateVersion(ApprovalTemplate template, Integer versionNo, Kind kind, Integer restoredFromNo,
                                    String savedById, String savedByName, LocalDateTime createdAt) {
        this.templateId = template.getId();
        this.companyId = template.getCompany().getId();
        this.versionNo = versionNo;
        this.kind = kind;
        this.restoredFromNo = restoredFromNo;
        this.name = template.getName();
        this.description = template.getDescription();
        this.category = template.getCategory();
        this.templateType = template.getTemplateType() != null ? template.getTemplateType() : "file";
        this.formSchema = template.getFormSchema();
        this.defaultApprovalLine = template.getDefaultApprovalLine();
        this.fileUrl = template.getFileUrl();
        this.fileName = template.getFileName();
        this.fileSize = template.getFileSize();
        this.savedById = savedById;
        this.savedByName = savedByName;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }
}
