package com.silverithm.vehicleplacementsystem.lab.controller;

import com.silverithm.vehicleplacementsystem.lab.dto.LabFacilityDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabFacilityService;
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

/** 시설 관리 대장 API — 시설 정보와 실시 기록. 기관은 서버가 로그인 정보로 구한다. */
@RestController
@RequestMapping("/api/v1/lab/facility")
@RequiredArgsConstructor
public class LabFacilityController {

    private final LabFacilityService service;

    @GetMapping("/profile")
    public LabFacilityDto.ProfileView getProfile() {
        return service.getProfile();
    }

    @PutMapping("/profile")
    public LabFacilityDto.ProfileView saveProfile(@RequestBody LabFacilityDto.ProfileInput input) {
        return service.saveProfile(input);
    }

    /** year 또는 itemCode 중 하나는 필요하다 */
    @GetMapping("/logs")
    public List<LabFacilityDto.LogView> listLogs(@RequestParam(required = false) String year,
                                                 @RequestParam(required = false) String itemCode) {
        return service.listLogs(year, itemCode);
    }

    @PostMapping("/logs")
    public LabFacilityDto.LogView createLog(@RequestBody LabFacilityDto.LogInput input) {
        return service.createLog(input);
    }

    @PutMapping("/logs/{id}")
    public LabFacilityDto.LogView updateLog(@PathVariable Long id, @RequestBody LabFacilityDto.LogInput input) {
        return service.updateLog(id, input);
    }

    @DeleteMapping("/logs/{id}")
    public ResponseEntity<Void> deleteLog(@PathVariable Long id) {
        service.deleteLog(id);
        return ResponseEntity.noContent().build();
    }
}
