package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabLedgerEntry;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 모든 조회에 기관 조건이 있다 */
public interface LabLedgerEntryRepository extends JpaRepository<LabLedgerEntry, Long> {

    /** 날짜 범위(양 끝 포함), 날짜·id 순 */
    List<LabLedgerEntry> findByCompanyIdAndEntryDateBetweenOrderByEntryDateAscIdAsc(Long companyId, LocalDate from,
                                                                                  LocalDate to);

    Optional<LabLedgerEntry> findByIdAndCompanyId(Long id, Long companyId);

    /** 후원금 여러 건에 연결된 지출 합을 한 번의 쿼리로(후원금마다 따로 묻지 않는다) */
    @Query("SELECT l.donationId AS donationId, SUM(l.amount) AS total FROM LabLedgerEntry l "
            + "WHERE l.companyId = :companyId AND l.direction = 'EXPENSE' AND l.donationId IN :ids "
            + "GROUP BY l.donationId")
    List<UsedRow> sumExpensesByDonationIds(@Param("companyId") Long companyId, @Param("ids") Collection<Long> ids);

    boolean existsByCompanyIdAndDonationIdAndDirection(Long companyId, Long donationId, String direction);

    interface UsedRow {
        Long getDonationId();

        Long getTotal();
    }
}
