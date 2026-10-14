package com.silverithm.vehicleplacementsystem.lab.controller;

import com.silverithm.vehicleplacementsystem.lab.dto.LabResourceLinkDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabResourceLinkService;
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

/** 외부기관·지역자원 연계 API. */
@RestController
@RequestMapping("/api/v1/lab/resource-links")
@RequiredArgsConstructor
public class LabResourceLinkController {

    private final LabResourceLinkService service;

    /** year 또는 elderlyId — 최신순 */
    @GetMapping
    public List<LabResourceLinkDto.View> list(@RequestParam(required = false) String year,
                                              @RequestParam(required = false) Long elderlyId) {
        return service.list(year, elderlyId);
    }

    @PostMapping
    public LabResourceLinkDto.View create(@RequestBody LabResourceLinkDto.Input input) {
        return service.create(input);
    }

    @PutMapping("/{id}")
    public LabResourceLinkDto.View update(@PathVariable Long id, @RequestBody LabResourceLinkDto.Input input) {
        return service.update(id, input);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
