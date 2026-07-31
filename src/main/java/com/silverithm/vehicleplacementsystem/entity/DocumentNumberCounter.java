package com.silverithm.vehicleplacementsystem.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 전자결재 문서번호 채번 (회사·연도별).
 * INSERT IGNORE로 행 존재를 보장한 뒤 SELECT ... FOR UPDATE로 잠그고 seq를 올려 발급한다.
 */
@Entity
@Table(name = "document_number_counters")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentNumberCounter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "year", nullable = false)
    private Integer year;

    @Column(nullable = false)
    private Integer seq;
}
