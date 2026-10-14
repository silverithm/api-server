package com.silverithm.vehicleplacementsystem.lab.controller;

import com.silverithm.vehicleplacementsystem.lab.dto.LabMedicalVisitDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabMedicalVisitService;
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

/** 진료 동행 기록 API. 기관은 서버가 로그인 정보로 구한다. */
@RestController
@RequestMapping("/api/v1/lab/medical-visits")
@RequiredArgsConstructor
public class LabMedicalVisitController {

    private final LabMedicalVisitService service;

    @GetMapping
    public List<LabMedicalVisitDto.View> list(@RequestParam(required = false) String month,
                                              @RequestParam(required = false) Long elderlyId,
                                              @RequestParam(required = false) Boolean followUpDue) {
        return service.list(month, elderlyId, followUpDue);
    }

    @PostMapping
    public LabMedicalVisitDto.View create(@RequestBody LabMedicalVisitDto.Input input) {
        return service.create(input);
    }

    @PutMapping("/{id}")
    public LabMedicalVisitDto.View update(@PathVariable Long id, @RequestBody LabMedicalVisitDto.Input input) {
        return service.update(id, input);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
