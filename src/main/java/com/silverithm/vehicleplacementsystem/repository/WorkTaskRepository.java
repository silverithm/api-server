package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.WorkTask;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkTaskRepository extends JpaRepository<WorkTask, Long> {

    Optional<WorkTask> findByIdAndCompanyId(Long id, Long companyId);

    /** 기관 목록 — 진행 중인 것 전부와 [since] 뒤에 끝낸 것 */
    @EntityGraph(attributePaths = "assignees")
    @Query("SELECT DISTINCT t FROM WorkTask t WHERE t.company.id = :companyId"
            + " AND (t.completedAt IS NULL OR t.completedAt >= :since)")
    List<WorkTask> findVisible(@Param("companyId") Long companyId, @Param("since") LocalDateTime since);

    /** 매일 알림 대상 — 모든 기관의 진행 중인 업무 */
    @EntityGraph(attributePaths = {"assignees", "company"})
    @Query("SELECT DISTINCT t FROM WorkTask t WHERE t.completedAt IS NULL")
    List<WorkTask> findAllInProgress();
}
