package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabBonusFund;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabBonusFundRepository extends JpaRepository<LabBonusFund, Long> {

    List<LabBonusFund> findByCompanyIdAndOccurredOnBetweenOrderByOccurredOnDescIdDesc(
            Long companyId, LocalDate from, LocalDate to);

    Optional<LabBonusFund> findByIdAndCompanyId(Long id, Long companyId);
}
