package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.ElderAttendance;
import com.silverithm.vehicleplacementsystem.entity.ElderAttendanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ElderAttendanceRepository extends JpaRepository<ElderAttendance, Long> {

    List<ElderAttendance> findByCompanyIdAndDate(Long companyId, LocalDate date);

    List<ElderAttendance> findByCompanyIdAndDateBetween(Long companyId, LocalDate startDate, LocalDate endDate);

    long countByCompanyIdAndDateAndStatus(Long companyId, LocalDate date, ElderAttendanceStatus status);

    long countByCompanyIdAndDateAndPersonalPickupTrue(Long companyId, LocalDate date);

    long countByCompanyIdAndDateAndPersonalDropoffTrue(Long companyId, LocalDate date);

    Optional<ElderAttendance> findByElderlyIdAndDate(Long elderlyId, LocalDate date);
}
