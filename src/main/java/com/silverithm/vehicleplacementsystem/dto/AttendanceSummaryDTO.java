package com.silverithm.vehicleplacementsystem.dto;

public record AttendanceSummaryDTO(
        long total,
        long present,
        long absent,
        long vacation
) {
}
