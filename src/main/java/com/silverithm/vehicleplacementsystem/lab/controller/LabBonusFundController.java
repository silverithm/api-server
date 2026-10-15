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

/** 평가 가산금 수령·사용 API. 기관은 서버가 로그인 정보로 구한다. */
@RestController
@RequestMapping("/api/v1/lab/bonus-funds")
@RequiredArgsConstructor
public class LabBonusFundController {

    private final LabStaffWelfareService service;

    @GetMapping
    public List<LabStaffWelfareDto.BonusView> list(@RequestParam(required = false) String year) {
        return service.listFunds(year);
    }

    @PostMapping
    public LabStaffWelfareDto.BonusView create(@RequestBody LabStaffWelfareDto.BonusInput input) {
        return service.createFund(input);
    }

    @PutMapping("/{id}")
    public LabStaffWelfareDto.BonusView update(@PathVariable Long id,
                                               @RequestBody LabStaffWelfareDto.BonusInput input) {
        return service.updateFund(id, input);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteFund(id);
        return ResponseEntity.noContent().build();
    }
}
