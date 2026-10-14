package com.silverithm.vehicleplacementsystem.lab.controller;

import com.silverithm.vehicleplacementsystem.lab.dto.LabBudgetDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabBudgetService;
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

/** 예산(본예산·추경) API. */
@RestController
@RequestMapping("/api/v1/lab/budgets")
@RequiredArgsConstructor
public class LabBudgetController {

    private final LabBudgetService service;

    @GetMapping
    public List<LabBudgetDto.View> list(@RequestParam(required = false) String year) {
        return service.listByYear(year);
    }

    /** 회계연도·차수 upsert — 줄은 통째로 바뀐다. 확정된 예산이면 409 */
    @PutMapping
    public LabBudgetDto.View upsert(@RequestBody LabBudgetDto.Input input) {
        return service.upsert(input);
    }

    @PostMapping("/{id}/confirm")
    public LabBudgetDto.View confirm(@PathVariable Long id) {
        return service.confirm(id);
    }

    @PostMapping("/{id}/unconfirm")
    public LabBudgetDto.View unconfirm(@PathVariable Long id) {
        return service.unconfirm(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
