package com.silverithm.vehicleplacementsystem.lab.controller;

import com.silverithm.vehicleplacementsystem.lab.dto.LabCarePlanChangeDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabCarePlanChangeService;
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

/** 급여제공계획 변경 이력 API. 기관은 서버가 로그인 정보로 구한다. */
@RestController
@RequestMapping("/api/v1/lab/care-plan-changes")
@RequiredArgsConstructor
public class LabCarePlanChangeController {

    private final LabCarePlanChangeService service;

    /** elderlyId 또는 year(또는 둘 다)로 거른다 — 둘 다 없으면 400 */
    @GetMapping
    public List<LabCarePlanChangeDto.View> list(@RequestParam(required = false) Long elderlyId,
                                                @RequestParam(required = false) String year) {
        return service.list(elderlyId, year);
    }

    @PostMapping
    public LabCarePlanChangeDto.View create(@RequestBody LabCarePlanChangeDto.Input input) {
        return service.create(input);
    }

    @PutMapping("/{id}")
    public LabCarePlanChangeDto.View update(@PathVariable Long id, @RequestBody LabCarePlanChangeDto.Input input) {
        return service.update(id, input);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
