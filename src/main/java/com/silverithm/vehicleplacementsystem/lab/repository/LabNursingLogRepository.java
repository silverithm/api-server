package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabNursingLog;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabNursingLogRepository extends JpaRepository<LabNursingLog, Long> {

    Optional<LabNursingLog> findByElderlyIdAndLogDate(Long elderlyId, LocalDate logDate);

    List<LabNursingLog> findByCompanyIdAndLogDate(Long companyId, LocalDate logDate);

    List<LabNursingLog> findByCompanyIdAndElderlyIdAndLogDateBetweenOrderByLogDateAsc(
            Long companyId, Long elderlyId, LocalDate from, LocalDate to);

    Optional<LabNursingLog> findByIdAndCompanyId(Long id, Long companyId);
}
