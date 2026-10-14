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
 * 수급자 서류함의 서류 1건 — 어떤 서류가 언제 발급돼 언제 만료되고 어디 보관되는지의 색인이다.
 *
 * <p>파일 자체가 아니라 위치·날짜만 담는다. 제목·위치는 목록과 점검표에 그대로 나가는 평문이라
 * 개인정보를 적지 않도록 화면이 안내한다.
 */
@Entity
@Table(name = "lab_elder_documents", indexes = @Index(
        name = "idx_lab_elder_documents_elder_type", columnList = "elderly_id, doc_type"))
@Getter
@NoArgsConstructor
public class LabElderDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "elderly_id", nullable = false)
    private Long elderlyId;

    @Column(name = "doc_type", nullable = false, length = 20)
    private String docType;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "issued_on")
    private LocalDate issuedOn;

    @Column(name = "expires_on")
    private LocalDate expiresOn;

    @Column(name = "location", length = 100)
    private String location;

    @Column(name = "checked_on")
    private LocalDate checkedOn;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public LabElderDocument(Long companyId) {
        this.companyId = companyId;
    }

    public void fill(Long elderlyId, String docType, String title, LocalDate issuedOn, LocalDate expiresOn,
                     String location, LocalDate checkedOn, String note) {
        this.elderlyId = elderlyId;
        this.docType = docType;
        this.title = title;
        this.issuedOn = issuedOn;
        this.expiresOn = expiresOn;
        this.location = location;
        this.checkedOn = checkedOn;
        this.note = note;
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
