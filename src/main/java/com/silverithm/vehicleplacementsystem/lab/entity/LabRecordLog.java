package com.silverithm.vehicleplacementsystem.lab.entity;

import com.silverithm.vehicleplacementsystem.entity.EncryptedPiiConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 종류별 기록 한 줄 — 업무수행일지(프로그램 관리자·선임 요양보호사), 시설운영일지, 수급자 안전관리 설명, 현금영수증 발행.
 *
 * <p>모두 "날짜 + 작성자 + 확인한 항목(체크) + 내용" 모양이라 한 표에 종류(kind)로 나눠 담는다. 종류마다 필수 칸과 허용
 * 체크 항목이 다르고, 그 규칙은 서비스가 정한다. 내용·조치·부가 정보는 건강·금전 정보가 섞일 수 있어 암호화한다.
 * 체크 항목은 쉼표로 이은 코드 문자열(예: "FALL,BEDSORE")이다.
 */
@Entity
@Table(name = "lab_record_logs", indexes = {
        @Index(name = "idx_lab_record_logs_company_kind_date", columnList = "company_id, kind, entry_date"),
        @Index(name = "idx_lab_record_logs_elder_kind_date", columnList = "elderly_id, kind, entry_date")})
@Getter
@NoArgsConstructor
public class LabRecordLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "kind", nullable = false, length = 30)
    private String kind;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    /** 수급자별 기록(안전관리 설명·현금영수증)만 값이 있다 */
    @Column(name = "elderly_id")
    private Long elderlyId;

    @Column(name = "author_name", nullable = false, length = 50)
    private String authorName;

    @Column(name = "checks", nullable = false, length = 300)
    private String checks;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "action", columnDefinition = "TEXT")
    private String action;

    /** 현금영수증 승인번호처럼 종류마다 한 칸 더 필요한 값. 평문 100자 이하 */
    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "extra", length = 512)
    private String extra;

    /** 현금영수증 발행 금액(원) */
    @Column(name = "amount")
    private Long amount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public LabRecordLog(Long companyId, String kind) {
        this.companyId = companyId;
        this.kind = kind;
    }

    public void fill(LocalDate entryDate, Long elderlyId, String authorName, String checks, String body,
                     String action, String extra, Long amount) {
        this.entryDate = entryDate;
        this.elderlyId = elderlyId;
        this.authorName = authorName;
        this.checks = checks;
        this.body = body;
        this.action = action;
        this.extra = extra;
        this.amount = amount;
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
