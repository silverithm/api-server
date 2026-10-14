package com.silverithm.vehicleplacementsystem.lab.controller;

import com.silverithm.vehicleplacementsystem.lab.dto.LabElderDocumentDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabElderDocumentService;
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

/** 수급자 서류함 API. 기관은 서버가 로그인 정보로 구한다. */
@RestController
@RequestMapping("/api/v1/lab/elder-documents")
@RequiredArgsConstructor
public class LabElderDocumentController {

    private final LabElderDocumentService service;

    @GetMapping
    public List<LabElderDocumentDto.View> list(@RequestParam(required = false) Long elderlyId) {
        return service.list(elderlyId);
    }

    @PostMapping
    public LabElderDocumentDto.View create(@RequestBody LabElderDocumentDto.Input input) {
        return service.create(input);
    }

    @PutMapping("/{id}")
    public LabElderDocumentDto.View update(@PathVariable Long id, @RequestBody LabElderDocumentDto.Input input) {
        return service.update(id, input);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
