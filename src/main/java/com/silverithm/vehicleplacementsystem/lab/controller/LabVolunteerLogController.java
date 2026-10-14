package com.silverithm.vehicleplacementsystem.lab.controller;

import com.silverithm.vehicleplacementsystem.lab.dto.LabVolunteerLogDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabVolunteerLogService;
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

/** 봉사자 활동 API. */
@RestController
@RequestMapping("/api/v1/lab/volunteer-logs")
@RequiredArgsConstructor
public class LabVolunteerLogController {

    private final LabVolunteerLogService service;

    /** month 또는 year — 날짜순 */
    @GetMapping
    public List<LabVolunteerLogDto.View> list(@RequestParam(required = false) String month,
                                              @RequestParam(required = false) String year) {
        return service.list(month, year);
    }

    @PostMapping
    public LabVolunteerLogDto.View create(@RequestBody LabVolunteerLogDto.Input input) {
        return service.create(input);
    }

    @PutMapping("/{id}")
    public LabVolunteerLogDto.View update(@PathVariable Long id, @RequestBody LabVolunteerLogDto.Input input) {
        return service.update(id, input);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
