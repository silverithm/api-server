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
 * 그날 하루치 배차 수정본.
 *
 * 배차표는 노선 설정에서 매일 다시 계산된다. 현장에서 "오늘은 저 어르신을 저 차에" 같은
 * 조정이 생기면 예전에는 노선 설정 자체를 고쳐야 했고, 그러면 다음 날부터도 바뀌었다.
 * 하루치 수정본은 여기 따로 쌓고, 설정은 건드리지 않는다.
 */
@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "dispatch_day_overrides")
public class DispatchDayOverride extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "dispatch_date", nullable = false)
    private LocalDate dispatchDate;

    /** [{ "seniorId": "...", "routeId": "...", "tripOrder": 1, "boardingOrder": 3 }, ...] */
    @Column(name = "overrides_json", nullable = false, columnDefinition = "LONGTEXT")
    private String overridesJson;
}
