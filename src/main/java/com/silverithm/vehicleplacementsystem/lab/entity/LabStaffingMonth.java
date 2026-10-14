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
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 월 인력배치 점검 — 기관 x 달 1건.
 *
 * <p>직원 목록(이름·직종·근무시간)은 개인별 근로 정보라 JSON 통째로 암호화한다. 공휴일은 날짜뿐이라 평문이다.
 * service_days는 SMALLINT 열이라 엔티티도 short로 받아 Hibernate 스키마 검증이 어긋나지 않게 한다.
 */
@Entity
@Table(name = "lab_staffing_months", uniqueConstraints = @UniqueConstraint(
        name = "uk_lab_staffing_months_company_month", columnNames = {"company_id", "staffing_month"}))
@Getter
@NoArgsConstructor
public class LabStaffingMonth {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "staffing_month", nullable = false, columnDefinition = "CHAR(7)")
    private String staffingMonth;

    @Column(name = "service_days", nullable = false)
    private short serviceDays;

    @Column(name = "users_sum", nullable = false)
    private int usersSum;

    @Column(name = "extra_users_note", length = 200)
    private String extraUsersNote;

    /** 'YYYY-MM-DD' JSON 배열 */
    @Column(name = "holidays", nullable = false, columnDefinition = "TEXT")
    private String holidays;

    /** [{memberId, name, jobType, hours}] JSON — 암호화 */
    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "staff", nullable = false, columnDefinition = "TEXT")
    private String staff;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "writer_name", length = 50)
    private String writerName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public LabStaffingMonth(Long companyId, String staffingMonth) {
        this.companyId = companyId;
        this.staffingMonth = staffingMonth;
    }

    /** 같은 달을 다시 저장하면 마지막 저장이 그 달의 기록이 되도록 내용을 통째로 덮어쓴다 */
    public void fill(short serviceDays, int usersSum, String extraUsersNote, String holidays, String staff,
                     String note, String writerName) {
        this.serviceDays = serviceDays;
        this.usersSum = usersSum;
        this.extraUsersNote = extraUsersNote;
        this.holidays = holidays;
        this.staff = staff;
        this.note = note;
        this.writerName = writerName;
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
