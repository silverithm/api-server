package com.silverithm.vehicleplacementsystem.lab.controller;

import com.silverithm.vehicleplacementsystem.lab.dto.LabBusinessPlanDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabBusinessPlanService;
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

/** 연간 사업계획서 API. 그 해 계획서가 없으면 GET은 404. */
@RestController
@RequestMapping("/api/v1/lab/business-plans")
@RequiredArgsConstructor
public class LabBusinessPlanController {

    private final LabBusinessPlanService service;

    @GetMapping
    public LabBusinessPlanDto.View get(@RequestParam(required = false) String year) {
        return service.getByYear(year);
    }

    /** 연도로 upsert */
    @PutMapping
    public LabBusinessPlanDto.View save(@RequestBody LabBusinessPlanDto.Input input) {
        return service.upsert(input);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
