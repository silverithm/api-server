package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabMealPlan;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabMealPlanRepository extends JpaRepository<LabMealPlan, Long> {

    Optional<LabMealPlan> findByCompanyIdAndWeekStart(Long companyId, LocalDate weekStart);

    Optional<LabMealPlan> findByIdAndCompanyId(Long id, Long companyId);

    /** 시작 주와 끝 주 월요일 양 끝 포함, 주 오름차순 */
    List<LabMealPlan> findByCompanyIdAndWeekStartBetweenOrderByWeekStartAsc(Long companyId, LocalDate from,
                                                                            LocalDate to);
}
