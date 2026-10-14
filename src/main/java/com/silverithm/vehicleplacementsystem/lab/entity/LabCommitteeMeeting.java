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
 * 운영위원회 회의 1건. 참석 위원은 운영위원 id JSON 배열(최대 30명), 결정 사항은 문자열 JSON 배열이다.
 * 회의록은 어르신 사례가 오갈 수 있어 암호화한다. 안건은 목록에 보이는 제목 성격이라 평문이다.
 */
@Entity
@Table(name = "lab_committee_meetings", indexes = @Index(
        name = "idx_lab_committee_meetings_company_held", columnList = "company_id, held_on"))
@Getter
@NoArgsConstructor
public class LabCommitteeMeeting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "held_on", nullable = false)
    private LocalDate heldOn;

    /** REGULAR | SPECIAL */
    @Column(name = "kind", nullable = false, length = 12)
    private String kind;

    @Column(name = "attendee_ids", nullable = false, columnDefinition = "TEXT")
    private String attendeeIds;

    @Column(name = "agenda", nullable = false, columnDefinition = "TEXT")
    private String agenda;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "minutes", columnDefinition = "TEXT")
    private String minutes;

    @Column(name = "decisions", nullable = false, columnDefinition = "TEXT")
    private String decisions;

    @Column(name = "next_on")
    private LocalDate nextOn;

    @Column(name = "writer_name", length = 50)
    private String writerName;

    @Column(name = "ai_assisted", nullable = false)
    private boolean aiAssisted;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public LabCommitteeMeeting(Long companyId) {
        this.companyId = companyId;
    }

    public void fill(LocalDate heldOn, String kind, String attendeeIds, String agenda, String minutes,
                     String decisions, LocalDate nextOn, String writerName, boolean aiAssisted) {
        this.heldOn = heldOn;
        this.kind = kind;
        this.attendeeIds = attendeeIds;
        this.agenda = agenda;
        this.minutes = minutes;
        this.decisions = decisions;
        this.nextOn = nextOn;
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
