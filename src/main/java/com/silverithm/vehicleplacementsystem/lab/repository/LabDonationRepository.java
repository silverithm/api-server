package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabDonation;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 모든 조회에 기관 조건이 있다 */
public interface LabDonationRepository extends JpaRepository<LabDonation, Long> {

    List<LabDonation> findByCompanyIdAndReceivedOnBetweenOrderByReceivedOnAscIdAsc(Long companyId, LocalDate from,
                                                                                LocalDate to);

    Optional<LabDonation> findByIdAndCompanyId(Long id, Long companyId);

    /**
     * 지출 연결·삭제가 같은 후원금을 동시에 건드리지 못하게 잠그고 읽는다 — 삭제 검사 직후 지출이
     * 새로 연결되는 틈을 막는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM LabDonation d WHERE d.id = :id AND d.companyId = :companyId")
    Optional<LabDonation> findForUpdate(@Param("id") Long id, @Param("companyId") Long companyId);

    /** 영수증 번호가 같은(대소문자 무시 — 운영 DB 정렬 규칙과 맞춘다) 후원금 */
    @Query("SELECT d FROM LabDonation d WHERE d.companyId = :companyId AND LOWER(d.receiptNo) = LOWER(:receiptNo)")
    List<LabDonation> findByReceiptNo(@Param("companyId") Long companyId, @Param("receiptNo") String receiptNo);
}
