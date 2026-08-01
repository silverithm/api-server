package com.silverithm.vehicleplacementsystem.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 감사 로그 — 인증된 쓰기 요청의 누가·언제·무엇을 남긴다.
 * 요청 본문은 개인정보가 섞일 수 있어 저장하지 않는다.
 * 기관과의 연관은 조회용 컬럼으로만 두고 FK를 걸지 않는다 (기관 삭제와 독립).
 */
@Entity
@Table(name = "audit_logs")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(nullable = false)
    private String username;

    @Column(name = "company_id")
    private Long companyId;

    @Column(nullable = false, length = 10)
    private String method;

    @Column(nullable = false, length = 500)
    private String uri;

    @Column(name = "status_code", nullable = false)
    private int statusCode;

    @Column(name = "client_ip", length = 64)
    private String clientIp;
}
