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
 * 휴무 입력 마감일의 월별 직접 지정.
 *
 * "셋째 주 일요일"처럼 달마다 달라지는 마감일을 위해, 행이 있는 달은
 * 이 날짜가 vacation_deadline_settings.deadline_day(매월 고정일)보다 우선한다.
 */
@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "vacation_deadline_dates")
public class VacationDeadlineDate extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /** 마감일이 속한 달 (yyyy-MM) */
    @Column(name = "target_month", nullable = false, length = 7)
    private String targetMonth;

    @Column(name = "deadline_date", nullable = false)
    private LocalDate deadlineDate;
}
