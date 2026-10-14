package com.silverithm.vehicleplacementsystem.lab.controller;

import com.silverithm.vehicleplacementsystem.lab.dto.LabMedicationDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabMedicationService;
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

/** 투약 기록 API. */
@RestController
@RequestMapping("/api/v1/lab/medication-doses")
@RequiredArgsConstructor
public class LabMedicationDoseController {

    private final LabMedicationService service;

    /** date(YYYY-MM-DD, 그날 기관 전체) 또는 elderlyId + month(YYYY-MM) */
    @GetMapping
    public List<LabMedicationDto.DoseView> list(@RequestParam(required = false) String date,
                                                @RequestParam(required = false) Long elderlyId,
                                                @RequestParam(required = false) String month) {
        return service.listDoses(date, elderlyId, month);
    }

    /** requestId + givenOn + slot로 덮어쓴다 */
    @PutMapping
    public LabMedicationDto.DoseView upsert(@RequestBody LabMedicationDto.DoseInput input) {
        return service.upsertDose(input);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteDose(id);
        return ResponseEntity.noContent().build();
    }
}
