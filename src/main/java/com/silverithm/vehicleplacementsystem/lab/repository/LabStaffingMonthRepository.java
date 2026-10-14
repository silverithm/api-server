package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabStaffingMonth;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabStaffingMonthRepository extends JpaRepository<LabStaffingMonth, Long> {

    Optional<LabStaffingMonth> findByCompanyIdAndStaffingMonth(Long companyId, String staffingMonth);

    /** 'YYYY-' 접두로 한 해치 — 달 오름차순 */
    List<LabStaffingMonth> findByCompanyIdAndStaffingMonthStartingWithOrderByStaffingMonthAsc(
            Long companyId, String yearPrefix);

    Optional<LabStaffingMonth> findByIdAndCompanyId(Long id, Long companyId);
}
