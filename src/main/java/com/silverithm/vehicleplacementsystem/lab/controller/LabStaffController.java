package com.silverithm.vehicleplacementsystem.lab.controller;

import com.silverithm.vehicleplacementsystem.lab.dto.LabStaffDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabStaffService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 교육·회의 참석자를 고르기 위한 같은 기관 직원 목록. */
@RestController
@RequestMapping("/api/v1/lab/staff")
@RequiredArgsConstructor
public class LabStaffController {

    private final LabStaffService service;

    @GetMapping
    public List<LabStaffDto.View> list() {
        return service.list();
    }
}
