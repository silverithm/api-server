package com.silverithm.vehicleplacementsystem.lab.controller;

import com.silverithm.vehicleplacementsystem.lab.dto.LabStaffWelfareDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabStaffWelfareService;
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

/** 직원 복지(포상) 제공대장 API. 기관은 서버가 로그인 정보로 구한다. */
@RestController
@RequestMapping("/api/v1/lab/staff-welfare")
@RequiredArgsConstructor
public class LabStaffWelfareController {

    private final LabStaffWelfareService service;

    @GetMapping
    public List<LabStaffWelfareDto.WelfareView> list(@RequestParam(required = false) String year) {
        return service.listWelfare(year);
    }

    @PostMapping
    public LabStaffWelfareDto.WelfareView create(@RequestBody LabStaffWelfareDto.WelfareInput input) {
        return service.createWelfare(input);
    }

    @PutMapping("/{id}")
    public LabStaffWelfareDto.WelfareView update(@PathVariable Long id,
                                                 @RequestBody LabStaffWelfareDto.WelfareInput input) {
        return service.updateWelfare(id, input);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteWelfare(id);
        return ResponseEntity.noContent().build();
    }
}
