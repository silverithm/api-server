package com.silverithm.vehicleplacementsystem.lab.controller;

import com.silverithm.vehicleplacementsystem.lab.dto.LabCareDeliveryDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabCareDeliveryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 제공대장 API. */
@RestController
@RequestMapping("/api/v1/lab/care-deliveries")
@RequiredArgsConstructor
public class LabCareDeliveryController {

    private final LabCareDeliveryService service;

    @GetMapping
    public List<LabCareDeliveryDto.View> list(@RequestParam String month) {
        return service.listByMonth(month);
    }

    @PutMapping
    public LabCareDeliveryDto.View save(@RequestBody LabCareDeliveryDto.Input input) {
        return service.upsert(input);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
