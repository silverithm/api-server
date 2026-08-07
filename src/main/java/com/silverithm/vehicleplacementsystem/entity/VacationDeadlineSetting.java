package com.silverithm.vehicleplacementsystem.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * 휴무 입력 마감일 설정 — 회사당 한 벌.
 *
 * 매월 deadlineDay일까지 다음 달 휴무 입력을 받는다. 마감일이 지나도
 * 휴무 인원이 제한을 초과한 날짜가 남아 있으면 해당 신청자들에게
 * 조정 요청 푸시를 매일 보낸다 (VacationAdjustmentReminderScheduler).
 */
@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "vacation_deadline_settings")
public class VacationDeadlineSetting extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, unique = true)
    private Long companyId;

    /** 매월 며칠까지 다음 달 휴무를 입력받는지 (1~31, 말일 초과 시 말일로 클램프) */
    @Column(name = "deadline_day", nullable = false)
    private Integer deadlineDay;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /**
     * 켜면 직원은 "바로 다음 달"에 속한 날짜만 휴무를 신청할 수 있다.
     * 마감일과는 별개 스위치다 — 마감일은 "언제까지 받는가", 이건 "어느 달을 받는가".
     */
    @Column(name = "next_month_only", nullable = false)
    private Boolean nextMonthOnly;
}
