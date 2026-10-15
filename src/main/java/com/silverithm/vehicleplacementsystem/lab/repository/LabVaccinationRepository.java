package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabVaccination;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabVaccinationRepository extends JpaRepository<LabVaccination, Long> {

    List<LabVaccination> findByCompanyIdAndSeasonYearOrderByElderlyIdAscVaccineAsc(Long companyId, Short seasonYear);

    List<LabVaccination> findByCompanyIdAndElderlyIdOrderBySeasonYearDescVaccineAsc(Long companyId, Long elderlyId);

    List<LabVaccination> findByCompanyIdAndElderlyIdAndSeasonYearOrderByVaccineAsc(
            Long companyId, Long elderlyId, Short seasonYear);

    Optional<LabVaccination> findByCompanyIdAndElderlyIdAndSeasonYearAndVaccine(
            Long companyId, Long elderlyId, Short seasonYear, String vaccine);

    Optional<LabVaccination> findByIdAndCompanyId(Long id, Long companyId);
}
