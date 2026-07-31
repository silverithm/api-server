package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.PlazaReport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlazaReportRepository extends JpaRepository<PlazaReport, Long> {

    boolean existsByTargetTypeAndTargetIdAndReporterId(PlazaReport.TargetType targetType, Long targetId, String reporterId);

    long countByTargetTypeAndTargetId(PlazaReport.TargetType targetType, Long targetId);
}
