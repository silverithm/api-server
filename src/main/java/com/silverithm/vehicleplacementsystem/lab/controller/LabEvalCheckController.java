package com.silverithm.vehicleplacementsystem.lab.controller;

import com.silverithm.vehicleplacementsystem.lab.dto.LabEvalCheckDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabEvalCheckService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 기관평가 준비 점검 API. */
@RestController
@RequestMapping("/api/v1/lab/eval-checks")
@RequiredArgsConstructor
public class LabEvalCheckController {

    private final LabEvalCheckService service;

    @GetMapping
    public List<LabEvalCheckDto.View> list() {
        return service.list();
    }

    @PutMapping("/{indicatorCode}")
    public LabEvalCheckDto.View save(@PathVariable String indicatorCode, @RequestBody LabEvalCheckDto.Input input) {
        return service.upsert(indicatorCode, input);
    }
}
