package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabBusinessPlan;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabBusinessPlanRepository extends JpaRepository<LabBusinessPlan, Long> {

    Optional<LabBusinessPlan> findByCompanyIdAndPlanYear(Long companyId, short planYear);

    Optional<LabBusinessPlan> findByIdAndCompanyId(Long id, Long companyId);
}
