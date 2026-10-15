package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabFacilityProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabFacilityProfileRepository extends JpaRepository<LabFacilityProfile, Long> {

    Optional<LabFacilityProfile> findByCompanyId(Long companyId);
}
