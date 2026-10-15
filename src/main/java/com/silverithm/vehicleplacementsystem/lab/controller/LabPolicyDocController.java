package com.silverithm.vehicleplacementsystem.lab.controller;

import com.silverithm.vehicleplacementsystem.lab.dto.LabPolicyDocDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabPolicyDocService;
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

/** 규정·지침 대장 API. 기관은 서버가 로그인 정보로 구한다. */
@RestController
@RequestMapping("/api/v1/lab/policy-docs")
@RequiredArgsConstructor
public class LabPolicyDocController {

    private final LabPolicyDocService service;

    @GetMapping
    public List<LabPolicyDocDto.View> list() {
        return service.list();
    }

    @PostMapping
    public LabPolicyDocDto.View create(@RequestBody LabPolicyDocDto.Input input) {
        return service.create(input);
    }

    @PutMapping("/{id}")
    public LabPolicyDocDto.View update(@PathVariable Long id, @RequestBody LabPolicyDocDto.Input input) {
        return service.update(id, input);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
