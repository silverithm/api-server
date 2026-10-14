package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabMedicalVisit;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabMedicalVisitRepository extends JpaRepository<LabMedicalVisit, Long> {

    List<LabMedicalVisit> findByCompanyIdAndVisitDateBetweenOrderByVisitDateDescIdDesc(
            Long companyId, LocalDate from, LocalDate to);

    List<LabMedicalVisit> findByCompanyIdAndElderlyIdOrderByVisitDateDescIdDesc(Long companyId, Long elderlyId);

    /** 후속 확인일이 지났거나 오늘인데 아직 안 한 것. follow_up_on이 비면 비교가 거짓이라 자연히 빠진다 */
    List<LabMedicalVisit> findByCompanyIdAndFollowUpDoneFalseAndFollowUpOnLessThanEqualOrderByVisitDateDescIdDesc(
            Long companyId, LocalDate today);

    Optional<LabMedicalVisit> findByIdAndCompanyId(Long id, Long companyId);
}
