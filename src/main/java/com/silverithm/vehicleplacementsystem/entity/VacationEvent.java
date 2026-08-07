package com.silverithm.vehicleplacementsystem.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * 근무조정 중요 행사 — 관리자가 등록해 직원이 휴무를 피하도록 알린다.
 * 월간일정(Schedule)과 달리 휴무 달력에 겹쳐 보여주는 가벼운 표시 전용이다.
 */
@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "vacation_events")
public class VacationEvent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 500)
    private String description;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** 하루짜리 행사는 startDate와 같다 */
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** 휴무 신청 시 경고를 띄울지 (끄면 달력 표시만) */
    @Column(name = "warn_on_request", nullable = false)
    private boolean warnOnRequest;

    @Column(name = "created_by")
    private String createdBy;
}
