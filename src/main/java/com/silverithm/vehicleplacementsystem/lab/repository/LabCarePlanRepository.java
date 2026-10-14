package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabCarePlan;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabCarePlanRepository extends JpaRepository<LabCarePlan, Long> {

    Optional<LabCarePlan> findByElderlyIdAndPlanYear(Long elderlyId, Short planYear);

    List<LabCarePlan> findByCompanyIdAndPlanYear(Long companyId, Short planYear);

    Optional<LabCarePlan> findByIdAndCompanyId(Long id, Long companyId);
}
