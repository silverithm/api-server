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
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 프로그램 의견수렴·반영 1건 — 반기마다 여러 건. 의견 요약과 반영 내용은 암호화한다. */
@Entity
@Table(name = "lab_program_feedback", indexes = @Index(
        name = "idx_lab_program_feedback_company_half", columnList = "company_id, half"))
@Getter
@NoArgsConstructor
public class LabProgramFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /** YYYY-H1 | YYYY-H2 */
    @Column(name = "half", nullable = false, length = 7)
    private String half;

    @Column(name = "collected_on", nullable = false)
    private LocalDate collectedOn;

    /** SURVEY | INTERVIEW | MEETING | OTHER */
    @Column(name = "method", nullable = false, length = 12)
    private String method;

    @Column(name = "participants", nullable = false)
    private short participants;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "summary", nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "reflection", columnDefinition = "TEXT")
    private String reflection;

    @Column(name = "reflected_on")
    private LocalDate reflectedOn;

    @Column(name = "writer_name", length = 50)
    private String writerName;

    @Column(name = "ai_assisted", nullable = false)
    private boolean aiAssisted;

    public LabProgramFeedback(Long companyId) {
        this.companyId = companyId;
    }

    public void fill(String half, LocalDate collectedOn, String method, short participants, String summary,
                     String reflection, LocalDate reflectedOn, String writerName, boolean aiAssisted) {
        this.half = half;
        this.collectedOn = collectedOn;
        this.method = method;
        this.participants = participants;
        this.summary = summary;
        this.reflection = reflection;
        this.reflectedOn = reflectedOn;
        this.writerName = writerName;
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
