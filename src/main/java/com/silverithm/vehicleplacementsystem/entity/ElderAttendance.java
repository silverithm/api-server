package com.silverithm.vehicleplacementsystem.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
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
    @Column(nullable = false, length = 20)
    private ElderAttendanceStatus status;

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
}
