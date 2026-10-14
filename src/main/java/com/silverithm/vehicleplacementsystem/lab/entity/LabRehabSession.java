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
 * 기능회복훈련 기록 — 어르신 1명의 훈련 1회. 하루에 여러 번 할 수 있어 유일 키가 없다.
 *
 * <p>훈련 항목(items)은 이름만 담은 짧은 문자열 배열이라 암호화하지 않고 JSON 문자열로 둔다.
 * 내용·반응은 어르신 상태가 적히는 서술이라 암호화한다.
 */
@Entity
@Table(name = "lab_rehab_sessions", indexes = {
        @Index(name = "idx_lab_rehab_sessions_company_date", columnList = "company_id, session_date"),
        @Index(name = "idx_lab_rehab_sessions_elder_date", columnList = "elderly_id, session_date")})
@Getter
@NoArgsConstructor
public class LabRehabSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "elderly_id", nullable = false)
    private Long elderlyId;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Column(name = "kind", nullable = false, length = 20)
    private String kind;

    @Column(name = "minutes", nullable = false)
    private short minutes;

    /** JSON 문자열 배열 */
    @Column(name = "items", columnDefinition = "TEXT")
    private String items;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "reaction", columnDefinition = "TEXT")
    private String reaction;

    @Column(name = "therapist_name", length = 50)
    private String therapistName;

    @Column(name = "ai_assisted", nullable = false)
    private boolean aiAssisted;

    public LabRehabSession(Long companyId) {
        this.companyId = companyId;
    }

    public void fill(Long elderlyId, LocalDate sessionDate, String kind, short minutes, String items,
                     String content, String reaction, String therapistName, boolean aiAssisted) {
        this.elderlyId = elderlyId;
        this.sessionDate = sessionDate;
        this.kind = kind;
        this.minutes = minutes;
        this.items = items;
        this.content = content;
        this.reaction = reaction;
        this.therapistName = therapistName;
        this.aiAssisted = aiAssisted;
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
