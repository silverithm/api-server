package com.silverithm.vehicleplacementsystem.lab.entity;

import com.silverithm.vehicleplacementsystem.entity.EncryptedPiiConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 보호자 월간 안내 — 어르신 x 월 1건. 초안(DRAFT)에서 보냄(SENT)으로 넘어간다.
 *
 * <p>본문·사실 묶음·받는 사람은 보호자와 어르신 상태가 적히므로 암호화한다. 받는 사람 평문 50자가
 * 암호문으로 VARCHAR(512)에 들어간다. 보낸 안내의 잠금은 서비스가 지키고, 상태 전이는 {@link #markSent}·
 * {@link #markUnsent}로만 한다.
 */
@Entity
@Table(name = "lab_guardian_notices",
        uniqueConstraints = @UniqueConstraint(name = "uk_lab_guardian_notices_elder_month",
                columnNames = {"elderly_id", "notice_month"}),
        indexes = @Index(name = "idx_lab_guardian_notices_company_month", columnList = "company_id, notice_month"))
@Getter
@NoArgsConstructor
public class LabGuardianNotice {

    public static final String DRAFT = "DRAFT";
    public static final String SENT = "SENT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "elderly_id", nullable = false)
    private Long elderlyId;

    @Column(name = "notice_month", nullable = false, length = 7, columnDefinition = "CHAR(7)")
    private String noticeMonth;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "facts", columnDefinition = "TEXT")
    private String facts;

    /** DRAFT | SENT */
    @Column(name = "status", nullable = false, length = 10)
    private String status;

    @Column(name = "sent_on")
    private LocalDate sentOn;

    /** SMS | KAKAO | PRINT | EMAIL | VISIT | OTHER */
    @Column(name = "method", length = 10)
    private String method;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "recipient", length = 512)
    private String recipient;

    @Column(name = "writer_name", length = 50)
    private String writerName;

    @Column(name = "ai_assisted", nullable = false)
    private boolean aiAssisted;

    public LabGuardianNotice(Long companyId, Long elderlyId, String noticeMonth) {
        this.companyId = companyId;
        this.elderlyId = elderlyId;
        this.noticeMonth = noticeMonth;
        this.status = DRAFT;
    }

    public boolean isSent() {
        return SENT.equals(status);
    }

    /** 초안의 글을 덮어쓴다(보낸 안내는 서비스가 먼저 막는다) */
    public void fill(String title, String body, String facts, String writerName, boolean aiAssisted) {
        this.title = title;
        this.body = body;
        this.facts = facts;
        this.writerName = writerName;
        this.aiAssisted = aiAssisted;
    }

    public void markSent(LocalDate sentOn, String method, String recipient) {
        this.status = SENT;
        this.sentOn = sentOn;
        this.method = method;
        this.recipient = recipient;
    }

    /** 보냄 표시를 풀고 보낸 정보를 비운다 */
    public void markUnsent() {
        this.status = DRAFT;
        this.sentOn = null;
        this.method = null;
        this.recipient = null;
    }

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
