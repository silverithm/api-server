package com.silverithm.vehicleplacementsystem.lab.controller;

import com.silverithm.vehicleplacementsystem.lab.dto.LabRehabSessionDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabRehabSessionService;
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

/** 기능회복훈련 API. */
@RestController
@RequestMapping("/api/v1/lab")
@RequiredArgsConstructor
public class LabRehabSessionController {

    private final LabRehabSessionService service;

    /** date 또는 month, 선택으로 elderlyId — 날짜·id 순 */
    @GetMapping("/rehab-sessions")
    public List<LabRehabSessionDto.View> list(@RequestParam(required = false) String date,
                                              @RequestParam(required = false) String month,
                                              @RequestParam(required = false) Long elderlyId) {
        return service.list(date, month, elderlyId);
    }

    @PostMapping("/rehab-sessions")
    public LabRehabSessionDto.View create(@RequestBody LabRehabSessionDto.Input input) {
        return service.create(input);
    }

    @PostMapping("/rehab-sessions/batch")
    public List<LabRehabSessionDto.View> createBatch(@RequestBody LabRehabSessionDto.BatchInput input) {
        return service.createBatch(input);
    }

    @PutMapping("/rehab-sessions/{id}")
    public LabRehabSessionDto.View update(@PathVariable Long id, @RequestBody LabRehabSessionDto.Input input) {
        return service.update(id, input);
    }

    @DeleteMapping("/rehab-sessions/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
