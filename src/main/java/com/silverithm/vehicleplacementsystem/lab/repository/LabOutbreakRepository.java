package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabOutbreak;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabOutbreakRepository extends JpaRepository<LabOutbreak, Long> {

    List<LabOutbreak> findByCompanyIdAndStartedOnBetweenOrderByStartedOnDescIdDesc(
            Long companyId, LocalDate from, LocalDate to);

    Optional<LabOutbreak> findByIdAndCompanyId(Long id, Long companyId);
}
