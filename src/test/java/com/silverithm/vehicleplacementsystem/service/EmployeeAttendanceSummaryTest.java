package com.silverithm.vehicleplacementsystem.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.silverithm.vehicleplacementsystem.dto.AttendanceSummaryDTO;
import com.silverithm.vehicleplacementsystem.entity.VacationRequest;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 대시보드 '오늘 직원 현황'.
 *
 * 하루를 통째로 쉬는 것은 연차만이 아니다. 일반·필수·대체휴무는 duration이 UNUSED로 저장되는데
 * (연차를 깎지 않는다는 뜻일 뿐 하루 쉬는 건 같다), 그걸 빼고 세는 바람에 실제로 여섯 명이
 * 쉬는 날 대시보드에 셋만 나왔다. 제보: "근무조정에 휴무로 등록된 선생님들이 휴무로 안 뜬다".
 */
class EmployeeAttendanceSummaryTest {

    private static final LocalDate 금요일 = LocalDate.of(2026, 9, 11);
    private static final LocalDate 일요일 = LocalDate.of(2026, 9, 13);

    private VacationRequest 휴무(String name, VacationRequest.VacationDuration duration,
                               VacationRequest.VacationStatus status) {
        VacationRequest request = new VacationRequest();
        request.setUserName(name);
        request.setDuration(duration.name());
        request.setStatus(status);
        return request;
    }

    private VacationRequest 승인된휴무(String name, VacationRequest.VacationDuration duration) {
        return 휴무(name, duration, VacationRequest.VacationStatus.APPROVED);
    }

    @Test
    @DisplayName("일반휴무(미사용)도 휴무로 센다 — 여기서 세 명이 빠졌다")
    void countsUnusedAsDayOff() {
        AttendanceSummaryDTO summary = AttendanceService.summarizeEmployees(28, List.of(
                승인된휴무("이수나", VacationRequest.VacationDuration.FULL_DAY),
                승인된휴무("조상권", VacationRequest.VacationDuration.UNUSED),
                승인된휴무("김효준", VacationRequest.VacationDuration.UNUSED)
        ), 금요일);

        assertThat(summary.vacation()).isEqualTo(3);
        assertThat(summary.present()).isEqualTo(25);
    }

    @Test
    @DisplayName("반차는 근무로 친다 — 반나절은 나온다")
    void halfDayCountsAsWorking() {
        AttendanceSummaryDTO summary = AttendanceService.summarizeEmployees(10, List.of(
                승인된휴무("오전반차", VacationRequest.VacationDuration.HALF_DAY_AM),
                승인된휴무("오후반차", VacationRequest.VacationDuration.HALF_DAY_PM)
        ), 금요일);

        assertThat(summary.vacation()).isZero();
        assertThat(summary.present()).isEqualTo(10);
    }

    @Test
    @DisplayName("승인되지 않은 신청은 세지 않는다")
    void ignoresUnapproved() {
        AttendanceSummaryDTO summary = AttendanceService.summarizeEmployees(10, List.of(
                휴무("대기중", VacationRequest.VacationDuration.UNUSED, VacationRequest.VacationStatus.PENDING),
                휴무("반려됨", VacationRequest.VacationDuration.FULL_DAY, VacationRequest.VacationStatus.REJECTED)
        ), 금요일);

        assertThat(summary.vacation()).isZero();
    }

    @Test
    @DisplayName("누가 쉬는지 이름을 함께 준다 — 숫자만 보고 근무조정 탭으로 넘어가지 않게")
    void listsWhoIsOff() {
        AttendanceSummaryDTO summary = AttendanceService.summarizeEmployees(10, List.of(
                승인된휴무("박소향", VacationRequest.VacationDuration.FULL_DAY),
                승인된휴무("강부옥", VacationRequest.VacationDuration.UNUSED)
        ), 금요일);

        assertThat(summary.vacationNames()).containsExactly("강부옥", "박소향");
    }

    @Test
    @DisplayName("같은 사람이 두 번 올라와도 한 명으로 센다")
    void countsPersonOnce() {
        AttendanceSummaryDTO summary = AttendanceService.summarizeEmployees(10, List.of(
                승인된휴무("김효준", VacationRequest.VacationDuration.UNUSED),
                승인된휴무("김효준", VacationRequest.VacationDuration.FULL_DAY)
        ), 금요일);

        assertThat(summary.vacation()).isEqualTo(1);
        assertThat(summary.vacationNames()).containsExactly("김효준");
    }

    @Test
    @DisplayName("일요일은 전원 휴무")
    void sundayIsAllOff() {
        AttendanceSummaryDTO summary = AttendanceService.summarizeEmployees(28, List.of(), 일요일);

        assertThat(summary.vacation()).isEqualTo(28);
        assertThat(summary.present()).isZero();
    }

    @Test
    @DisplayName("휴무자가 총원보다 많아도 총원을 넘지 않는다")
    void neverExceedsTotal() {
        AttendanceSummaryDTO summary = AttendanceService.summarizeEmployees(2, List.of(
                승인된휴무("가", VacationRequest.VacationDuration.UNUSED),
                승인된휴무("나", VacationRequest.VacationDuration.UNUSED),
                승인된휴무("다", VacationRequest.VacationDuration.UNUSED)
        ), 금요일);

        assertThat(summary.vacation()).isEqualTo(2);
        assertThat(summary.present()).isZero();
    }
}
