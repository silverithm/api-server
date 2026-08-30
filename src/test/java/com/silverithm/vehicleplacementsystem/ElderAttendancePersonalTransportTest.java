package com.silverithm.vehicleplacementsystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.silverithm.vehicleplacementsystem.dto.ElderAttendanceRequestDTO;
import com.silverithm.vehicleplacementsystem.entity.ElderAttendance;
import com.silverithm.vehicleplacementsystem.entity.ElderAttendanceStatus;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 어르신 출결의 개인등하원 규칙.
 *
 * 결석과 개인등하원은 별개다 — "개인등원하고 차량으로 하원"이 실제로 있어서
 * status 하나로 합칠 수 없다. 그 구분이 엔티티·요청 DTO에서 지켜지는지 본다.
 */
class ElderAttendancePersonalTransportTest {

    @Test
    @DisplayName("새 출결은 개인등하원 없이 시작한다")
    void defaultsToNoPersonalTransport() {
        ElderAttendance attendance =
                new ElderAttendance(null, null, LocalDate.of(2026, 8, 31), ElderAttendanceStatus.PRESENT);

        assertFalse(attendance.isPersonalPickup());
        assertFalse(attendance.isPersonalDropoff());
    }

    @Test
    @DisplayName("개인등원만 켜도 하원은 그대로 차량을 탄다")
    void pickupOnlyLeavesDropoffAlone() {
        ElderAttendance attendance =
                new ElderAttendance(null, null, LocalDate.of(2026, 8, 31), ElderAttendanceStatus.PRESENT);

        attendance.updatePersonalTransport(true, false);

        assertTrue(attendance.isPersonalPickup());
        assertFalse(attendance.isPersonalDropoff());
        // 개인등원은 결석이 아니다 — 센터에는 오신다
        assertEquals(ElderAttendanceStatus.PRESENT, attendance.getStatus());
    }

    @Test
    @DisplayName("개인등하원을 껐다 켜는 것이 서로를 덮어쓰지 않는다")
    void togglingOneKeepsTheOther() {
        ElderAttendance attendance =
                new ElderAttendance(null, null, LocalDate.of(2026, 8, 31), ElderAttendanceStatus.PRESENT);

        attendance.updatePersonalTransport(true, true);
        attendance.updatePersonalTransport(false, true);

        assertFalse(attendance.isPersonalPickup());
        assertTrue(attendance.isPersonalDropoff());
    }

    @Test
    @DisplayName("date를 담아 보내면 그날에 기록한다")
    void requestKeepsGivenDate() {
        ElderAttendanceRequestDTO request = new ElderAttendanceRequestDTO(
                1L, LocalDate.of(2026, 8, 20), "ABSENT", true, false, null);

        assertEquals(LocalDate.of(2026, 8, 20), request.dateOrToday());
        assertTrue(request.personalPickupOrFalse());
        assertFalse(request.personalDropoffOrFalse());
    }

    @Test
    @DisplayName("date가 비어 있으면 오늘로 본다 — 구버전 앱이 그렇게 보낸다")
    void requestWithoutDateFallsBackToToday() {
        ElderAttendanceRequestDTO request =
                new ElderAttendanceRequestDTO(1L, null, "PRESENT", null, null, null);

        assertEquals(LocalDate.now(), request.dateOrToday());
        // 새 필드를 모르는 클라이언트가 보내지 않아도 false로 읽힌다
        assertFalse(request.personalPickupOrFalse());
        assertFalse(request.personalDropoffOrFalse());
    }
}
