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

/** 약품 관리대장 API. */
@RestController
@RequestMapping("/api/v1/lab/medicine-stock")
@RequiredArgsConstructor
public class LabMedicineStockController {

    private final LabMedicineService service;

    /** 사용기한 순. 폐기한 약은 includeDisposed=true일 때만 */
    @GetMapping
    public List<LabMedicineDto.StockView> list(@RequestParam(defaultValue = "false") boolean includeDisposed) {
        return service.listStock(includeDisposed);
    }

    @PostMapping
    public LabMedicineDto.StockView create(@RequestBody LabMedicineDto.StockInput input) {
        return service.createStock(input);
    }

    @PutMapping("/{id}")
    public LabMedicineDto.StockView update(@PathVariable Long id, @RequestBody LabMedicineDto.StockInput input) {
        return service.updateStock(id, input);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteStock(id);
        return ResponseEntity.noContent().build();
    }
}
