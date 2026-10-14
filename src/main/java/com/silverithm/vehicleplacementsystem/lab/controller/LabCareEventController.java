package com.silverithm.vehicleplacementsystem.lab.controller;

import com.silverithm.vehicleplacementsystem.lab.dto.LabCareEventDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabCareEventService;
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

/** 상태변화·사고·응급 API. */
@RestController
@RequestMapping("/api/v1/lab/care-events")
@RequiredArgsConstructor
public class LabCareEventController {

    private final LabCareEventService service;

    /** month, elderlyId, status(OPEN|CLOSED) 중 하나 이상 — 최신순 */
    @GetMapping
    public List<LabCareEventDto.View> list(@RequestParam(required = false) String month,
                                           @RequestParam(required = false) Long elderlyId,
                                           @RequestParam(required = false) String status) {
        return service.list(month, elderlyId, status);
    }

    @PostMapping
    public LabCareEventDto.View create(@RequestBody LabCareEventDto.Input input) {
        return service.create(input);
    }

    @PutMapping("/{id}")
    public LabCareEventDto.View update(@PathVariable Long id, @RequestBody LabCareEventDto.Input input) {
        return service.update(id, input);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
