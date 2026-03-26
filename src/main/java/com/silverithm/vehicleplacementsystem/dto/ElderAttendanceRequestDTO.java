package com.silverithm.vehicleplacementsystem.dto;

public record ElderAttendanceRequestDTO(
        Long elderlyId,
        String status,
        String note
) {
}
