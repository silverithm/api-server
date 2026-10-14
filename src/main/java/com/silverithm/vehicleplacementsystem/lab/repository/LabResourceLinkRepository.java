package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabResourceLink;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabResourceLinkRepository extends JpaRepository<LabResourceLink, Long> {

    List<LabResourceLink> findByCompanyIdAndLinkedOnBetweenOrderByLinkedOnDescIdDesc(
            Long companyId, LocalDate from, LocalDate to);

    List<LabResourceLink> findByCompanyIdAndElderlyIdOrderByLinkedOnDescIdDesc(Long companyId, Long elderlyId);

    List<LabResourceLink> findByCompanyIdAndElderlyIdAndLinkedOnBetweenOrderByLinkedOnDescIdDesc(
            Long companyId, Long elderlyId, LocalDate from, LocalDate to);

    Optional<LabResourceLink> findByIdAndCompanyId(Long id, Long companyId);
}
