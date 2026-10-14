package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabProgramPlan;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabProgramPlanRepository extends JpaRepository<LabProgramPlan, Long> {

    List<LabProgramPlan> findByCompanyIdAndPlanYearOrderByCategoryAsc(Long companyId, short planYear);

    Optional<LabProgramPlan> findByCompanyIdAndPlanYearAndCategory(Long companyId, short planYear, String category);

    Optional<LabProgramPlan> findByIdAndCompanyId(Long id, Long companyId);
}
