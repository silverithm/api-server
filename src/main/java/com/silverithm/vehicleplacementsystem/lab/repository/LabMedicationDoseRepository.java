package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabMedicationDose;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabMedicationDoseRepository extends JpaRepository<LabMedicationDose, Long> {

    Optional<LabMedicationDose> findByIdAndCompanyId(Long id, Long companyId);

    Optional<LabMedicationDose> findByRequestIdAndGivenOnAndSlot(Long requestId, LocalDate givenOn, String slot);

    boolean existsByRequestId(Long requestId);

    List<LabMedicationDose> findByRequestId(Long requestId);

    List<LabMedicationDose> findByCompanyIdAndGivenOnOrderByElderlyIdAscIdAsc(Long companyId, LocalDate givenOn);

    List<LabMedicationDose> findByCompanyIdAndElderlyIdAndGivenOnBetweenOrderByGivenOnAscIdAsc(Long companyId,
                                                                                             Long elderlyId,
                                                                                             LocalDate from,
                                                                                             LocalDate to);
}
