package com.silverithm.vehicleplacementsystem;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.silverithm.vehicleplacementsystem.dto.AttendanceSummaryDTO;
import com.silverithm.vehicleplacementsystem.entity.VacationRequest;
import com.silverithm.vehicleplacementsystem.service.AttendanceService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 대시보드 "오늘 현황"의 직원 숫자.
 *
 * 출근 기록을 쓰는 화면이 없어서 기록 기반으로는 영원히 근무 0·휴무 0이었다.
 * 지금은 승인된 휴가로 휴무를 세고, 나머지는 근무로 본다.
 */
class AttendanceSummaryTest {

    private static final LocalDate SAT = LocalDate.of(2026, 9, 5);
    private static final LocalDate SUN = LocalDate.of(2026, 9, 6);

    private static VacationRequest vacation(String name, VacationRequest.VacationStatus status, String duration) {
        return VacationRequest.builder()
                .userName(name)
                .date(LocalDate.of(2026, 9, 5))
                .status(status)
                .duration(duration)
                .build();
    }

    @Test
    @DisplayName("휴가 신청이 없으면 전원 근무다")
    void noVacationMeansEveryoneWorks() {
        AttendanceSummaryDTO s = AttendanceService.summarizeEmployees(28, List.of(), SAT);
        assertEquals(28, s.total());
        assertEquals(28, s.present());
        assertEquals(0, s.vacation());
    }

    @Test
    @DisplayName("승인된 종일 휴가만 휴무로 센다 — 대기·반려·반차는 근무")
    void onlyApprovedFullDayCountsAsVacation() {
        List<VacationRequest> vacations = List.of(
                vacation("김철수", VacationRequest.VacationStatus.APPROVED, "FULL_DAY"),
                vacation("이영희", VacationRequest.VacationStatus.PENDING, "FULL_DAY"),
                vacation("박민수", VacationRequest.VacationStatus.REJECTED, "FULL_DAY"),
                vacation("최지우", VacationRequest.VacationStatus.APPROVED, "HALF_DAY_AM"));

        AttendanceSummaryDTO s = AttendanceService.summarizeEmployees(10, vacations, SAT);
        assertEquals(1, s.vacation());
        assertEquals(9, s.present());
        assertEquals(0, s.absent());
    }

    @Test
    @DisplayName("같은 사람의 중복 신청은 한 번만 센다")
    void duplicateRequestsCountOnce() {
        List<VacationRequest> vacations = List.of(
                vacation("김철수", VacationRequest.VacationStatus.APPROVED, "FULL_DAY"),
                vacation("김철수", VacationRequest.VacationStatus.APPROVED, "FULL_DAY"));

        assertEquals(1, AttendanceService.summarizeEmployees(5, vacations, SAT).vacation());
    }

    @Test
    @DisplayName("레거시 duration 값이 비어 있어도 종일로 본다")
    void legacyDurationFallsBackToFullDay() {
        List<VacationRequest> vacations = List.of(
                vacation("김철수", VacationRequest.VacationStatus.APPROVED, "이상한값"));

        assertEquals(1, AttendanceService.summarizeEmployees(5, vacations, SAT).vacation());
    }

    @Test
    @DisplayName("퇴사자의 휴가가 남아 있어도 근무가 음수로 가지 않는다")
    void vacationNeverExceedsTotal() {
        List<VacationRequest> vacations = List.of(
                vacation("a", VacationRequest.VacationStatus.APPROVED, "FULL_DAY"),
                vacation("b", VacationRequest.VacationStatus.APPROVED, "FULL_DAY"));

        AttendanceSummaryDTO s = AttendanceService.summarizeEmployees(1, vacations, SAT);
        assertEquals(1, s.vacation());
        assertEquals(0, s.present());
    }

    @Test
    @DisplayName("일요일은 근무하지 않는다 — 휴가 신청과 무관하게 전원 휴무")
    void sundayNobodyWorks() {
        AttendanceSummaryDTO s = AttendanceService.summarizeEmployees(28, List.of(), SUN);
        assertEquals(0, s.present());
        assertEquals(28, s.vacation());
    }
}
