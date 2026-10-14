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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 후원금 1건. 후원자 이름은 개인정보라 암호화한다(평문 50자 이하, 암호문은 512자 열에 담긴다).
 * 암호문은 DB에서 검색·정렬하지 못하므로 정렬은 날짜·id로 한다. 영수증 번호는 기관 안에서 유일하다(NULL 여러 건 허용).
 */
@Entity
@Table(name = "lab_donations",
        uniqueConstraints = @UniqueConstraint(name = "uk_lab_donations_company_receipt",
                columnNames = {"company_id", "receipt_no"}),
        indexes = @Index(name = "idx_lab_donations_company_date", columnList = "company_id, received_on"))
@Getter
@NoArgsConstructor
public class LabDonation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "received_on", nullable = false)
    private LocalDate receivedOn;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "donor_name", nullable = false, length = 512)
    private String donorName;

    /** INDIVIDUAL | CORPORATE | GROUP */
    @Column(name = "donor_type", nullable = false, length = 20)
    private String donorType;

    /** CASH | GOODS */
    @Column(name = "kind", nullable = false, length = 10)
    private String kind;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Column(name = "goods_description", length = 200)
    private String goodsDescription;

    @Column(name = "designated_purpose", length = 200)
    private String designatedPurpose;

    @Column(name = "receipt_no", length = 30)
    private String receiptNo;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public LabDonation(Long companyId) {
        this.companyId = companyId;
    }

    public void fill(LocalDate receivedOn, String donorName, String donorType, String kind, long amount,
                     String goodsDescription, String designatedPurpose, String receiptNo) {
        this.receivedOn = receivedOn;
        this.donorName = donorName;
        this.donorType = donorType;
        this.kind = kind;
        this.amount = amount;
        this.goodsDescription = goodsDescription;
        this.designatedPurpose = designatedPurpose;
        this.receiptNo = receiptNo;
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
