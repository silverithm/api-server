package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.*;
import com.silverithm.vehicleplacementsystem.entity.*;
import com.silverithm.vehicleplacementsystem.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final EmployeeAttendanceRepository employeeAttendanceRepository;
    private final ElderAttendanceRepository elderAttendanceRepository;
    private final MemberRepository memberRepository;
    private final ElderRepository elderRepository;
    private final CompanyRepository companyRepository;
    private final ResourceScopeGuard resourceScopeGuard;
    private final VacationRequestRepository vacationRequestRepository;

    // ==================== 직원 출석 ====================

    /**
     * 직원 오늘 현황. 휴무는 승인된 휴가(근무조정)에서 센다 — 예전엔 employee_attendance 기록을
     * 봤는데, 그 기록을 쓰는 화면이 앱에도 웹에도 없어서 근무 0·휴무 0으로만 나왔다.
     * 반차는 근무로 친다(반나절은 나온다). 결근은 따로 기록되지 않으므로 0이다.
     */
    public AttendanceSummaryDTO getEmployeeAttendanceSummary(Long companyId, LocalDate date) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회사입니다: " + companyId));

        long total = memberRepository.countByCompanyAndStatus(company, Member.MemberStatus.ACTIVE);
        List<VacationRequest> vacations = vacationRequestRepository.findByCompanyAndDate(company, date);
        return summarizeEmployees(total, vacations);
    }

    public static AttendanceSummaryDTO summarizeEmployees(long total, List<VacationRequest> vacations) {
        long vacation = vacations.stream()
                .filter(v -> v.getStatus() == VacationRequest.VacationStatus.APPROVED)
                .filter(v -> v.getDurationEnum() == VacationRequest.VacationDuration.FULL_DAY)
                .map(VacationRequest::getUserName)
                .distinct()
                .count();
        if (vacation > total) vacation = total;
        return new AttendanceSummaryDTO(total, total - vacation, 0, vacation);
    }

    public List<EmployeeAttendanceDTO> getEmployeeAttendanceList(Long companyId, LocalDate date) {
        return employeeAttendanceRepository.findByCompanyIdAndDate(companyId, date)
                .stream()
                .map(EmployeeAttendanceDTO::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public void checkEmployeeAttendance(Long companyId, EmployeeAttendanceRequestDTO request) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회사입니다: " + companyId));
        Member member = memberRepository.findById(request.memberId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 직원입니다: " + request.memberId()));
        resourceScopeGuard.requireSameCompany(member.getCompany());

        LocalDate today = LocalDate.now();
        AttendanceStatus status = AttendanceStatus.valueOf(request.status());

        EmployeeAttendance attendance = employeeAttendanceRepository
                .findByMemberIdAndDate(request.memberId(), today)
                .orElse(null);

        if (attendance != null) {
            attendance.updateStatus(status);
            if (request.note() != null) attendance.updateNote(request.note());
            if (status == AttendanceStatus.PRESENT) attendance.updateCheckInTime(LocalTime.now());
        } else {
            attendance = new EmployeeAttendance(member, company, today, status);
            if (request.note() != null) attendance.updateNote(request.note());
            if (status == AttendanceStatus.PRESENT) attendance.updateCheckInTime(LocalTime.now());
            employeeAttendanceRepository.save(attendance);
        }
    }

    @Transactional
    public void bulkCheckEmployeeAttendance(Long companyId, List<EmployeeAttendanceRequestDTO> requests) {
        for (EmployeeAttendanceRequestDTO request : requests) {
            checkEmployeeAttendance(companyId, request);
        }
    }

    // ==================== 어르신 출석 ====================

    /**
     * 어르신 오늘 현황. 현장은 결석만 표시하고 나머지는 나오는 걸로 본다 — 앱 배차 화면이 그렇게 쓴다.
     * 그래서 결석은 ABSENT 기록 수, 출석은 총원에서 결석을 뺀 값이다. 예전엔 PRESENT 기록이 없으면
     * 전원 결석으로 나왔다.
     */
    public ElderAttendanceSummaryDTO getElderAttendanceSummary(Long companyId, LocalDate date) {
        long total = elderRepository.countByCompanyId(companyId);
        long absent = elderAttendanceRepository.countByCompanyIdAndDateAndStatus(companyId, date, ElderAttendanceStatus.ABSENT);
        if (absent > total) absent = total;
        long present = total - absent;
        long personalPickup = elderAttendanceRepository.countByCompanyIdAndDateAndPersonalPickupTrue(companyId, date);
        long personalDropoff = elderAttendanceRepository.countByCompanyIdAndDateAndPersonalDropoffTrue(companyId, date);

        return new ElderAttendanceSummaryDTO(total, present, absent, personalPickup, personalDropoff);
    }

    public List<ElderAttendanceDTO> getElderAttendanceList(Long companyId, LocalDate date) {
        return elderAttendanceRepository.findByCompanyIdAndDate(companyId, date)
                .stream()
                .map(ElderAttendanceDTO::from)
                .collect(Collectors.toList());
    }

    /**
     * 기간 조회 - 배차 캘린더/리스트가 월 단위로 출결을 읽는다.
     * 날짜별로 한 건씩 호출하면 30번 왕복하게 되므로 범위로 한 번에 준다.
     */
    public List<ElderAttendanceDTO> getElderAttendanceRange(Long companyId, LocalDate startDate, LocalDate endDate) {
        return elderAttendanceRepository.findByCompanyIdAndDateBetween(companyId, startDate, endDate)
                .stream()
                .map(ElderAttendanceDTO::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public void checkElderAttendance(Long companyId, ElderAttendanceRequestDTO request) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회사입니다: " + companyId));
        Elderly elderly = elderRepository.findById(request.elderlyId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 어르신입니다: " + request.elderlyId()));
        resourceScopeGuard.requireSameCompany(elderly.getCompany(),
                elderly.getUser() != null ? elderly.getUser().getCompany() : null);

        LocalDate targetDate = request.dateOrToday();
        ElderAttendanceStatus status = ElderAttendanceStatus.valueOf(request.status());

        ElderAttendance attendance = elderAttendanceRepository
                .findByElderlyIdAndDate(request.elderlyId(), targetDate)
                .orElse(null);

        if (attendance != null) {
            attendance.updateStatus(status);
            attendance.updatePersonalTransport(request.personalPickupOrFalse(), request.personalDropoffOrFalse());
            if (request.note() != null) attendance.updateNote(request.note());
        } else {
            attendance = new ElderAttendance(elderly, company, targetDate, status);
            attendance.updatePersonalTransport(request.personalPickupOrFalse(), request.personalDropoffOrFalse());
            if (request.note() != null) attendance.updateNote(request.note());
            elderAttendanceRepository.save(attendance);
        }
    }

    @Transactional
    public void bulkCheckElderAttendance(Long companyId, List<ElderAttendanceRequestDTO> requests) {
        for (ElderAttendanceRequestDTO request : requests) {
            checkElderAttendance(companyId, request);
        }
    }
}
