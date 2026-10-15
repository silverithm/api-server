package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabPolicyDoc;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabPolicyDocRepository extends JpaRepository<LabPolicyDoc, Long> {

    List<LabPolicyDoc> findByCompanyIdOrderByKindAscGuideNoAscIdAsc(Long companyId);

    List<LabPolicyDoc> findByCompanyIdAndKind(Long companyId, String kind);

    Optional<LabPolicyDoc> findByIdAndCompanyId(Long id, Long companyId);
}
