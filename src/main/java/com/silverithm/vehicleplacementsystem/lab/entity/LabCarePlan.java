package com.silverithm.vehicleplacementsystem.lab.entity;

import com.silverithm.vehicleplacementsystem.entity.EncryptedPiiConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 욕구사정·급여제공계획 — 어르신 1명 x 연도 1건.
 *
 * <p>욕구(needs)와 급여 내용(services)은 JSON 문자열로 담은 채 통째로 암호화한다. 구조를 DB가 볼 필요가
 * 없고, 어르신의 상태가 그대로 적히기 때문이다. plan_year는 SMALLINT라 Short로 받는다.
 */
@Entity
@Table(name = "lab_care_plans", uniqueConstraints = @UniqueConstraint(
        name = "uk_lab_care_plans_elder_year", columnNames = {"elderly_id", "plan_year"}))
@Getter
@NoArgsConstructor
public class LabCarePlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "elderly_id", nullable = false)
    private Long elderlyId;

    @Column(name = "plan_year", nullable = false)
    private Short planYear;

    @Column(name = "assessed_on", nullable = false)
    private LocalDate assessedOn;

    /** 욕구사정 JSON 객체 {physical, cognition, nutrition, communication, family, other} */
    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "needs", columnDefinition = "TEXT")
    private String needs;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "goals", columnDefinition = "TEXT")
    private String goals;

    /** 급여제공 내용 JSON 배열 [{area, content, frequency}] */
    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "services", columnDefinition = "TEXT")
    private String services;

    @Column(name = "guardian_agreed_on")
    private LocalDate guardianAgreedOn;

    @Column(name = "writer_name", length = 50)
    private String writerName;

    @Column(name = "ai_assisted", nullable = false)
    private boolean aiAssisted;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public LabCarePlan(Long companyId, Long elderlyId, Short planYear) {
        this.companyId = companyId;
        this.elderlyId = elderlyId;
        this.planYear = planYear;
    }

    /** 같은 해 다시 저장하면 계획을 통째로 갱신한다 */
    public void fill(LocalDate assessedOn, String needs, String goals, String services,
                     LocalDate guardianAgreedOn, String writerName, boolean aiAssisted) {
        this.assessedOn = assessedOn;
        this.needs = needs;
        this.goals = goals;
        this.services = services;
        this.guardianAgreedOn = guardianAgreedOn;
        this.writerName = writerName;
        this.aiAssisted = aiAssisted;
    }

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
