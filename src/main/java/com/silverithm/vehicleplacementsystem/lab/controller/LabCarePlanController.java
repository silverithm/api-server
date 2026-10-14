package com.silverithm.vehicleplacementsystem.lab.controller;

import com.silverithm.vehicleplacementsystem.lab.dto.LabCarePlanDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabCarePlanService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 욕구사정·급여제공계획 API. */
@RestController
@RequestMapping("/api/v1/lab/care-plans")
@RequiredArgsConstructor
public class LabCarePlanController {

    private final LabCarePlanService service;

    @GetMapping
    public List<LabCarePlanDto.View> list(@RequestParam String year) {
        return service.listByYear(year);
    }

    @PutMapping
    public LabCarePlanDto.View save(@RequestBody LabCarePlanDto.Input input) {
        return service.upsert(input);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
