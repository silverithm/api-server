package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabStaffProfile;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabStaffProfileRepository extends JpaRepository<LabStaffProfile, Long> {

    List<LabStaffProfile> findByCompanyIdOrderByIdAsc(Long companyId);

    Optional<LabStaffProfile> findByCompanyIdAndMemberId(Long companyId, Long memberId);

    Optional<LabStaffProfile> findByIdAndCompanyId(Long id, Long companyId);
}
