package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabVolunteerLog;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabVolunteerLogRepository extends JpaRepository<LabVolunteerLog, Long> {

    /** 날짜 범위(양 끝 포함), 날짜·시작 시각·id 오름차순 */
    List<LabVolunteerLog> findByCompanyIdAndActivityDateBetweenOrderByActivityDateAscStartTimeAscIdAsc(
            Long companyId, LocalDate from, LocalDate to);

    Optional<LabVolunteerLog> findByIdAndCompanyId(Long id, Long companyId);
}
