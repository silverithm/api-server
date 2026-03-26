package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.EmployeeAttendance;

import java.time.LocalTime;

public record EmployeeAttendanceDTO(
        Long id,
        Long memberId,
        String memberName,
        String status,
        LocalTime checkInTime,
        LocalTime checkOutTime,
        String note
) {
    public static EmployeeAttendanceDTO from(EmployeeAttendance attendance) {
        return new EmployeeAttendanceDTO(
                attendance.getId(),
                attendance.getMember().getId(),
                attendance.getMember().getName(),
                attendance.getStatus().name(),
                attendance.getCheckInTime(),
                attendance.getCheckOutTime(),
                attendance.getNote()
        );
    }
}
