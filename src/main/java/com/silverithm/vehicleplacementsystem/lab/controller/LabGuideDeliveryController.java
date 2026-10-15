package com.silverithm.vehicleplacementsystem.lab.controller;

import com.silverithm.vehicleplacementsystem.lab.dto.LabTransportDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabTransportService;
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

/** 송영 수칙·운행표 제공 기록 API. 기관은 서버가 로그인 정보로 구한다. */
@RestController
@RequestMapping("/api/v1/lab/guide-deliveries")
@RequiredArgsConstructor
public class LabGuideDeliveryController {

    private final LabTransportService service;

    /** kind는 필수, elderlyId는 선택 */
    @GetMapping
    public List<LabTransportDto.DeliveryView> list(@RequestParam(required = false) String kind,
                                                   @RequestParam(required = false) Long elderlyId) {
        return service.listDeliveries(kind, elderlyId);
    }

    /** 여러 어르신에게 한 번에 제공한 기록 — 어르신마다 한 건씩 만든다 */
    @PostMapping("/batch")
    public List<LabTransportDto.DeliveryView> batch(@RequestBody LabTransportDto.DeliveryBatchInput input) {
        return service.createDeliveries(input);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteDelivery(id);
        return ResponseEntity.noContent().build();
    }
}
