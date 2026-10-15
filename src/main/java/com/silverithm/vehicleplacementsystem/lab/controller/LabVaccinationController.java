package com.silverithm.vehicleplacementsystem.lab.controller;

import com.silverithm.vehicleplacementsystem.lab.dto.LabVaccinationDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabVaccinationService;
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

/** 예방접종 API. 기관은 서버가 로그인 정보로 구한다. */
@RestController
@RequestMapping("/api/v1/lab/vaccinations")
@RequiredArgsConstructor
public class LabVaccinationController {

    private final LabVaccinationService service;

    /** year 또는 elderlyId(또는 둘 다)로 거른다 — 둘 다 없으면 400 */
    @GetMapping
    public List<LabVaccinationDto.View> list(@RequestParam(required = false) String year,
                                             @RequestParam(required = false) Long elderlyId) {
        return service.list(year, elderlyId);
    }

    /** elderlyId + seasonYear + vaccine으로 upsert */
    @PutMapping
    public LabVaccinationDto.View save(@RequestBody LabVaccinationDto.Input input) {
        return service.upsert(input);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
