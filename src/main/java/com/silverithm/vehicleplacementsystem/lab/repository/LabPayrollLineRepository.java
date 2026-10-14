package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabPayrollLine;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 급여대장 저장소. 수정·삭제·확정은 행을 잠그고(SELECT ... FOR UPDATE) 읽는다 — 확정과 동시에 들어온 수정이
 * 확정 전 상태를 읽고 덮어써서 확정 행을 DRAFT로 되돌리는 일을 막기 위해서다. 모든 조회에 기관 조건이 있다.
 */
public interface LabPayrollLineRepository extends JpaRepository<LabPayrollLine, Long> {

    /** 월 범위(양 끝 포함), 월·id 순 */
    List<LabPayrollLine> findByCompanyIdAndPayMonthBetweenOrderByPayMonthAscIdAsc(Long companyId, String from,
                                                                               String to);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM LabPayrollLine l WHERE l.companyId = :companyId AND l.memberId = :memberId "
            + "AND l.payMonth = :payMonth")
    Optional<LabPayrollLine> findForUpdate(@Param("companyId") Long companyId, @Param("memberId") Long memberId,
                                           @Param("payMonth") String payMonth);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM LabPayrollLine l WHERE l.id = :id AND l.companyId = :companyId")
    Optional<LabPayrollLine> findForUpdateById(@Param("id") Long id, @Param("companyId") Long companyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM LabPayrollLine l WHERE l.companyId = :companyId AND l.payMonth = :payMonth "
            + "AND l.status = :status ORDER BY l.id")
    List<LabPayrollLine> findForUpdateByMonthAndStatus(@Param("companyId") Long companyId,
                                                       @Param("payMonth") String payMonth,
                                                       @Param("status") String status);
}
