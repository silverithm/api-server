package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.VacationEvent;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VacationEventRepository extends JpaRepository<VacationEvent, Long> {

    /** 조회 기간과 하루라도 겹치는 행사 */
    @Query("""
            SELECT e FROM VacationEvent e
            WHERE e.companyId = :companyId
              AND e.startDate <= :end
              AND e.endDate >= :start
            ORDER BY e.startDate ASC
            """)
    List<VacationEvent> findOverlapping(@Param("companyId") Long companyId,
                                        @Param("start") LocalDate start,
                                        @Param("end") LocalDate end);
}
