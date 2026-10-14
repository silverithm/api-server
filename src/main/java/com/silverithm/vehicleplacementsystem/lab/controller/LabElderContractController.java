package com.silverithm.vehicleplacementsystem.lab.controller;

import com.silverithm.vehicleplacementsystem.lab.dto.LabElderContractDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabElderContractService;
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

/** 급여 계약·장기요양 인정 API. 같은 어르신의 둘째 진행 중 계약은 409. */
@RestController
@RequestMapping("/api/v1/lab/elder-contracts")
@RequiredArgsConstructor
public class LabElderContractController {

    private final LabElderContractService service;

    /** status(ACTIVE|ENDED), elderlyId로 좁힌다(없으면 전부) — 계약 시작일 최신순 */
    @GetMapping
    public List<LabElderContractDto.View> list(@RequestParam(required = false) String status,
                                               @RequestParam(required = false) Long elderlyId) {
        return service.list(status, elderlyId);
    }

    @PostMapping
    public LabElderContractDto.View create(@RequestBody LabElderContractDto.Input input) {
        return service.create(input);
    }

    @PutMapping("/{id}")
    public LabElderContractDto.View update(@PathVariable Long id, @RequestBody LabElderContractDto.Input input) {
        return service.update(id, input);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
