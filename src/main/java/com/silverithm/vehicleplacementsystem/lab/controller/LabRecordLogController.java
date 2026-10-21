package com.silverithm.vehicleplacementsystem.lab.controller;

import com.silverithm.vehicleplacementsystem.lab.dto.LabRecordLogDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabRecordLogService;
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

/** 종류별 기록(업무수행일지·시설운영일지·안전관리 설명·현금영수증) API. 기관은 서버가 로그인 정보로 구한다. */
@RestController
@RequestMapping("/api/v1/lab/record-logs")
@RequiredArgsConstructor
public class LabRecordLogController {

    private final LabRecordLogService service;

    @GetMapping
    public List<LabRecordLogDto.View> list(@RequestParam String kind,
                                        @RequestParam(required = false) String month,
                                        @RequestParam(required = false) Integer year,
                                        @RequestParam(required = false) Long elderlyId) {
        return service.list(kind, month, year, elderlyId);
    }

    @PostMapping
    public LabRecordLogDto.View create(@RequestBody LabRecordLogDto.Input input) {
        return service.create(input);
    }

    @PutMapping("/{id}")
    public LabRecordLogDto.View update(@PathVariable Long id, @RequestBody LabRecordLogDto.Input input) {
        return service.update(id, input);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
