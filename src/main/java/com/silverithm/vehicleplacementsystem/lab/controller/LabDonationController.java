package com.silverithm.vehicleplacementsystem.lab.controller;

import com.silverithm.vehicleplacementsystem.lab.dto.LabDonationDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabDonationService;
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

/** 후원금 API. */
@RestController
@RequestMapping("/api/v1/lab/donations")
@RequiredArgsConstructor
public class LabDonationController {

    private final LabDonationService service;

    @GetMapping
    public List<LabDonationDto.View> list(@RequestParam String year) {
        return service.listByYear(year);
    }

    @PostMapping
    public LabDonationDto.View create(@RequestBody LabDonationDto.Input input) {
        return service.create(input);
    }

    @PutMapping("/{id}")
    public LabDonationDto.View update(@PathVariable Long id, @RequestBody LabDonationDto.Input input) {
        return service.update(id, input);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
