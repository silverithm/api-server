package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabCarePlanChange;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabCarePlanChangeRepository extends JpaRepository<LabCarePlanChange, Long> {

    List<LabCarePlanChange> findByCompanyIdAndElderlyIdOrderByChangedOnDescIdDesc(Long companyId, Long elderlyId);

    List<LabCarePlanChange> findByCompanyIdAndChangedOnBetweenOrderByChangedOnDescIdDesc(
            Long companyId, LocalDate from, LocalDate to);

    Optional<LabCarePlanChange> findByIdAndCompanyId(Long id, Long companyId);
}
