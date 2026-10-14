package com.silverithm.vehicleplacementsystem.lab.controller;

import com.silverithm.vehicleplacementsystem.lab.dto.LabBathLogDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabBathLogService;
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

/** 목욕 제공 일지 API. */
@RestController
@RequestMapping("/api/v1/lab/bath-logs")
@RequiredArgsConstructor
public class LabBathLogController {

    private final LabBathLogService service;

    /** month(YYYY-MM) 필수, elderlyId로 좁힐 수 있다 — 날짜순 */
    @GetMapping
    public List<LabBathLogDto.View> list(@RequestParam(required = false) String month,
                                         @RequestParam(required = false) Long elderlyId) {
        return service.list(month, elderlyId);
    }

    @PostMapping
    public LabBathLogDto.View create(@RequestBody LabBathLogDto.Input input) {
        return service.create(input);
    }

    @PutMapping("/{id}")
    public LabBathLogDto.View update(@PathVariable Long id, @RequestBody LabBathLogDto.Input input) {
        return service.update(id, input);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
