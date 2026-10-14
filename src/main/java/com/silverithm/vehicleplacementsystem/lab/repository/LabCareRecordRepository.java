package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabCareRecord;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabCareRecordRepository extends JpaRepository<LabCareRecord, Long> {

    Optional<LabCareRecord> findByElderlyIdAndRecordDate(Long elderlyId, LocalDate recordDate);

    List<LabCareRecord> findByCompanyIdAndRecordDate(Long companyId, LocalDate recordDate);

    List<LabCareRecord> findByCompanyIdAndElderlyIdAndRecordDateBetweenOrderByRecordDateAsc(
            Long companyId, Long elderlyId, LocalDate from, LocalDate to);

    Optional<LabCareRecord> findByIdAndCompanyId(Long id, Long companyId);
}
