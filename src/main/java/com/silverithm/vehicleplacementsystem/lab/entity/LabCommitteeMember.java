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

/**
 * 운영위원 1명의 임기 1건. 위원은 직원이 아니라 외부인(보호자·지역 인사·전문가)이 많아 members와 잇지 않고
 * 이름을 직접 담는다. 이름은 개인정보라 암호화한다(평문 50자 이하라 열은 VARCHAR(512)).
 */
@Entity
@Table(name = "lab_committee_members", indexes = @Index(
        name = "idx_lab_committee_members_company_active", columnList = "company_id, active"))
@Getter
@NoArgsConstructor
public class LabCommitteeMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "name", nullable = false, length = 512)
    private String name;

    @Column(name = "role", nullable = false, length = 20)
    private String role;

    @Column(name = "term_start", nullable = false)
    private LocalDate termStart;

    @Column(name = "term_end", nullable = false)
    private LocalDate termEnd;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "note", length = 200)
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public LabCommitteeMember(Long companyId) {
        this.companyId = companyId;
    }

    public void fill(String name, String role, LocalDate termStart, LocalDate termEnd, boolean active,
                     String note) {
        this.name = name;
        this.role = role;
        this.termStart = termStart;
        this.termEnd = termEnd;
        this.active = active;
        this.note = note;
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
