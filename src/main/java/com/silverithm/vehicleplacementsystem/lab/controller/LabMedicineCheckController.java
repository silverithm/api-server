package com.silverithm.vehicleplacementsystem.lab.controller;

import com.silverithm.vehicleplacementsystem.lab.dto.LabMedicineDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabMedicineService;
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

/** 약품 보관 점검 API. */
@RestController
@RequestMapping("/api/v1/lab/medicine-checks")
@RequiredArgsConstructor
public class LabMedicineCheckController {

    private final LabMedicineService service;

    /** year(YYYY) — 점검일 오름차순 */
    @GetMapping
    public List<LabMedicineDto.CheckView> list(@RequestParam(required = false) String year) {
        return service.listChecks(year);
    }

    /** checkedOn으로 덮어쓴다 */
    @PutMapping
    public LabMedicineDto.CheckView upsert(@RequestBody LabMedicineDto.CheckInput input) {
        return service.upsertCheck(input);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteCheck(id);
        return ResponseEntity.noContent().build();
    }
}
