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

    // ==================== 직원 출석 ====================

    public AttendanceSummaryDTO getEmployeeAttendanceSummary(Long companyId, LocalDate date) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회사입니다: " + companyId));

        long total = memberRepository.countByCompanyAndStatus(company, Member.MemberStatus.ACTIVE);
        long present = employeeAttendanceRepository.countByCompanyIdAndDateAndStatus(companyId, date, AttendanceStatus.PRESENT);
        long vacation = employeeAttendanceRepository.countByCompanyIdAndDateAndStatus(companyId, date, AttendanceStatus.VACATION);
        long halfDay = employeeAttendanceRepository.countByCompanyIdAndDateAndStatus(companyId, date, AttendanceStatus.HALF_DAY);
        long absent = total - present - vacation - halfDay;
        if (absent < 0) absent = 0;

        return new AttendanceSummaryDTO(total, present + halfDay, absent, vacation);
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

    public ElderAttendanceSummaryDTO getElderAttendanceSummary(Long companyId, LocalDate date) {
        long total = elderRepository.countByCompanyId(companyId);
        long present = elderAttendanceRepository.countByCompanyIdAndDateAndStatus(companyId, date, ElderAttendanceStatus.PRESENT);
        long absent = total - present;
        if (absent < 0) absent = 0;
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
