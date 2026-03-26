package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.AttendanceStatus;
import com.silverithm.vehicleplacementsystem.entity.EmployeeAttendance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EmployeeAttendanceRepository extends JpaRepository<EmployeeAttendance, Long> {

    List<EmployeeAttendance> findByCompanyIdAndDate(Long companyId, LocalDate date);

    long countByCompanyIdAndDateAndStatus(Long companyId, LocalDate date, AttendanceStatus status);

    Optional<EmployeeAttendance> findByMemberIdAndDate(Long memberId, LocalDate date);
}
