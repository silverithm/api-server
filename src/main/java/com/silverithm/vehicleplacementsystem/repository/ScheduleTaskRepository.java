package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.ScheduleTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ScheduleTaskRepository extends JpaRepository<ScheduleTask, Long> {

    List<ScheduleTask> findByScheduleIdOrderBySortOrderAscIdAsc(Long scheduleId);

    long countByScheduleId(Long scheduleId);

    long countByScheduleIdAndIsCompletedTrue(Long scheduleId);

    void deleteByScheduleId(Long scheduleId);

    /** 특정 담당자의 할 일 (회사 범위 + 기간 필터) */
    @Query("SELECT t FROM ScheduleTask t JOIN t.schedule s "
            + "WHERE s.company.id = :companyId AND t.assigneeMemberId = :memberId "
            + "AND (:startDate IS NULL OR s.endDate >= :startDate) "
            + "AND (:endDate IS NULL OR s.startDate <= :endDate) "
            + "ORDER BY t.isCompleted ASC, s.startDate ASC, t.sortOrder ASC")
    List<ScheduleTask> findByAssignee(@Param("companyId") Long companyId,
                                      @Param("memberId") Long memberId,
                                      @Param("startDate") LocalDate startDate,
                                      @Param("endDate") LocalDate endDate);

    /** 일정 종료일이 지났는데 아직 완료되지 않은 할 일 (미완료 알림용) */
    @Query("SELECT t FROM ScheduleTask t JOIN t.schedule s "
            + "WHERE t.isCompleted = false AND t.assigneeMemberId IS NOT NULL "
            + "AND s.endDate = :endDate")
    List<ScheduleTask> findOverdueByScheduleEndDate(@Param("endDate") LocalDate endDate);
}
