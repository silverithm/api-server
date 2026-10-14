package com.silverithm.vehicleplacementsystem.lab.controller;

import com.silverithm.vehicleplacementsystem.lab.dto.LabCopayBillDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabCopayBillService;
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

/** 본인부담금 청구·수납 API. */
@RestController
@RequestMapping("/api/v1/lab/copay-bills")
@RequiredArgsConstructor
public class LabCopayBillController {

    private final LabCopayBillService service;

    @GetMapping
    public List<LabCopayBillDto.View> list(@RequestParam String month) {
        return service.listByMonth(month);
    }

    @PutMapping
    public LabCopayBillDto.View save(@RequestBody LabCopayBillDto.Input input) {
        return service.upsert(input);
    }

    @PostMapping("/{id}/payments")
    public LabCopayBillDto.View addPayment(@PathVariable Long id, @RequestBody LabCopayBillDto.PaymentInput input) {
        return service.addPayment(id, input);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
