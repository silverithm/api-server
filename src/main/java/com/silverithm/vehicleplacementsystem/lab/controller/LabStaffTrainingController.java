package com.silverithm.vehicleplacementsystem.lab.controller;

import com.silverithm.vehicleplacementsystem.lab.dto.LabStaffTrainingDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabStaffTrainingService;
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

/** 직원 교육·회의 API. */
@RestController
@RequestMapping("/api/v1/lab/staff-trainings")
@RequiredArgsConstructor
public class LabStaffTrainingController {

    private final LabStaffTrainingService service;

    @GetMapping
    public List<LabStaffTrainingDto.View> list(@RequestParam String year) {
        return service.listByYear(year);
    }

    @PostMapping
    public LabStaffTrainingDto.View create(@RequestBody LabStaffTrainingDto.Input input) {
        return service.create(input);
    }

    @PutMapping("/{id}")
    public LabStaffTrainingDto.View update(@PathVariable Long id, @RequestBody LabStaffTrainingDto.Input input) {
        return service.update(id, input);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
