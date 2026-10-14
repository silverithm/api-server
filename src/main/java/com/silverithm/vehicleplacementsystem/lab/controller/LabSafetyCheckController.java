package com.silverithm.vehicleplacementsystem.lab.controller;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabSafetyCheckDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabSafetyCheckService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 위생·안전·시설 점검 API. */
@RestController
@RequestMapping("/api/v1/lab/safety-checks")
@RequiredArgsConstructor
public class LabSafetyCheckController {

    private final LabSafetyCheckService service;

    /** month 하나면 그 달, year 하나면 그 해. kind는 선택. 둘을 함께 주거나 둘 다 없으면 400 */
    @GetMapping
    public List<LabSafetyCheckDto.View> list(@RequestParam(required = false) String month,
                                             @RequestParam(required = false) String year,
                                             @RequestParam(required = false) String kind) {
        if (month != null && year != null) {
            throw new CustomException("month와 year는 함께 보낼 수 없습니다. 하나만 보내 주세요", HttpStatus.BAD_REQUEST);
        }
        if (year != null) {
            return service.listByYear(year, kind);
        }
        if (month != null) {
            return service.listByMonth(month, kind);
        }
        throw new CustomException("month 또는 year 중 하나를 보내 주세요", HttpStatus.BAD_REQUEST);
    }

    @PutMapping
    public LabSafetyCheckDto.View save(@RequestBody LabSafetyCheckDto.Input input) {
        return service.upsert(input);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
