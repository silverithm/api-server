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
import java.time.LocalTime;

@Entity
@Table(name = "employee_attendance",
        uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "date"}))
@Getter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class EmployeeAttendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private AttendanceStatus status;

    private LocalTime checkInTime;

    private LocalTime checkOutTime;

    @Column(length = 500)
    private String note;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime modifiedAt;

    public EmployeeAttendance(Member member, Company company, LocalDate date, AttendanceStatus status) {
        this.member = member;
        this.company = company;
        this.date = date;
        this.status = status;
    }

    public void updateStatus(AttendanceStatus status) {
        this.status = status;
    }

    public void updateCheckInTime(LocalTime checkInTime) {
        this.checkInTime = checkInTime;
    }

    public void updateCheckOutTime(LocalTime checkOutTime) {
        this.checkOutTime = checkOutTime;
    }

    public void updateNote(String note) {
        this.note = note;
    }
}
