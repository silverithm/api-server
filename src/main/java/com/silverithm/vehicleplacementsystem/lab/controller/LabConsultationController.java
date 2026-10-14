package com.silverithm.vehicleplacementsystem.lab.controller;

import com.silverithm.vehicleplacementsystem.lab.dto.LabConsultationDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabConsultationService;
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

/** 상담일지 API. */
@RestController
@RequestMapping("/api/v1/lab/consultations")
@RequiredArgsConstructor
public class LabConsultationController {

    private final LabConsultationService service;

    /** elderlyId 또는 month(또는 둘 다) — 최신순 */
    @GetMapping
    public List<LabConsultationDto.View> list(@RequestParam(required = false) Long elderlyId,
                                              @RequestParam(required = false) String month) {
        return service.list(elderlyId, month);
    }

    @PostMapping
    public LabConsultationDto.View create(@RequestBody LabConsultationDto.Input input) {
        return service.create(input);
    }

    @PutMapping("/{id}")
    public LabConsultationDto.View update(@PathVariable Long id, @RequestBody LabConsultationDto.Input input) {
        return service.update(id, input);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
