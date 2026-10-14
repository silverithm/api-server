package com.silverithm.vehicleplacementsystem.lab.controller;

import com.silverithm.vehicleplacementsystem.lab.dto.LabPayrollDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabPayrollService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 직원 급여 기준·월 급여대장 API. 기관은 서버가 로그인 정보로 정한다. */
@RestController
@RequestMapping("/api/v1/lab")
@RequiredArgsConstructor
public class LabPayrollController {

    private final LabPayrollService service;

    @GetMapping("/payroll-profiles")
    public List<LabPayrollDto.ProfileView> listProfiles() {
        return service.listProfiles();
    }

    @PutMapping("/payroll-profiles")
    public LabPayrollDto.ProfileView upsertProfile(@RequestBody LabPayrollDto.ProfileInput input) {
        return service.upsertProfile(input);
    }

    @DeleteMapping("/payroll-profiles/{id}")
    public ResponseEntity<Void> deleteProfile(@PathVariable Long id) {
        service.deleteProfile(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/payroll-lines")
    public List<LabPayrollDto.LineView> listLines(@RequestParam(required = false) String month,
                                                  @RequestParam(required = false) String year) {
        return service.listLines(month, year);
    }

    @PutMapping("/payroll-lines")
    public LabPayrollDto.LineView upsertLine(@RequestBody LabPayrollDto.LineInput input) {
        return service.upsertLine(input);
    }

    @PostMapping("/payroll-lines/confirm")
    public LabPayrollDto.ConfirmResult confirm(@RequestBody LabPayrollDto.MonthInput input) {
        return service.confirm(input);
    }

    @PostMapping("/payroll-lines/unconfirm")
    public LabPayrollDto.UnconfirmResult unconfirm(@RequestBody LabPayrollDto.MonthInput input) {
        return service.unconfirm(input);
    }

    @DeleteMapping("/payroll-lines/{id}")
    public ResponseEntity<Void> deleteLine(@PathVariable Long id) {
        service.deleteLine(id);
        return ResponseEntity.noContent().build();
    }
}
