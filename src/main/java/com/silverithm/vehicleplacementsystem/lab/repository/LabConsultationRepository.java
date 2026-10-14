package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabConsultation;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabConsultationRepository extends JpaRepository<LabConsultation, Long> {

    List<LabConsultation> findByCompanyIdAndElderlyIdOrderByConsultedOnDescIdDesc(Long companyId, Long elderlyId);

    List<LabConsultation> findByCompanyIdAndElderlyIdAndConsultedOnBetweenOrderByConsultedOnDescIdDesc(
            Long companyId, Long elderlyId, LocalDate from, LocalDate to);

    List<LabConsultation> findByCompanyIdAndConsultedOnBetweenOrderByConsultedOnDescIdDesc(
            Long companyId, LocalDate from, LocalDate to);

    Optional<LabConsultation> findByIdAndCompanyId(Long id, Long companyId);
}
