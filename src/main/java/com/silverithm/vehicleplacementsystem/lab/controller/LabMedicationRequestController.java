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

/** 투약의뢰서 API. 투약 기록이 있는 의뢰서를 지우면 409. */
@RestController
@RequestMapping("/api/v1/lab/medication-requests")
@RequiredArgsConstructor
public class LabMedicationRequestController {

    private final LabMedicationService service;

    /** status(ACTIVE|ENDED), elderlyId로 좁힌다 — 시작일 최신순 */
    @GetMapping
    public List<LabMedicationDto.RequestView> list(@RequestParam(required = false) String status,
                                                   @RequestParam(required = false) Long elderlyId) {
        return service.listRequests(status, elderlyId);
    }

    @PostMapping
    public LabMedicationDto.RequestView create(@RequestBody LabMedicationDto.RequestInput input) {
        return service.createRequest(input);
    }

    @PutMapping("/{id}")
    public LabMedicationDto.RequestView update(@PathVariable Long id,
                                               @RequestBody LabMedicationDto.RequestInput input) {
        return service.updateRequest(id, input);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteRequest(id);
        return ResponseEntity.noContent().build();
    }
}
