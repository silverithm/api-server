package com.silverithm.vehicleplacementsystem.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "elder_attendance",
        uniqueConstraints = @UniqueConstraint(columnNames = {"elderly_id", "date"}))
@Getter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class ElderAttendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "elderly_id", nullable = false)
    private Elderly elderly;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ElderAttendanceStatus status;

    /**
     * 개인등원 - 보호자가 직접 데려와 등원 차량에 타지 않는다.
     * 결석(ABSENT)과 달리 출석은 한 것이므로 status와 별개의 플래그로 둔다.
     * (개인등원 + 차량하원 같은 조합이 실제로 존재한다)
     */
    @Column(name = "personal_pickup", nullable = false)
    private boolean personalPickup = false;

    /** 개인하원 - 보호자가 직접 데려가 하원 차량에 타지 않는다. */
    @Column(name = "personal_dropoff", nullable = false)
    private boolean personalDropoff = false;

    @Column(length = 500)
    private String note;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime modifiedAt;

    public ElderAttendance(Elderly elderly, Company company, LocalDate date, ElderAttendanceStatus status) {
        this.elderly = elderly;
        this.company = company;
        this.date = date;
        this.status = status;
    }

    public void updateStatus(ElderAttendanceStatus status) {
        this.status = status;
    }

    public void updateNote(String note) {
        this.note = note;
    }

    public void updatePersonalTransport(boolean personalPickup, boolean personalDropoff) {
        this.personalPickup = personalPickup;
        this.personalDropoff = personalDropoff;
    }
}
