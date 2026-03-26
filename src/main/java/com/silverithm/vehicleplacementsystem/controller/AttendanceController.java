package com.silverithm.vehicleplacementsystem.controller;

import com.silverithm.vehicleplacementsystem.dto.*;
import com.silverithm.vehicleplacementsystem.service.AttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    // ==================== 직원 출석 ====================

    @GetMapping("/employee/summary")
    public ResponseEntity<AttendanceSummaryDTO> getEmployeeAttendanceSummary(
            @RequestParam Long companyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(attendanceService.getEmployeeAttendanceSummary(companyId, date));
    }

    @GetMapping("/employee")
    public ResponseEntity<Map<String, Object>> getEmployeeAttendanceList(
            @RequestParam Long companyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        List<EmployeeAttendanceDTO> list = attendanceService.getEmployeeAttendanceList(companyId, date);
        return ResponseEntity.ok(Map.of("attendances", list));
    }

    @PostMapping("/employee")
    public ResponseEntity<String> checkEmployeeAttendance(
            @RequestParam Long companyId,
            @RequestBody EmployeeAttendanceRequestDTO request) {
        attendanceService.checkEmployeeAttendance(companyId, request);
        return ResponseEntity.ok("Success");
    }

    @PostMapping("/employee/bulk")
    public ResponseEntity<String> bulkCheckEmployeeAttendance(
            @RequestParam Long companyId,
            @RequestBody List<EmployeeAttendanceRequestDTO> requests) {
        attendanceService.bulkCheckEmployeeAttendance(companyId, requests);
        return ResponseEntity.ok("Success");
    }

    // ==================== 어르신 출석 ====================

    @GetMapping("/elder/summary")
    public ResponseEntity<ElderAttendanceSummaryDTO> getElderAttendanceSummary(
            @RequestParam Long companyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(attendanceService.getElderAttendanceSummary(companyId, date));
    }

    @GetMapping("/elder")
    public ResponseEntity<Map<String, Object>> getElderAttendanceList(
            @RequestParam Long companyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        List<ElderAttendanceDTO> list = attendanceService.getElderAttendanceList(companyId, date);
        return ResponseEntity.ok(Map.of("attendances", list));
    }

    @PostMapping("/elder")
    public ResponseEntity<String> checkElderAttendance(
            @RequestParam Long companyId,
            @RequestBody ElderAttendanceRequestDTO request) {
        attendanceService.checkElderAttendance(companyId, request);
        return ResponseEntity.ok("Success");
    }

    @PostMapping("/elder/bulk")
    public ResponseEntity<String> bulkCheckElderAttendance(
            @RequestParam Long companyId,
            @RequestBody List<ElderAttendanceRequestDTO> requests) {
        attendanceService.bulkCheckElderAttendance(companyId, requests);
        return ResponseEntity.ok("Success");
    }

    // ==================== 공통 ====================

    @PutMapping("/{id}")
    public ResponseEntity<String> updateAttendance(@PathVariable Long id) {
        // 개별 출석 수정은 employee/elder 각각의 체크 API를 재호출하는 방식으로 처리
        return ResponseEntity.ok("Success");
    }
}
