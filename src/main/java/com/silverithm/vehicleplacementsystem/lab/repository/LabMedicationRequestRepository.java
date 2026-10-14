package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabMedicationRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabMedicationRequestRepository extends JpaRepository<LabMedicationRequest, Long> {

    Optional<LabMedicationRequest> findByIdAndCompanyId(Long id, Long companyId);

    List<LabMedicationRequest> findByCompanyIdOrderByStartDateDescIdDesc(Long companyId);

    List<LabMedicationRequest> findByCompanyIdAndStatusOrderByStartDateDescIdDesc(Long companyId, String status);

    List<LabMedicationRequest> findByCompanyIdAndElderlyIdOrderByStartDateDescIdDesc(Long companyId, Long elderlyId);

    List<LabMedicationRequest> findByCompanyIdAndElderlyIdAndStatusOrderByStartDateDescIdDesc(Long companyId,
                                                                                            Long elderlyId,
                                                                                            String status);
}
