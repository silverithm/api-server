package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabPayrollProfile;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 모든 조회에 기관 조건이 있다 */
public interface LabPayrollProfileRepository extends JpaRepository<LabPayrollProfile, Long> {

    List<LabPayrollProfile> findByCompanyIdOrderByIdAsc(Long companyId);

    Optional<LabPayrollProfile> findByCompanyIdAndMemberId(Long companyId, Long memberId);

    Optional<LabPayrollProfile> findByIdAndCompanyId(Long id, Long companyId);
}
