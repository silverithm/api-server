package com.silverithm.vehicleplacementsystem.controller;

import com.silverithm.vehicleplacementsystem.dto.WorkTaskDto;
import com.silverithm.vehicleplacementsystem.service.WorkTaskService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 업무진행도 API — 기관은 토큰으로 정한다(요청에 companyId를 받지 않는다). */
@RestController
@RequestMapping("/api/v1/work-tasks")
@RequiredArgsConstructor
public class WorkTaskController {

    private final WorkTaskService service;

    @GetMapping
    public Map<String, List<WorkTaskDto.View>> list() {
        return Map.of("tasks", service.list());
    }

    @PostMapping
    public WorkTaskDto.View create(@RequestBody WorkTaskDto.Input input) {
        return service.create(input);
    }

    @PutMapping("/{id}")
    public WorkTaskDto.View update(@PathVariable Long id, @RequestBody WorkTaskDto.Input input) {
        return service.update(id, input);
    }

    @PatchMapping("/{id}/progress")
    public WorkTaskDto.View updateProgress(@PathVariable Long id, @RequestBody WorkTaskDto.ProgressInput input) {
        return service.updateProgress(id, input == null ? null : input.progress());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
