package com.silverithm.vehicleplacementsystem.lab.controller;

import com.silverithm.vehicleplacementsystem.lab.dto.LabSatisfactionDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabSatisfactionService;
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

/** 급여 만족도 응답 API. 조회는 기간(from·to)이 필수이고 1년까지. */
@RestController
@RequestMapping("/api/v1/lab/satisfaction")
@RequiredArgsConstructor
public class LabSatisfactionController {

    private final LabSatisfactionService service;

    @GetMapping
    public List<LabSatisfactionDto.View> list(@RequestParam(required = false) String kind,
                                              @RequestParam(required = false) String from,
                                              @RequestParam(required = false) String to) {
        return service.list(kind, from, to);
    }

    @PostMapping
    public LabSatisfactionDto.View create(@RequestBody LabSatisfactionDto.Input input) {
        return service.create(input);
    }

    @PutMapping("/{id}")
    public LabSatisfactionDto.View update(@PathVariable Long id, @RequestBody LabSatisfactionDto.Input input) {
        return service.update(id, input);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
