package com.silverithm.vehicleplacementsystem.lab.controller;

import com.silverithm.vehicleplacementsystem.lab.dto.LabGuardianNoticeDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabGuardianNoticeService;
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

/** 보호자 월간 안내 API. */
@RestController
@RequestMapping("/api/v1/lab/guardian-notices")
@RequiredArgsConstructor
public class LabGuardianNoticeController {

    private final LabGuardianNoticeService service;

    @GetMapping
    public List<LabGuardianNoticeDto.View> list(@RequestParam(required = false) String month) {
        return service.listByMonth(month);
    }

    /** 어르신 x 월 upsert. 보낸 안내면 409 */
    @PutMapping
    public LabGuardianNoticeDto.View upsert(@RequestBody LabGuardianNoticeDto.Input input) {
        return service.upsert(input);
    }

    @PostMapping("/{id}/sent")
    public LabGuardianNoticeDto.View sent(@PathVariable Long id, @RequestBody LabGuardianNoticeDto.SentInput input) {
        return service.markSent(id, input);
    }

    @PostMapping("/{id}/unsent")
    public LabGuardianNoticeDto.View unsent(@PathVariable Long id) {
        return service.markUnsent(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
