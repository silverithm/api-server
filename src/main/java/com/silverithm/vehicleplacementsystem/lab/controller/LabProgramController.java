package com.silverithm.vehicleplacementsystem.lab.controller;

import com.silverithm.vehicleplacementsystem.lab.dto.LabProgramDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabProgramService;
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

/** 프로그램 일지 API. */
@RestController
@RequestMapping("/api/v1/lab/programs")
@RequiredArgsConstructor
public class LabProgramController {

    private final LabProgramService service;

    @GetMapping
    public List<LabProgramDto.View> list(@RequestParam String month) {
        return service.listByMonth(month);
    }

    @PostMapping
    public LabProgramDto.View create(@RequestBody LabProgramDto.Input input) {
        return service.create(input);
    }

    @PutMapping("/{id}")
    public LabProgramDto.View update(@PathVariable Long id, @RequestBody LabProgramDto.Input input) {
        return service.update(id, input);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
