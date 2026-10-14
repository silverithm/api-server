package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabGuardianNotice;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabGuardianNoticeRepository extends JpaRepository<LabGuardianNotice, Long> {

    List<LabGuardianNotice> findByCompanyIdAndNoticeMonthOrderByIdAsc(Long companyId, String noticeMonth);

    Optional<LabGuardianNotice> findByElderlyIdAndNoticeMonth(Long elderlyId, String noticeMonth);

    Optional<LabGuardianNotice> findByIdAndCompanyId(Long id, Long companyId);
}
