package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabRecordLog;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabRecordLogRepository extends JpaRepository<LabRecordLog, Long> {

    List<LabRecordLog> findByCompanyIdAndKindAndEntryDateBetweenOrderByEntryDateDescIdDesc(
            Long companyId, String kind, LocalDate from, LocalDate to);

    List<LabRecordLog> findByCompanyIdAndKindAndElderlyIdOrderByEntryDateDescIdDesc(
            Long companyId, String kind, Long elderlyId);

    Optional<LabRecordLog> findByIdAndCompanyId(Long id, Long companyId);
}
