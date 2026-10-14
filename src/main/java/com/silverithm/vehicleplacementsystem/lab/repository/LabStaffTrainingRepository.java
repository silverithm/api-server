package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabStaffTraining;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabStaffTrainingRepository extends JpaRepository<LabStaffTraining, Long> {

    List<LabStaffTraining> findByCompanyIdAndHeldOnBetweenOrderByHeldOnAscIdAsc(
            Long companyId, LocalDate from, LocalDate to);

    Optional<LabStaffTraining> findByIdAndCompanyId(Long id, Long companyId);
}
