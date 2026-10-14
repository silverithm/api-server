package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabRehabSession;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabRehabSessionRepository extends JpaRepository<LabRehabSession, Long> {

    /** 날짜 범위(양 끝 포함), 날짜·id 오름차순 */
    List<LabRehabSession> findByCompanyIdAndSessionDateBetweenOrderBySessionDateAscIdAsc(
            Long companyId, LocalDate from, LocalDate to);

    List<LabRehabSession> findByCompanyIdAndElderlyIdAndSessionDateBetweenOrderBySessionDateAscIdAsc(
            Long companyId, Long elderlyId, LocalDate from, LocalDate to);

    Optional<LabRehabSession> findByIdAndCompanyId(Long id, Long companyId);
}
