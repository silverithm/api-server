package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.ElderAttendance;

public record ElderAttendanceDTO(
        Long id,
        Long elderlyId,
        String elderlyName,
        String status,
        String note
) {
    public static ElderAttendanceDTO from(ElderAttendance attendance) {
        return new ElderAttendanceDTO(
                attendance.getId(),
                attendance.getElderly().getId(),
                attendance.getElderly().getName(),
                attendance.getStatus().name(),
                attendance.getNote()
        );
    }
}
