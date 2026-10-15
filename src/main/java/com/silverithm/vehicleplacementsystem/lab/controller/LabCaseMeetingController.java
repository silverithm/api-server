package com.silverithm.vehicleplacementsystem.lab.controller;

import com.silverithm.vehicleplacementsystem.lab.dto.LabCaseMeetingDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabCaseMeetingService;
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

/** 사례관리 회의 API. 기관은 서버가 로그인 정보로 구한다. */
@RestController
@RequestMapping("/api/v1/lab/case-meetings")
@RequiredArgsConstructor
public class LabCaseMeetingController {

    private final LabCaseMeetingService service;

    @GetMapping
    public List<LabCaseMeetingDto.View> list(@RequestParam(required = false) String year) {
        return service.list(year);
    }

    @PostMapping
    public LabCaseMeetingDto.View create(@RequestBody LabCaseMeetingDto.Input input) {
        return service.create(input);
    }

    @PutMapping("/{id}")
    public LabCaseMeetingDto.View update(@PathVariable Long id, @RequestBody LabCaseMeetingDto.Input input) {
        return service.update(id, input);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
