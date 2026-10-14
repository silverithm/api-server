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
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상담일지 — 어르신 본인·보호자와 나눈 상담 한 번.
 *
 * <p>보호자 이름(counterpart_name)은 개인정보라 암호화한다. 암호문이 평문 50자의 약 4.2배라 VARCHAR(512).
 * 상담은 날짜가 같아도 여러 번 있을 수 있어 유일 키를 두지 않고 쌓는다. 표에는 updated_at이 없다.
 */
@Entity
@Table(name = "lab_consultations", indexes = @Index(
        name = "idx_lab_consultations_elder_date", columnList = "elderly_id, consulted_on"))
@Getter
@NoArgsConstructor
public class LabConsultation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "elderly_id", nullable = false)
    private Long elderlyId;

    @Column(name = "consulted_on", nullable = false)
    private LocalDate consultedOn;

    /** SELF | GUARDIAN | FAMILY | OTHER */
    @Column(name = "counterpart", nullable = false, length = 20)
    private String counterpart;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "counterpart_name", length = 512)
    private String counterpartName;

    /** VISIT | PHONE | ONLINE | OTHER */
    @Column(name = "method", nullable = false, length = 20)
    private String method;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "follow_up", columnDefinition = "TEXT")
    private String followUp;

    @Column(name = "consultant_name", length = 50)
    private String consultantName;

    @Column(name = "ai_assisted", nullable = false)
    private boolean aiAssisted;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public LabConsultation(Long companyId) {
        this.companyId = companyId;
    }

    /** 등록과 수정이 같은 경로 — 입력으로 내용을 통째로 덮어쓴다 */
    public void fill(Long elderlyId, LocalDate consultedOn, String counterpart, String counterpartName,
                     String method, String content, String followUp, String consultantName, boolean aiAssisted) {
        this.elderlyId = elderlyId;
        this.consultedOn = consultedOn;
        this.counterpart = counterpart;
        this.counterpartName = counterpartName;
        this.method = method;
        this.content = content;
        this.followUp = followUp;
        this.consultantName = consultantName;
        this.aiAssisted = aiAssisted;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
