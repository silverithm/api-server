package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabSafetyCheck;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabSafetyCheckRepository extends JpaRepository<LabSafetyCheck, Long> {

    Optional<LabSafetyCheck> findByCompanyIdAndKindAndCheckDate(Long companyId, String kind, LocalDate checkDate);

    Optional<LabSafetyCheck> findByIdAndCompanyId(Long id, Long companyId);

    List<LabSafetyCheck> findByCompanyIdAndCheckDateBetweenOrderByCheckDateAscKindAscIdAsc(Long companyId,
                                                                                         LocalDate from,
                                                                                         LocalDate to);

    List<LabSafetyCheck> findByCompanyIdAndKindAndCheckDateBetweenOrderByCheckDateAscIdAsc(Long companyId,
                                                                                         String kind,
                                                                                         LocalDate from,
                                                                                         LocalDate to);
}
