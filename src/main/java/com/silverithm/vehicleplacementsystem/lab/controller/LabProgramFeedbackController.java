package com.silverithm.vehicleplacementsystem.lab.controller;

import com.silverithm.vehicleplacementsystem.lab.dto.LabProgramPlanDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabProgramPlanService;
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

/** 프로그램 의견수렴·반영 API. 반기당 여러 건을 쌓는다. */
@RestController
@RequestMapping("/api/v1/lab/program-feedback")
@RequiredArgsConstructor
public class LabProgramFeedbackController {

    private final LabProgramPlanService service;

    @GetMapping
    public List<LabProgramPlanDto.FeedbackView> list(@RequestParam(required = false) String year) {
        return service.listFeedback(year);
    }

    @PostMapping
    public LabProgramPlanDto.FeedbackView create(@RequestBody LabProgramPlanDto.FeedbackInput input) {
        return service.createFeedback(input);
    }

    @PutMapping("/{id}")
    public LabProgramPlanDto.FeedbackView update(@PathVariable Long id,
                                                 @RequestBody LabProgramPlanDto.FeedbackInput input) {
        return service.updateFeedback(id, input);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteFeedback(id);
        return ResponseEntity.noContent().build();
    }
}
