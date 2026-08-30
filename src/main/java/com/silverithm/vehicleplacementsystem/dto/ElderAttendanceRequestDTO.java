package com.silverithm.vehicleplacementsystem.dto;

import java.time.LocalDate;

/**
 * 어르신 출결 체크 요청.
 * date가 비어 있으면 오늘로 본다(구버전 클라이언트 호환).
 * personalPickup/personalDropoff도 비어 있으면 false로 본다.
 */
public record ElderAttendanceRequestDTO(
        Long elderlyId,
        LocalDate date,
        String status,
        Boolean personalPickup,
        Boolean personalDropoff,
        String note
) {
    public LocalDate dateOrToday() {
        return date != null ? date : LocalDate.now();
    }

    public boolean personalPickupOrFalse() {
        return Boolean.TRUE.equals(personalPickup);
    }

    public boolean personalDropoffOrFalse() {
        return Boolean.TRUE.equals(personalDropoff);
    }
}
