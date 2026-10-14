package com.silverithm.vehicleplacementsystem.lab.controller;

import com.silverithm.vehicleplacementsystem.lab.dto.LabIntakeDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabIntakeService;
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

/** 이용 문의·입소 상담·대기 API. 연락처엔 동의일, 거절엔 사유, 계약 완료엔 어르신 연결이 필요하다. */
@RestController
@RequestMapping("/api/v1/lab/intake")
@RequiredArgsConstructor
public class LabIntakeController {

    private final LabIntakeService service;

    /** status로 좁힌다(없으면 전부) — 문의일 최신순 */
    @GetMapping
    public List<LabIntakeDto.View> list(@RequestParam(required = false) String status) {
        return service.list(status);
    }

    @PostMapping
    public LabIntakeDto.View create(@RequestBody LabIntakeDto.Input input) {
        return service.create(input);
    }

    @PutMapping("/{id}")
    public LabIntakeDto.View update(@PathVariable Long id, @RequestBody LabIntakeDto.Input input) {
        return service.update(id, input);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
