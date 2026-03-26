package com.silverithm.vehicleplacementsystem.dto;

public record EmployeeAttendanceRequestDTO(
        Long memberId,
        String status,
        String note
) {
}
