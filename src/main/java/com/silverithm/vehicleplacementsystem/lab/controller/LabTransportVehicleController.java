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

/** 송영 차량·보험 API. 기관은 서버가 로그인 정보로 구한다. */
@RestController
@RequestMapping("/api/v1/lab/transport-vehicles")
@RequiredArgsConstructor
public class LabTransportVehicleController {

    private final LabTransportService service;

    @GetMapping
    public List<LabTransportDto.VehicleView> list() {
        return service.listVehicles();
    }

    @PostMapping
    public LabTransportDto.VehicleView create(@RequestBody LabTransportDto.VehicleInput input) {
        return service.createVehicle(input);
    }

    @PutMapping("/{id}")
    public LabTransportDto.VehicleView update(@PathVariable Long id,
                                              @RequestBody LabTransportDto.VehicleInput input) {
        return service.updateVehicle(id, input);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteVehicle(id);
        return ResponseEntity.noContent().build();
    }
}
