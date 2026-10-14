package com.silverithm.vehicleplacementsystem.lab.controller;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabCopayBillDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabCopayBillService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

    /** month 하나면 그 달, year 하나면 그 해 전체. 둘을 함께 주거나 둘 다 없으면 400 */
    @GetMapping
    public List<LabCopayBillDto.View> list(@RequestParam(required = false) String month,
                                           @RequestParam(required = false) String year) {
        if (month != null && year != null) {
            throw new CustomException("month와 year는 함께 보낼 수 없습니다. 하나만 보내 주세요", HttpStatus.BAD_REQUEST);
        }
        if (year != null) {
            return service.listByYear(year);
        }
        if (month != null) {
            return service.listByMonth(month);
        }
        throw new CustomException("month 또는 year 중 하나를 보내 주세요", HttpStatus.BAD_REQUEST);
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
