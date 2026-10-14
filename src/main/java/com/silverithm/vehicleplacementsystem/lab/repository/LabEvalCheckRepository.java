package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabEvalCheck;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabEvalCheckRepository extends JpaRepository<LabEvalCheck, Long> {

    List<LabEvalCheck> findByCompanyIdOrderByIndicatorCodeAsc(Long companyId);

    Optional<LabEvalCheck> findByCompanyIdAndIndicatorCode(Long companyId, String indicatorCode);
}
