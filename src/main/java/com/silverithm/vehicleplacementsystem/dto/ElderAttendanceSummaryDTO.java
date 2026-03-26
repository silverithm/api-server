package com.silverithm.vehicleplacementsystem.dto;

public record ElderAttendanceSummaryDTO(
        long total,
        long present,
        long absent
) {
}
