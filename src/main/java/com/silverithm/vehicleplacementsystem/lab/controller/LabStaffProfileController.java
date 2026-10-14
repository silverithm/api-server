package com.silverithm.vehicleplacementsystem.lab.controller;

import com.silverithm.vehicleplacementsystem.lab.dto.LabStaffHrDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabStaffHrService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 직원 인사 기준 API. 기관은 서버가 로그인 정보로 구한다. */
@RestController
@RequestMapping("/api/v1/lab/staff-profiles")
@RequiredArgsConstructor
public class LabStaffProfileController {

    private final LabStaffHrService service;

    @GetMapping
    public List<LabStaffHrDto.ProfileView> list() {
        return service.listProfiles();
    }

    /** memberId로 upsert */
    @PutMapping
    public LabStaffHrDto.ProfileView save(@RequestBody LabStaffHrDto.ProfileInput input) {
        return service.upsertProfile(input);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteProfile(id);
        return ResponseEntity.noContent().build();
    }
}
