package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.ElderAttendance;
import com.silverithm.vehicleplacementsystem.entity.ElderAttendanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ElderAttendanceRepository extends JpaRepository<ElderAttendance, Long> {

    List<ElderAttendance> findByCompanyIdAndDate(Long companyId, LocalDate date);

    long countByCompanyIdAndDateAndStatus(Long companyId, LocalDate date, ElderAttendanceStatus status);

    Optional<ElderAttendance> findByElderlyIdAndDate(Long elderlyId, LocalDate date);
}
