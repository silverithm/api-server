package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabCopayBill;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LabCopayBillRepository extends JpaRepository<LabCopayBill, Long> {

    Optional<LabCopayBill> findByElderlyIdAndBillMonth(Long elderlyId, String billMonth);

    List<LabCopayBill> findByCompanyIdAndBillMonth(Long companyId, String billMonth);

    /** 한 해치 — bill_month가 'YYYY-MM' 고정 폭 문자열이라 사전순 범위가 곧 월 범위다(양 끝 포함) */
    List<LabCopayBill> findByCompanyIdAndBillMonthBetween(Long companyId, String fromMonth, String toMonth);

    Optional<LabCopayBill> findByIdAndCompanyId(Long id, Long companyId);

    /**
     * 행을 잠그고 읽는다. 수납은 읽고-더하고-쓰는 갱신이라, 두 직원이 동시에 입금 처리하면
     * 한쪽 금액이 사라질 수 있다 — 같은 청구서의 갱신을 직렬화한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM LabCopayBill b WHERE b.id = :id AND b.companyId = :companyId")
    Optional<LabCopayBill> findLockedByIdAndCompanyId(@Param("id") Long id, @Param("companyId") Long companyId);
}
