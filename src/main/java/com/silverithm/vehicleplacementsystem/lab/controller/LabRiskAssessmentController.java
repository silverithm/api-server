package com.silverithm.vehicleplacementsystem.lab.controller;

import com.silverithm.vehicleplacementsystem.lab.dto.LabRiskDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabRiskAssessmentService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 욕창(Braden)·낙상(Morse) 위험도 평가 API. */
@RestController
@RequestMapping("/api/v1/lab/risk-assessments")
@RequiredArgsConstructor
public class LabRiskAssessmentController {

    private final LabRiskAssessmentService service;

    @GetMapping
    public List<LabRiskDto.View> list(@RequestParam Long elderlyId) {
        return service.listByElder(elderlyId);
    }

    @GetMapping("/latest")
    public List<LabRiskDto.Latest> latest() {
        return service.latest();
    }

    @PostMapping
    public LabRiskDto.View create(@RequestBody LabRiskDto.Input input) {
        return service.create(input);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
