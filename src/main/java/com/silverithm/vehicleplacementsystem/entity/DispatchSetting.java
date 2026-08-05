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
 * 배차 설정 — 노선, 노선별 주·부운전자, 어르신 탑승 순서.
 *
 * 프론트가 { routes, seniors } 한 덩어리로 읽고 쓰므로 JSON으로 보관한다.
 * 회사당 한 벌이며, 웹 관리자와 직원 앱이 같은 설정을 본다.
 */
@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "dispatch_settings")
public class DispatchSetting extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, unique = true)
    private Long companyId;

    /** { "routes": [...], "seniors": [...] } */
    @Column(name = "settings_json", nullable = false, columnDefinition = "LONGTEXT")
    private String settingsJson;
}
